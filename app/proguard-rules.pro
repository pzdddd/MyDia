# Xposed 模块入口类由 manifest/xposed_init 声明，不要被混淆移除
-keep class com.pzdd.mydia.module.** { *; }

# Xposed API（compileOnly，运行时由框架提供）
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }
