package com.uvindu.linuxsyncandroid.utils.crypto

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {

    private const val NONCE_SIZE = 12   // 96 bits — standard for GCM
    private const val TAG_SIZE   = 128  // bits

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     * Returns base64(nonce + ciphertext + tag) — mirrors the Python side exactly.
     */
    fun encrypt(plaintext: String, keyBytes: ByteArray): String {
        val nonce = ByteArray(NONCE_SIZE).also {
            java.security.SecureRandom().nextBytes(it)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_SIZE, nonce)
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // pack nonce + ciphertext into one base64 blob — same layout as Python side
        return Base64.encodeToString(nonce + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a base64(nonce + ciphertext + tag) string.
     * Throws if the GCM tag verification fails (tampered message).
     */
    fun decrypt(payload: String, keyBytes: ByteArray): String {
        val raw        = Base64.decode(payload, Base64.NO_WRAP)
        val nonce      = raw.sliceArray(0 until NONCE_SIZE)
        val ciphertext = raw.sliceArray(NONCE_SIZE until raw.size)
        val cipher     = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_SIZE, nonce)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Decodes a base64 key string into raw bytes.
     * The enc_key stored in DataStore is base64 — this converts it for use in encrypt/decrypt.
     */
    fun decodeKey(base64Key: String): ByteArray =
        Base64.decode(base64Key, Base64.NO_WRAP)
}
