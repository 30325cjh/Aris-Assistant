package com.arisassistant.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts user-authored conversation content before it reaches SQLite.
 * Record identity is authenticated as AES-GCM AAD so ciphertext cannot be
 * copied between rows or content columns without detection.
 */
internal interface ConversationCipher {
    fun initializeForNewDatabase()
    fun encrypt(plaintext: String, associatedData: String): String
    fun decrypt(envelope: String, associatedData: String): String
}

internal class AndroidKeystoreConversationCipher : ConversationCipher {
    override fun initializeForNewDatabase() {
        if (loadSecretKey() != null) return

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
        generator.generateKey()
    }

    override fun encrypt(plaintext: String, associatedData: String): String {
        val key = loadSecretKey()
            ?: throw IllegalStateException("The conversation encryption key is missing.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + ciphertext.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(envelope: String, associatedData: String): String {
        require(envelope.startsWith(PREFIX)) { "Conversation content is not encrypted or uses an unsupported format." }
        val payload = Base64.decode(envelope.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) {
            "Encrypted conversation content is truncated."
        }

        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val key = loadSecretKey()
            ?: throw IllegalStateException("The conversation encryption key is missing; refusing to replace it.")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, StandardCharsets.UTF_8)
    }

    private fun loadSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aris.conversation-data.aes-gcm.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc:v1:"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
