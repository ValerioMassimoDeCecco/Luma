package it.luma.tapo.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import it.luma.tapo.protocol.TapoCrypto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Automatic local AES-256-GCM encryption helper backed by Android Keystore
 * so Tapo account credentials and protocol sessions persist across app restarts
 * without requiring the user to enter a manual passphrase.
 */
class LumaKeystoreVault(private val prefs: SharedPreferences) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "luma_tapo_master_aes_v1"
        private const val FALLBACK_PREF_KEY = "luma_local_fallback_key_v1"
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                return existing.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (_: Throwable) {
            // Fallback for JVM test environments where AndroidKeyStore is not mounted
            val savedB64 = prefs.getString(FALLBACK_PREF_KEY, null)
            val rawBytes = if (!savedB64.isNullOrEmpty()) {
                runCatching { TapoCrypto.from64(savedB64) }.getOrNull()?.takeIf { it.size == 32 }
            } else null

            val keyBytes = rawBytes ?: TapoCrypto.randomBytes(32).also { generated ->
                prefs.edit().putString(FALLBACK_PREF_KEY, TapoCrypto.b64(generated)).apply()
            }
            return SecretKeySpec(keyBytes, "AES")
        }
    }

    fun encrypt(plainText: String, aadContext: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = TapoCrypto.concat( byteArrayOf(iv.size.toByte()), iv, encrypted)
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
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(cipherBytes)
            String(plain, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
