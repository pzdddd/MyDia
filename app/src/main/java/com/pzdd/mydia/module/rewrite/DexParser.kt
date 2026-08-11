package com.pzdd.mydia.module.rewrite

import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.MultiDexContainer
import org.jf.dexlib2.iface.reference.TypeReference
import java.io.File

/**
 * 用 dexlib2 解析 dex/apk，提取类与方法信息。
 *
 * 对应 Dia 的 dialog.box.expand.rewrite.DexParser。
 * 输出轻量的 [ClassInfo] / [MethodInfo]，供 UI 类树浏览器展示。
 */
object DexParser {

    /** 一个类的信息（已把 smali 类型转成 Java 风格类名）。 */
    data class ClassInfo(
        /** Java 风格全限定名，如 com.foo.Bar（内部类用 $） */
        val className: String,
        /** smali 类型串，如 Lcom/foo/Bar; */
        val type: String,
        val methods: List<MethodInfo>,
    )

    /** 一个方法的信息。 */
    data class MethodInfo(
        val name: String,
        /** 返回类型 smali 串，如 V / Ljava/lang/String; */
        val returnType: String,
        /** 参数类型 smali 串列表 */
        val parameterTypes: List<String>,
    ) {
        /** JVM 风格签名，如 (Ljava/lang/String;I)V —— 仅显示/存档用。 */
        val signature: String get() = "(${parameterTypes.joinToString("")})$returnType"
    }

    /**
     * 解析单个文件（dex/apk/jar/zip），返回全部类。
     *
     * apk 是多 dex 容器：遍历所有 dex entry。
     * 单 dex 文件也兼容（loadDexContainer 对单 dex 返回一个 entry）。
     */
    fun parse(file: File): List<ClassInfo> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val container: MultiDexContainer<*> =
                DexFileFactory.loadDexContainer(file, Opcodes.getDefault())
            val result = ArrayList<ClassInfo>()
            for (entryName in container.dexEntryNames) {
                val entry = container.getEntry(entryName) ?: continue
                val dexFile = entry.dexFile
                for (classDef in dexFile.classes) {
                    val type = classDef.type  // Lcom/foo/Bar;
                    val methods = classDef.methods.map { m ->
                        MethodInfo(
                            name = m.name,
                            returnType = m.returnType,
                            parameterTypes = m.parameterTypes.map { it.toString() },
                        )
                    }
                    result.add(ClassInfo(smaliToJava(type), type, methods))
                }
            }
            result
        }.getOrElse { emptyList() }
    }

    /** 解析多个文件，合并去重（按 type）。 */
    fun parseAll(files: List<File>): List<ClassInfo> {
        val byType = LinkedHashMap<String, ClassInfo>()
        for (f in files) {
            parse(f).forEach { c ->
                // 同名类取首个出现（优先级由 files 顺序决定）
                if (c.type !in byType) byType[c.type] = c
            }
        }
        return byType.values.toList()
    }

    /** Lcom/foo/Bar; → com.foo.Bar；内部类 Lcom/foo/Bar$Inner; → com.foo.Bar$Inner */
    fun smaliToJava(type: String): String {
        if (type.isEmpty()) return type
        var s = type
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length - 1)
        return s.replace('/', '.')
    }
}
