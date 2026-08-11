//
// libmydia_hook.so —— MyDia 原生 hook 层（对齐 Dia 的 libdialbox_hook.so）。
//
// 【关键修复】旧版 install_hook 只覆盖本地函数指针，对目标 App 的 libc GOT 无效。
//            现改用 ByteHook（bytehook_hook_all）做真正的 PLT/GOT hook，让以下能力
//            在被注入的目标进程里真正生效：
//
//  1. 原生时间伪造：gettimeofday / time 按偏移或固定值改写
//  2. exit / _exit 拦截：记录后吞掉（对齐 Dia 的 disable-exit 原生层）
//  3. fopen 敏感文件打点：打开 su/xposed/magisk 路径时记录
//
// 通过 JNI 暴露给 Java（签名与旧版保持一致，NativeHook.kt 无需改动）：
//  - nativeSetTimeOffset(long ms)    设置时间偏移（0 = 关闭偏移）
//  - nativeSetTimeFixed(long ms)     设置固定时间（-1 = 关闭固定）
//  - nativeIsHooked()                是否已安装原生 hook
//
// ByteHook proxy 函数约定：
//  - 调原函数用 BYTEHOOK_CALL_PREV()
//  - 在 .cpp 里函数开头写 BYTEHOOK_STACK_SCOPE() 自动管理栈（替代手动 POP_STACK）
//

#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include "bytehook.h"

#define LOG_TAG "MyDiaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ---------- 全局状态（volatile：跨线程可见） ----------
static volatile int64_t g_time_offset_ms = 0;   // 偏移模式（0 = 关闭）
static volatile int64_t g_time_fixed_ms = -1;   // 固定模式（-1 = 关闭）
static volatile int g_disable_exit = 0;          // 1 = 拦截 exit/_exit
static volatile int g_hooked = 0;                // hook 是否已安装成功

// ---------- 直接 syscall 取真实时间（避免在 fake 函数里递归走 hook） ----------
static int64_t real_now_ms(void) {
    struct timespec ts;
    syscall(SYS_clock_gettime, CLOCK_REALTIME, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/** 把伪造后的时间写进 timeval（fixed 优先于 offset）。 */
static void apply_fake_time(struct timeval *tv) {
    if (tv == NULL) return;
    int64_t base_ms = real_now_ms();
    int64_t out_ms = base_ms;
    if (g_time_fixed_ms >= 0) {
        out_ms = g_time_fixed_ms;
    } else if (g_time_offset_ms != 0) {
        out_ms = base_ms + g_time_offset_ms;
    }
    tv->tv_sec = (time_t) (out_ms / 1000);
    tv->tv_usec = (suseconds_t) ((out_ms % 1000) * 1000);
}

// ---------- 1. gettimeofday 伪造 ----------
static int fake_gettimeofday(struct timeval *tv, void *tz) {
    BYTEHOOK_STACK_SCOPE();
    if (tv == NULL) {
        errno = EFAULT;
        return -1;
    }
    if (g_time_fixed_ms >= 0 || g_time_offset_ms != 0) {
        apply_fake_time(tv);
        return 0;
    }
    // 未开启伪造：调原函数
    return BYTEHOOK_CALL_PREV(fake_gettimeofday, tv, tz);
}

// ---------- 2. time 伪造 ----------
static time_t fake_time(time_t *t) {
    BYTEHOOK_STACK_SCOPE();
    if (g_time_fixed_ms >= 0 || g_time_offset_ms != 0) {
        int64_t ms = (g_time_fixed_ms >= 0) ? g_time_fixed_ms : real_now_ms() + g_time_offset_ms;
        time_t v = (time_t) (ms / 1000);
        if (t) *t = v;
        return v;
    }
    return BYTEHOOK_CALL_PREV(fake_time, t);
}

// ---------- 3. exit / _exit 拦截（disable-exit 开启时吞掉） ----------
static void fake_exit(int status) {
    BYTEHOOK_STACK_SCOPE();
    if (g_disable_exit) {
        LOGW("exit(%d) blocked by MyDia native hook", status);
        return;  // 吞掉，阻止 App 退出
    }
    BYTEHOOK_CALL_PREV(fake_exit, status);
}

static void fake_exit_(int status) {
    BYTEHOOK_STACK_SCOPE();
    if (g_disable_exit) {
        LOGW("_exit(%d) blocked by MyDia native hook", status);
        return;
    }
    BYTEHOOK_CALL_PREV(fake_exit_, status);
}

// ---------- 4. fopen 敏感文件打点 ----------
static FILE *fake_fopen(const char *path, const char *mode) {
    BYTEHOOK_STACK_SCOPE();
    if (path && (strstr(path, "su") || strstr(path, "xposed") || strstr(path, "magisk"))) {
        LOGI("fopen(sensitive): %s (%s)", path, mode);
    }
    return BYTEHOOK_CALL_PREV(fake_fopen, path, mode);
}

// ---------- 安装所有 PLT hook ----------
static void install_hooks(void) {
    // libc.so 里的符号全进程 hook
    bytehook_hook_all("libc.so", "gettimeofday", (void *) fake_gettimeofday, NULL, NULL);
    bytehook_hook_all("libc.so", "time",         (void *) fake_time,         NULL, NULL);
    bytehook_hook_all("libc.so", "exit",         (void *) fake_exit,         NULL, NULL);
    bytehook_hook_all("libc.so", "_exit",        (void *) fake_exit_,        NULL, NULL);
    bytehook_hook_all("libc.so", "fopen",        (void *) fake_fopen,        NULL, NULL);
    g_hooked = 1;
    LOGI("ByteHook PLT hooks installed (gettimeofday/time/exit/_exit/fopen)");
}

// ---------- JNI ----------
extern "C" JNIEXPORT void JNICALL
Java_com_pzdd_mydia_module_hook_extras_NativeHook_nativeSetTimeOffset(JNIEnv *, jobject, jlong ms) {
    g_time_offset_ms = ms;
    LOGI("native time offset set to %lld ms", (long long) ms);
}

extern "C" JNIEXPORT void JNICALL
Java_com_pzdd_mydia_module_hook_extras_NativeHook_nativeSetTimeFixed(JNIEnv *, jobject, jlong ms) {
    g_time_fixed_ms = ms;
    LOGI("native time fixed set to %lld ms", (long long) ms);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pzdd_mydia_module_hook_extras_NativeHook_nativeIsHooked(JNIEnv *, jobject) {
    return g_hooked ? JNI_TRUE : JNI_FALSE;
}

/** 由 Java 层调用：开启/关闭 exit 拦截（联动 disable-exit 的原生层）。 */
extern "C" JNIEXPORT void JNICALL
Java_com_pzdd_mydia_module_hook_extras_NativeHook_nativeSetDisableExit(JNIEnv *, jobject, jboolean enable) {
    g_disable_exit = enable ? 1 : 0;
    LOGI("native disable-exit set to %d", g_disable_exit);
}

// ---------- JNI_OnLoad：安装所有 hook ----------
extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    install_hooks();
    LOGI("libmydia_hook loaded (ByteHook, JNI_OnLoad done)");
    return JNI_VERSION_1_6;
}
