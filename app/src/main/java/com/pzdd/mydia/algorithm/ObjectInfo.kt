package com.pzdd.mydia.algorithm

import com.pzdd.mydia.module.rewrite.bytesToHex

/**
 * 一次加密/签名算法调用的完整记录。对应 Dia 的 ObjectInfo。
 *
 * MessageDigest/Mac/Cipher 是【有状态】对象：update() 喂数据 → doFinal()/digest() 出结果。
 * 所以我们要在 update 时累计输入（[data]），在 doFinal/digest 时拿到输出（[ret]），
 * 关联 key/iv（Cipher/Mac init 时记录），最后一次性广播出去。
 *
 * 注意：以【对象实例】为 key 保存本类（AlgorithmHookManager.objInfos），所以同一 Cipher
 * 实例多次 update 会被合并成一条记录。
 */
class ObjectInfo(
    /** 算法名，如 "MD5" / "HmacSHA256" / "AES/CBC/PKCS5Padding" / "Base64Encode-mode:0" */
    val name: String,
) {
    @Volatile var data: ByteArray? = null         // 累计输入
        private set
    @Volatile var ret: ByteArray? = null          // 最终输出
        private set
    @Volatile var key: ByteArray? = null          // Cipher/Mac 的密钥
        private set
    @Volatile var iv: ByteArray? = null           // Cipher 的 IV（如记录）
        private set
    @Volatile var opMode: Int = -1                // Cipher.init 的 opmode（ENCRYPT=1/DECRYPT=2）
        private set
    @Volatile var stack: String = ""              // 调用栈（可选）

    fun setData(b: ByteArray?) { if (b != null) data = b.copyOf() }
    fun appendData(b: ByteArray?) {
        if (b == null || b.isEmpty()) return
        data = if (data == null) b.copyOf() else data!! + b
    }
    fun appendData(b: ByteArray, off: Int, len: Int) {
        if (off < 0 || len <= 0 || off + len > b.size) return
        appendData(b.copyOfRange(off, off + len))
    }
    fun setReturn(b: ByteArray?) { if (b != null) ret = b.copyOf() }
    fun setReturn(b: ByteArray, off: Int, len: Int) {
        if (off < 0 || len <= 0 || off + len > b.size) return
        ret = b.copyOfRange(off, off + len)
    }
    fun setKey(b: ByteArray?) { if (b != null) key = b.copyOf() }
    fun setIv(b: ByteArray?) { if (b != null) iv = b.copyOf() }
    fun setOpMode(mode: Int) { if (mode >= 0) opMode = mode }

    /** 有可用数据才上报（对齐 Dia 的上报门槛） */
    fun hasPayload(): Boolean =
        (data?.isNotEmpty() == true) || (ret?.isNotEmpty() == true)

    override fun toString(): String =
        "ObjectInfo(name=$name, data=${bytesToHex(data, 64)}, ret=${bytesToHex(ret, 64)}, " +
            "key=${bytesToHex(key, 64)}, iv=${bytesToHex(iv, 64)})"
}
