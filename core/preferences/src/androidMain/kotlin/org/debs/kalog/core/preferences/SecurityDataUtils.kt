package org.debs.kalog.core.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.BLOCK_MODE_GCM
import android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
import android.security.keystore.KeyProperties.KEY_ALGORITHM_AES
import android.security.keystore.KeyProperties.PURPOSE_DECRYPT
import android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SecurityDataUtils {
    fun encryptData(text: String): Pair<ByteArray, ByteArray>

    fun decryptData(iv: ByteArray, encryptedData: ByteArray): String
}

internal class SecurityDataUtilsImpl(
    private val keyAlias: String = "org.debs.kalog.secure.preferences",
) : SecurityDataUtils {
    private val provider = "AndroidKeyStore"
    private val cipher by lazy { Cipher.getInstance("AES/GCM/NoPadding") }
    private val charset by lazy { charset("UTF-8") }
    private val keyStore by lazy {
        KeyStore.getInstance(provider).apply {
            load(null)
        }
    }
    private val keyGenerator by lazy { KeyGenerator.getInstance(KEY_ALGORITHM_AES, provider) }

    @Synchronized
    override fun encryptData(text: String): Pair<ByteArray, ByteArray> {
        val secretKey = generateSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedData = cipher.doFinal(text.toByteArray(charset))
        return cipher.iv to encryptedData
    }

    @Synchronized
    override fun decryptData(iv: ByteArray, encryptedData: ByteArray): String {
        val gcmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmParameterSpec)
        return cipher.doFinal(encryptedData).toString(charset)
    }

    private fun generateSecretKey(): SecretKey {
        val keyEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (keyEntry != null) return keyEntry.secretKey

        keyGenerator.init(
            KeyGenParameterSpec.Builder(keyAlias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE_GCM)
                .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey {
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
