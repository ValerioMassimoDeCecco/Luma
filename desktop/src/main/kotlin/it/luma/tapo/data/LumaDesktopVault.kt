package it.luma.tapo.data

import it.luma.tapo.protocol.TapoCrypto
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop equivalent of the Android Keystore vault: local AES-256-GCM encryption of Tapo
 * credentials and protocol session state. The master key is generated once and stored in the
 * local Luma storage file, so no manual passphrase is ever required.
 *
 * The on-disk payload format is identical to the Android implementation:
 * base64( [ivLen byte][iv][AES-GCM ciphertext] ) with the given AAD context.
 */
class LumaDesktopVault(private val storage: LumaDesktopStorage) {

    companion object {
        private const val KEY_PREF = "luma_desktop_master_key_v1"
    }

    @Synchronized
    private fun getOrCreateSecretKey(): ByteArray {
        val saved = storage.getString(KEY_PREF, null)
        if (!saved.isNullOrEmpty()) {
            val bytes = runCatching { TapoCrypto.from64(saved) }.getOrNull()?.takeIf { it.size == 32 }
            if (bytes != null) return bytes
        }
        val generated = TapoCrypto.randomBytes(32)
        storage.edit().putString(KEY_PREF, TapoCrypto.b64(generated)).apply()
        return generated
    }

    fun encrypt(plainText: String, aadContext: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secretKey, "AES"))
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = TapoCrypto.concat(byteArrayOf(iv.size.toByte()), iv, encrypted)
        return TapoCrypto.b64(payload)
    }

    fun decrypt(encodedB64: String, aadContext: String): String? {
        if (encodedB64.isBlank()) return null
        return try {
            val payload = TapoCrypto.from64(encodedB64)
            if (payload.size < 1 + 12 + 16) return null
            val ivLen = payload[0].toInt() and 0xFF
            if (ivLen !in 12..16 || payload.size <= 1 + ivLen + 16) return null
            val iv = payload.copyOfRange(1, 1 + ivLen)
            val cipherBytes = payload.copyOfRange(1 + ivLen, payload.size)

            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secretKey, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(cipherBytes)
            String(plain, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
