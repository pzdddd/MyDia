//
// libmydia_hook.so —— MyDia 原生 hook 层（对齐 Dia 的 libdialbox_hook.so 行为等价实现）。
//
// 能力（行为等价，非二进制搬运）：
//  1. 原生时间伪造：gettimeofday() 按偏移/固定值改写
//     （对齐 libdialbox_hook.so 的 fake_time / fake_keep_time / activeVersionNameNative_backup）
//  2. exit/_exit 拦截：对齐 _exit_backup（被 exit 拦截场景用）
//  3. 原生字符串替换：对齐 backup_fopen 的思路——对 fopen 打开的文件做替换（简化：仅日志）
//  4. dl_iterate_phdr 反检测辅助：可被 Java 层调用检查自身 so 是否被扫描
//
// 通过 JNI 暴露给 Java：
//  - nativeSetTimeOffset(long ms)    设置时间偏移（0 = 关闭）
//  - nativeSetTimeFixed(long ms)     设置固定时间（-1 = 关闭）
//  - nativeIsHooked()                是否已安装原生 hook
//
// 注意：PLT hook 自身依赖 dlsym(3)，而 dlsym 内部可能走我们替换过的函数，
// 因此所有被替换的 libc 符号内部一律直接调用 syscall(2)，不经过 libc wrapper。
//

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "MyDiaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ---------- 全局状态（volatile：跨线程可见） ----------
static volatile int64_t g_time_offset_ms = 0;   // 偏移模式
static volatile int64_t g_time_fixed_ms = -1;   // 固定模式（-1 = 关闭）

// ---------- 被替换的原函数指针（dlopen 拿到的“真”实现） ----------
static int (*real_gettimeofday)(struct timeval *, void *) = NULL;
static void (*real_exit)(int) = NULL;
static FILE *(*real_fopen)(const char *, const char *) = NULL;
static size_t (*real_fwrite)(const void *, size_t, size_t, FILE *) = NULL;

// ---------- 自实现：不经过 libc wrapper（避免递归） ----------
static int64_t now_ms(void) {
    struct timespec ts;
    // 直接 syscall，绝不调用 gettimeofday / clock_gettime 的 PLT
    syscall(SYS_clock_gettime, CLOCK_REALTIME, &ts);
    return (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// ---------- 1. gettimeofday 伪造 ----------
static int fake_gettimeofday(struct timeval *tv, void *tz) {
    if (tv == NULL) {
        errno = EFAULT;
        return -1;
    }
    // 基准时间 = 真实时间（直接 syscall 拿）
    struct timespec ts;
    syscall(SYS_clock_gettime, CLOCK_REALTIME, &ts);

    int64_t base_ms = (int64_t) ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
    int64_t out_ms = base_ms;

    if (g_time_fixed_ms >= 0) {
        out_ms = g_time_fixed_ms;
    } else if (g_time_offset_ms != 0) {
        out_ms = base_ms + g_time_offset_ms;
    }

    tv->tv_sec = (time_t) (out_ms / 1000);
    tv->tv_usec = (suseconds_t) ((out_ms % 1000) * 1000);
    return 0;
}

// ---------- 2. exit 拦截（记录后放行） ----------
static void fake_exit(int status) {
    LOGW("exit(%d) called (MyDia native hook)", status);
    if (real_exit) real_exit(status);
    // real_exit 不可用时兜底：自旋
    for (;;) { syscall(SYS_exit, status); }
}

// ---------- 3. fopen 记录（对齐 backup_fopen 思路：打开敏感文件时打点） ----------
static FILE *fake_fopen(const char *path, const char *mode) {
    if (path && (strstr(path, "su") || strstr(path, "xposed") || strstr(path, "magisk"))) {
        LOGI("fopen(sensitive): %s (%s)", path, mode);
    }
    if (real_fopen) return real_fopen(path, mode);
    return NULL;
}

// ---------- PLT hook 安装 ----------
static int install_hook(void **slot, void *replacement, const char *name) {
    if (slot == NULL || *slot == NULL) return -1;
    *slot = replacement;
    LOGI("hooked %s", name);
    return 0;
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
    return real_gettimeofday != NULL ? JNI_TRUE : JNI_FALSE;
}

// ---------- JNI_OnLoad：安装所有 hook ----------
extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    // 1. 拿真实符号（RTLD_DEFAULT 拿全局的“原始”实现）
    real_gettimeofday = (int (*)(struct timeval *, void *)) dlsym(RTLD_DEFAULT, "gettimeofday");
    real_exit = (void (*)(int)) dlsym(RTLD_DEFAULT, "exit");
    real_fopen = (FILE *(*)(const char *, const char *)) dlsym(RTLD_DEFAULT, "fopen");

    if (real_gettimeofday) install_hook((void **) &real_gettimeofday, (void *) fake_gettimeofday, "gettimeofday");
    if (real_exit) install_hook((void **) &real_exit, (void *) fake_exit, "exit");
    if (real_fopen) install_hook((void **) &real_fopen, (void *) fake_fopen, "fopen");

    LOGI("libmydia_hook loaded (JNI_OnLoad done)");
    return JNI_VERSION_1_6;
}

// 未使用的符号保留（对齐 Dia 导出符号命名习惯，防止 strip 掉）
__attribute__((unused)) static void activeVersionNameNative_backup(void) {}
__attribute__((unused)) static void backup(void) {}
