package de.robv.android.xposed;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

/**
 * 旧 API XC_MethodHook 的 compat 实现（自写 compat 层）。
 *
 * 让 MyDia 的 36+ 个 hook（全用旧 API）无需改动即可运行在 libxposed API 102 上：
 * 本 compat 层把旧 API 桥接到 libxposed 新 API 的 hook().intercept() 机制。
 *
 * 关键设计：hook 实例不需要真正以 hook 身份注册——XposedBridge.hookMethod 会把
 * 每个旧 hook 包装成一个 libxposed Hooker，用线程局部保存当前回调上下文。
 */
public abstract class XC_MethodHook {

    /**
     * 单次方法调用的回调参数，与旧 API 签名完全一致。
     */
    public static class MethodHookParam {
        /** 被调用的方法/构造器 */
        public Member method;
        /** this 对象（静态方法为 null） */
        public Object thisObject;
        /** 方法参数 */
        public Object[] args;
        /** 返回值（before 里设置=跳过原方法直接返回；after 里设置=覆盖返回值） */
        public Object result;
        /** 抛出的异常（before 里设置=跳过原方法直接抛；after 里设置=覆盖） */
        public Throwable throwable;

        /** 内部：result 是否被 setResult() 显式设置（决定是否跳过原方法） */
        transient boolean resultSet;
        /** 内部：throwable 是否被 setThrowable() 显式设置 */
        transient boolean throwableSet;

        /** 供内部使用：本次调用对应的 libxposed Chain（null = 非回调上下文） */
        transient Object chain;

        public MethodHookParam() {}

        public MethodHookParam(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        /** 设置返回结果并跳过原方法（等价 result = value + 跳过 proceed） */
        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.resultSet = true;
            this.throwableSet = false;
        }

        /** 设置异常并跳过原方法 */
        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.throwableSet = true;
            this.resultSet = false;
        }

        /** 获取参数数量 */
        public int getArgumentCount() { return args == null ? 0 : args.length; }

        /** 获取指定参数（旧 API 兼容） */
        public Object getArgument(int index) { return args[index]; }

        /** 设置指定参数 */
        public void setArgument(int index, Object value) { args[index] = value; }
    }

    /** hook 解除句柄（本项目未用到 unhook，保留类型兼容） */
    public class Unhook {
        private final MethodHookParam param;
        public Unhook(MethodHookParam param) { this.param = param; }
        public Member getMember() { return param.method; }
        public void unhook() {}
    }

    /**
     * 在方法体执行前回调（可以修改参数、设置结果/异常跳过原方法）。
     */
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    /**
     * 在方法体执行后回调（可以修改返回值/异常）。
     */
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    /**
     * 内部使用：before + after 的包装（供 XposedBridge 调用）。
     */
    void callBefore(MethodHookParam param) throws Throwable { beforeHookedMethod(param); }
    void callAfter(MethodHookParam param) throws Throwable { afterHookedMethod(param); }

    /** 供 XposedBridge 用：判断是否替换方法（DO_NOTHING/returnConstant） */
    boolean isReplacement() { return this instanceof XC_MethodReplacement; }

    /** 兼容旧 API：invoke 钩子（本实现不直接用） */
    @SuppressWarnings("unused")
    public Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws Throwable {
        return XposedBridge.invokeOriginalMethod(method, thisObject, args);
    }
}
