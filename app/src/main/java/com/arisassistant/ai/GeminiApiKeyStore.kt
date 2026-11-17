package com.arisassistant.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a user-supplied Gemini API key encrypted with a non-exportable
 * Android Keystore AES-GCM key. The plaintext key is never written to
 * SharedPreferences or logs.
 */
internal class GeminiApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(rawKey: String) {
        val apiKey = rawKey.trim()
        require(apiKey.isNotEmpty()) { "API key must not be empty." }
        require(apiKey.length <= MAX_KEY_LENGTH) { "API key is too long." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        val committed = preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()

        check(committed) { "Unable to persist the encrypted API key." }
    }

    @Synchronized
    fun read(): String? {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val key = loadSecretKey()
            ?: throw IllegalStateException("The encrypted API key exists but its Keystore key is missing.")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP))
        )
        val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
        return String(plaintext, StandardCharsets.UTF_8)
    }

    fun hasSavedKey(): Boolean =
        preferences.contains(KEY_IV) && preferences.contains(KEY_CIPHERTEXT)

    @Synchronized
    fun clear() {
        val committed = preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .commit()
        check(committed) { "Unable to clear the saved API key." }

        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        loadSecretKey()?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadSecretKey(): SecretKey? =
        loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val PREFERENCES_NAME = "aris_secrets"
        const val KEY_ALIAS = "aris.gemini.api-key.aes-gcm.v1"
        const val KEY_IV = "gemini_api_key_iv"
        const val KEY_CIPHERTEXT = "gemini_api_key_ciphertext"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val MAX_KEY_LENGTH = 512
    }
}
