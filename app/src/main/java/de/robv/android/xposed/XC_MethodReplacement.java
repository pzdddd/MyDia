package de.robv.android.xposed;

/**
 * 旧 API XC_MethodReplacement 的 compat 实现。
 *
 * 用于「直接替换方法」的场景：DO_NOTHING（方法体变空）、returnConstant（固定返回值）。
 */
public class XC_MethodReplacement extends XC_MethodHook {

    private final Object replacement;

    private XC_MethodReplacement(Object replacement) {
        this.replacement = replacement;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.result = replacement;
    }

    @Override
    boolean isReplacement() { return true; }

    /** 静态字段：方法体直接变空（返回默认值 null/0/false 由 JVM 处理） */
    public static final XC_MethodReplacement DO_NOTHING = new XC_MethodReplacement(null);

    /** 返回固定值 */
    public static XC_MethodReplacement returnConstant(Object result) {
        return new XC_MethodReplacement(result);
    }
}
