package com.v2ray.ang.handler

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tencent.mmkv.MMKV
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps a random MMKV crypt key with an AES-GCM key that never leaves the Android Keystore. */
internal class KeystoreVault(private val file: File, private val wrap: KeyWrap = KeyWrap.keystore()) : AtRestCrypto.KeyVault {
    override fun load(): String? = try {
        if (!file.exists()) null else wrap.unwrap(file.readBytes())?.toString(Charsets.US_ASCII)
    } catch (e: Exception) {
        null
    }

    override fun loadOrCreate(): String? {
        load()?.let { return it }
        return try {
            val key = randomKey()
            val blob = wrap.wrap(key.toByteArray(Charsets.US_ASCII)) ?: return null
            // Prove the round trip before any data depends on it.
            if (wrap.unwrap(blob)?.toString(Charsets.US_ASCII) != key) return null
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(blob)
            if (!tmp.renameTo(file)) return null
            key
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        /** MMKV uses up to 16 bytes of the crypt key (AES-128). */
        fun randomKey(): String {
            val r = SecureRandom()
            return String(CharArray(16) { ALPHABET[r.nextInt(ALPHABET.length)] })
        }
    }
}

/** AES-GCM wrap/unwrap; [keystore] uses an Android Keystore key, tests pass a software key. */
internal class KeyWrap(private val keyProvider: () -> SecretKey?) {
    fun wrap(plain: ByteArray): ByteArray? {
        val k = keyProvider() ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k)
        val iv = c.iv
        val ct = c.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    fun unwrap(blob: ByteArray): ByteArray? = try {
        val k = keyProvider() ?: return null
        val n = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
        c.doFinal(blob, 1 + n, blob.size - 1 - n)
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val ALIAS = "satra_mmkv_wrap_v1"

        fun keystore() = KeyWrap {
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                    generateKey()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** MMKV-backed [AtRestCrypto.Stores]: files live in files/mmkv, backups in files/mmkv_plain_backup. */
internal class MmkvStores(context: Context) : AtRestCrypto.Stores {
    private val dir = File(context.filesDir, "mmkv")
    private val backupDir = File(context.filesDir, "mmkv_plain_backup")
    private val asideDir = File(context.filesDir, "mmkv_unreadable_${System.currentTimeMillis()}")

    private fun open(id: String, key: String?): MMKV = MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE, key)

    override fun snapshot(id: String, key: String?): Map<String, String>? = try {
        val kv = open(id, key)
        val keys = kv.allKeys().orEmpty()
        val out = HashMap<String, String>(keys.size)
        for (k in keys) out[k] = kv.decodeBytes(k)?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        kv.close()
        out
    } catch (e: Exception) {
        null
    }

    override fun reKey(id: String, from: String?, to: String?): Boolean = try {
        val kv = open(id, from)
        val ok = kv.reKey(to)
        kv.close()
        ok
    } catch (e: Exception) {
        false
    }

    private fun filesOf(id: String) = listOf(id, "$id.crc")

    override fun backup(ids: List<String>): Boolean = try {
        backupDir.deleteRecursively()
        backupDir.mkdirs()
        for (id in ids) for (f in filesOf(id)) {
            val src = File(dir, f)
            if (src.exists()) src.copyTo(File(backupDir, f), overwrite = true)
        }
        File(backupDir, "complete").writeText("1")
        true
    } catch (e: Exception) {
        false
    }

    override fun restore(ids: List<String>): Boolean = try {
        if (!hasBackup()) {
            false
        } else {
            for (id in ids) for (f in filesOf(id)) {
                val b = File(backupDir, f)
                if (b.exists()) b.copyTo(File(dir, f), overwrite = true)
            }
            true
        }
    } catch (e: Exception) {
        false
    }

    override fun hasBackup(): Boolean = File(backupDir, "complete").exists()

    override fun dropBackup() {
        backupDir.deleteRecursively()
    }

    override fun setAsideUnreadable(ids: List<String>) {
        try {
            asideDir.mkdirs()
            for (id in ids) for (f in filesOf(id)) File(dir, f).takeIf { it.exists() }?.copyTo(File(asideDir, f), overwrite = true)
        } catch (_: Exception) {
        }
    }
}

/** State in a tiny plain file next to the stores. */
internal class FileState(private val file: File) : AtRestCrypto.StateStore {
    private fun read(): List<String> = try { file.readText().split('\n') } catch (e: Exception) { emptyList() }
    private fun write(state: String, at: Long) {
        val tmp = File(file.path + ".tmp"); tmp.writeText("$state\n$at"); tmp.renameTo(file)
    }
    override var state: String
        get() = read().getOrNull(0)?.takeIf { it.isNotBlank() } ?: AtRestCrypto.STATE_PLAIN
        set(v) = write(v, migratedAt)
    override var migratedAt: Long
        get() = read().getOrNull(1)?.toLongOrNull() ?: 0L
        set(v) = write(state, v)
}

/** Runs [AtRestCrypto.ensure] once per process under a cross-process file lock. */
internal object AtRestCryptoAndroid {
    fun ensure(context: Context, ids: List<String>): AtRestCrypto.Outcome {
        val files = context.filesDir
        RandomAccessFile(File(files, "mmkv_crypt.lock"), "rw").use { raf ->
            raf.channel.lock().use {
                return AtRestCrypto.ensure(
                    KeystoreVault(File(files, "mmkv_crypt.key")),
                    MmkvStores(context),
                    FileState(File(files, "mmkv_crypt.state")),
                    ids, System.currentTimeMillis(),
                )
            }
        }
    }
}
