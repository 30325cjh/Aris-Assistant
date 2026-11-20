package com.arisassistant.data

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** JVM-only AES-GCM test cipher; production uses the non-exportable Android Keystore key. */
internal class TestConversationCipher : ConversationCipher {
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    override fun initializeForNewDatabase() = Unit

    override fun encrypt(plaintext: String, associatedData: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    override fun decrypt(envelope: String, associatedData: String): String {
        require(envelope.startsWith(PREFIX))
        val payload = Base64.getDecoder().decode(envelope.removePrefix(PREFIX))
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(associatedData.toByteArray(StandardCharsets.UTF_8))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    internal companion object {
        const val PREFIX = "test:enc:v1:"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
