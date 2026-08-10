package com.pzdd.mydia.module.rewrite

import java.lang.reflect.Array

/**
 * 把 smali 类型签名转成 Java Class。对应 Dia 的 SmaliSignatureConverter。
 *
 * 例：
 *  "I"        -> Int::class.javaPrimitiveType
 *  "Ljava/lang/String;" -> String::class.java
 *  "[B"       -> ByteArray::class.java
 *  "[Ljava/lang/Object;" -> Array<Object>
 *
 * 这是“方法改写引擎”定位方法参数时必须的一步——用户在规则里写的是 smali 串，
 * XposedHelpers.findAndHookMethod 要的是 Class[]。
 */
object SmaliSignatureConverter {

    fun toClass(loader: ClassLoader, smali: String): Class<*>? = when {
        smali.startsWith("[") -> {
            val component = toClass(loader, smali.substring(1)) ?: return null
            Array.newInstance(component, 0).javaClass
        }
        smali.startsWith("L") && smali.endsWith(";") -> {
            val name = smali.substring(1, smali.length - 1).replace('/', '.')
            runCatching { loader.loadClass(name) }.getOrNull()
        }
        else -> when (smali) {
            "Z" -> Boolean::class.javaPrimitiveType
            "B" -> Byte::class.javaPrimitiveType
            "C" -> Char::class.javaPrimitiveType
            "S" -> Short::class.javaPrimitiveType
            "I" -> Int::class.javaPrimitiveType
            "J" -> Long::class.javaPrimitiveType
            "F" -> Float::class.javaPrimitiveType
            "D" -> Double::class.javaPrimitiveType
            "V" -> Void::class.javaPrimitiveType
            else -> runCatching { loader.loadClass(smali) }.getOrNull()
        }
    }
}
