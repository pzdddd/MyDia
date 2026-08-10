package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 旧 API XposedBridge 的 compat 实现（自写 compat 层核心）。
 *
 * 把旧 API 的静态方法（log / hookAllMethods / hookAllConstructors / invokeOriginalMethod）
 * 桥接到 libxposed API 102 的 `module.hook(Executable).intercept(Hooker)` 机制。
 *
 * 桥接原理：
 *  - 每个旧 XC_MethodHook 实例被包装成一个 libxposed Hooker；
 *  - Hooker.intercept(Chain) 里构造 MethodHookParam（填充 method/thisObject/args），
 *    依次调用旧 hook 的 before → chain.proceed()（若 before 设置了 result/throwable
 *    则跳过原方法）→ after；
 *  - 返回值/异常通过 MethodHookParam 传递。
 *
 * 本 compat 层编译进模块 APK（不在 compileOnly 范围），运行时替换框架未提供的旧 API。
 */
public final class XposedBridge {

    private static final String TAG = "MyDia-Compat";

    /** libxposed 模块实例（由 LibXposedEntry 在 onModuleLoaded 时注入）。 */
    @SuppressWarnings("unused")
    private static XposedModule module;

    /**
     * 注入 libxposed 模块实例。由 [com.pzdd.mydia.module.LibXposedEntry] 调用。
     */
    public static void attach(XposedModule m) {
        module = m;
    }

    /** 兼容：框架名（注入侧用） */
    public static String getFrameworkName() {
        return module == null ? "unknown" : module.getFrameworkName();
    }

    // ==================== 日志 ====================

    /** 旧 API log(String)：转发到 libxposed 模块日志（logcat TAG=MyDia）。 */
    public static void log(String text) {
        if (module != null) {
            module.log(Log.INFO, "MyDia", text);
        } else {
            Log.i("MyDia", text);
        }
    }

    /** 旧 API log(Throwable)。 */
    public static void log(Throwable t) {
        if (module != null) {
            module.log(Log.ERROR, "MyDia", String.valueOf(t), t);
        } else {
            Log.e("MyDia", String.valueOf(t), t);
        }
    }

    // ==================== hookAllMethods / hookAllConstructors ====================

    /**
     * hook 类上所有名为 methodName 的方法（含重载），返回 unhook 集合（空集合，本项目未用）。
     */
    public static java.util.Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> clazz, String methodName, XC_MethodHook callback) {
        java.util.Set<XC_MethodHook.Unhook> result = new java.util.HashSet<>();
        if (clazz == null || methodName == null || callback == null) return result;
        int hooked = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                try {
                    hookMethod(m, callback);
                    hooked++;
                    result.add(callback.new Unhook(new XC_MethodHook.MethodHookParam(m, null, null)));
                } catch (Throwable t) {
                    Log.w(TAG, "hookAllMethods FAILED " + clazz.getName() + "." + methodName + ": " + t);
                }
            }
        }
        // 兼容旧 API：hookAllMethods 也覆盖父类 public 方法（旧实现行为）
        Class<?> superCls = clazz.getSuperclass();
        if (superCls != null && superCls != Object.class) {
            for (Method m : superCls.getMethods()) {
                if (m.getName().equals(methodName) && m.getDeclaringClass() != Object.class) {
                    try { hookMethod(m, callback); hooked++; } catch (Throwable t) {
                        Log.w(TAG, "hookAllMethods super FAILED " + superCls.getName() + "." + methodName + ": " + t);
                    }
                }
            }
        }
        if (hooked == 0 && clazz.getDeclaredMethods().length > 0) {
            Log.w(TAG, "hookAllMethods: 0 hooked for " + clazz.getName() + "." + methodName
                    + " (methods=" + java.util.Arrays.toString(clazz.getDeclaredMethods()) + ")");
        }
        return result;
    }

    /**
     * hook 类上所有构造器。
     */
    public static java.util.Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> clazz, XC_MethodHook callback) {
        java.util.Set<XC_MethodHook.Unhook> result = new java.util.HashSet<>();
        if (clazz == null || callback == null) return result;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            try {
                hookConstructor(c, callback);
                result.add(callback.new Unhook(new XC_MethodHook.MethodHookParam(c, null, null)));
            } catch (Throwable t) {
                Log.w(TAG, "hookAllConstructors skip " + clazz.getName() + ": " + t);
            }
        }
        return result;
    }

    // ==================== hookMethod / hookConstructor（内部） ====================

    /** 把单个方法桥接到 libxposed hook。 */
    static void hookMethod(Method method, XC_MethodHook callback) throws Throwable {
        if (module == null) {
            Log.w(TAG, "hookMethod: module not attached, skip " + method);
            return;
        }
        module.hook(method).intercept(new OldApiHooker(callback));
    }

    /** 把单个构造器桥接到 libxposed hook。 */
    static void hookConstructor(Constructor<?> ctor, XC_MethodHook callback) throws Throwable {
        if (module == null) {
            Log.w(TAG, "hookConstructor: module not attached, skip " + ctor);
            return;
        }
        module.hook(ctor).intercept(new OldApiHooker(callback));
    }

    /** 旧 API hook 的 libxposed Hooker 包装。 */
    private static final class OldApiHooker implements XposedInterface.Hooker {
        private final XC_MethodHook callback;

        OldApiHooker(XC_MethodHook callback) { this.callback = callback; }

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            // 构造旧 API 参数
            Executable exe = chain.getExecutable();
            List<Object> argsList = chain.getArgs();
            Object[] args = argsList == null ? new Object[0] : argsList.toArray(new Object[0]);
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(
                    (Member) exe, chain.getThisObject(), args);

            // ===== before 阶段 =====
            try {
                callback.callBefore(param);
            } catch (Throwable t) {
                param.setThrowable(t);
            }

            // before 里显式设置了结果/异常（或 replacement）→ 跳过原方法
            if (param.throwable != null) {
                throw param.throwable;
            }
            if (param.resultSet) {
                return param.result;
            }

            // ===== 执行原方法 =====
            Object result;
            try {
                // 【关键】libxposed 的 chain.proceed() 无参版用【原始参数】执行，
                // proceed(newArgs) 才用传入参数。旧 API 语义是 before 里改 param.args[i]
                // 会传给原方法 —— 所以这里必须无条件传回当前 args（哪怕没改，
                // 传同一数组内容一致，行为等价且参数修改不丢失）。
                result = chain.proceed(param.args);
            } catch (Throwable t) {
                param.setThrowable(t);
                throw t;
            }
            param.result = result;

            // ===== after 阶段 =====
            param.resultSet = false;  // after 里 setResult 表示覆盖返回值
            param.throwableSet = false;
            try {
                callback.callAfter(param);
            } catch (Throwable t) {
                throw t;
            }
            if (param.throwable != null) {
                throw param.throwable;
            }
            return param.result;
        }

        private boolean isReplacement(XC_MethodHook c) {
            return c.isReplacement();
        }
    }

    /**
     * 调用原始方法（旧 API 兼容）。用于 after 里想调原始实现再包装的场景。
     * 注意：libxposed 的 Chain 已 proceed 过，这里用反射直接调用（尽力而为）。
     */
    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws Throwable {
        try {
            if (method instanceof Method) {
                Method m = (Method) method;
                m.setAccessible(true);
                return m.invoke(thisObject, args);
            } else if (method instanceof Constructor) {
                Constructor<?> c = (Constructor<?>) method;
                c.setAccessible(true);
                return c.newInstance(args);
            }
        } catch (Throwable t) {
            throw t.getCause() != null ? t.getCause() : t;
        }
        return null;
    }

    /** 兼容：hookMethod 单个方法（内部用）。 */
    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        try {
            if (method instanceof Method) hookMethod((Method) method, callback);
            else if (method instanceof Constructor) hookConstructor((Constructor<?>) method, callback);
        } catch (Throwable t) {
            Log.w(TAG, "hookMethod failed " + method + ": " + t);
        }
        return null;
    }
}
