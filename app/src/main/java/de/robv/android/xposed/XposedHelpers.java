package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 旧 API XposedHelpers 的 compat 实现（自写 compat 层）。
 *
 * 覆盖 MyDia 36 个 hook 用到的全部成员：
 *  - findClass / findClassIfExists
 *  - findAndHookMethod / findAndHookConstructor
 *  - get/set(Static)ObjectField、get/setBooleanField、get/setIntField、
 *    get/setLongField、get/setDoubleField、get/setFloatField
 */
public final class XposedHelpers {

    private static final String TAG = "MyDia-Compat";

    /** findClass 失败时的包装异常（兼容旧 API ClassNotFoundError） */
    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError(Throwable cause) { super(cause); }
        public ClassNotFoundError(String detailMessage, Throwable cause) { super(detailMessage, cause); }
    }

    // ==================== findClass ====================

    /** 在指定 ClassLoader 上按名加载类，失败抛 ClassNotFoundError（兼容旧 API）。 */
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError("Class not found: " + className, e);
        }
    }

    /** 找不到返回 null（不抛）。 */
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable t) {
            return null;
        }
    }

    // ==================== findAndHookMethod / findAndHookConstructor ====================

    /**
     * 按类 + 方法名 + 参数类型 hook 单个方法。
     * 最后一个参数是 XC_MethodHook（或 XC_MethodReplacement）。
     * 兼容字符串类名（内部先 findClass）。
     */
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                         Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("You must provide at least one parameter type and the callback");
        }
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Class<?>[] paramTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < paramTypes.length; i++) {
            Object pt = parameterTypesAndCallback[i];
            if (pt instanceof String) {
                paramTypes[i] = findClass((String) pt, clazz.getClassLoader());
            } else if (pt instanceof Class) {
                paramTypes[i] = (Class<?>) pt;
            } else {
                throw new IllegalArgumentException("parameter type must be Class or String: " + pt);
            }
        }
        try {
            Method m = clazz.getMethod(methodName, paramTypes);
            try { XposedBridge.hookMethod(m, callback); } catch (Throwable t) { throw new RuntimeException(t); }
            return callback.new Unhook(new XC_MethodHook.MethodHookParam(m, null, null));
        } catch (NoSuchMethodException e) {
            // 兜底：查私有方法
            try {
                Method m = findMethodExact(clazz, methodName, paramTypes);
                try { XposedBridge.hookMethod(m, callback); } catch (Throwable t) { throw new RuntimeException(t); }
                return callback.new Unhook(new XC_MethodHook.MethodHookParam(m, null, null));
            } catch (Throwable t) {
                throw new NoSuchMethodError(clazz.getName() + "." + methodName);
            }
        }
    }

    /** 字符串类名版 */
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                         String methodName, Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    /** 字符串类名版（直接方法名，参数类型由反射推断——旧 API 常见用法） */
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                         String methodName, XC_MethodHook callback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, callback);
    }

    /** hook 构造器。 */
    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz,
                                                              Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("You must provide at least one parameter type and the callback");
        }
        XC_MethodHook callback = (XC_MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Class<?>[] paramTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < paramTypes.length; i++) {
            Object pt = parameterTypesAndCallback[i];
            if (pt instanceof String) {
                paramTypes[i] = findClass((String) pt, clazz.getClassLoader());
            } else if (pt instanceof Class) {
                paramTypes[i] = (Class<?>) pt;
            } else {
                throw new IllegalArgumentException("parameter type must be Class or String: " + pt);
            }
        }
        try {
            Constructor<?> c = clazz.getDeclaredConstructor(paramTypes);
            try { XposedBridge.hookConstructor(c, callback); } catch (Throwable t) { throw new RuntimeException(t); }
            return callback.new Unhook(new XC_MethodHook.MethodHookParam(c, null, null));
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(clazz.getName() + ".<init>");
        }
    }

    /** 字符串类名版构造器 */
    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader,
                                                              Object... parameterTypesAndCallback) {
        return findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
    }

    /** 精确找方法（含私有），找不到抛异常。 */
    private static Method findMethodExact(Class<?> clazz, String methodName, Class<?>[] paramTypes)
            throws NoSuchMethodException {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Method m = c.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + methodName);
    }

    // ==================== 字段读写 ====================

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "." + fieldName);
    }

    private static Object getField(Object obj, Class<?> clazz, String fieldName) {
        try {
            Field f = findField(clazz, fieldName);
            return f.get(obj);
        } catch (Throwable t) {
            throw new NoSuchFieldError(clazz.getName() + "." + fieldName + ": " + t);
        }
    }

    private static void setField(Object obj, Class<?> clazz, String fieldName, Object value) {
        try {
            Field f = findField(clazz, fieldName);
            f.set(obj, value);
        } catch (Throwable t) {
            throw new NoSuchFieldError(clazz.getName() + "." + fieldName + ": " + t);
        }
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return getField(obj, obj.getClass(), fieldName);
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        return getField(null, clazz, fieldName);
    }

    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        setField(null, clazz, fieldName, value);
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        return (Boolean) getField(obj, obj.getClass(), fieldName);
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    public static int getIntField(Object obj, String fieldName) {
        return (Integer) getField(obj, obj.getClass(), fieldName);
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    public static long getLongField(Object obj, String fieldName) {
        return (Long) getField(obj, obj.getClass(), fieldName);
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    public static double getDoubleField(Object obj, String fieldName) {
        return (Double) getField(obj, obj.getClass(), fieldName);
    }

    public static void setDoubleField(Object obj, String fieldName, double value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    public static float getFloatField(Object obj, String fieldName) {
        return (Float) getField(obj, obj.getClass(), fieldName);
    }

    public static void setFloatField(Object obj, String fieldName, float value) {
        setField(obj, obj.getClass(), fieldName, value);
    }

    // ==================== 其他常用（未用到但保留兼容） ====================

    public static Object callMethod(Object obj, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
            Method m = findMethodExact(obj.getClass(), methodName, paramTypes);
            return m.invoke(obj, args);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? Object.class : args[i].getClass();
            Method m = findMethodExact(clazz, methodName, paramTypes);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
