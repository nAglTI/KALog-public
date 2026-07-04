package org.debs.kalog.core.preferences

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.AUTH_BIOMETRIC_STRONG
import android.security.keystore.KeyProperties.AUTH_DEVICE_CREDENTIAL
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
    private val keyAlias: String = "org.debs.kalog.secure.preferences.auth.v1",
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
        val secretKey = generateSecretKey(keyAlias)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedData = cipher.doFinal(text.toByteArray(charset))
        return cipher.iv to encryptedData
    }

    @Synchronized
    override fun decryptData(iv: ByteArray, encryptedData: ByteArray): String {
        val gcmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(keyAlias), gcmParameterSpec)
        return cipher.doFinal(encryptedData).toString(charset)
    }

    private fun generateSecretKey(alias: String): SecretKey {
        val keyEntry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (keyEntry != null) return keyEntry.secretKey

        keyGenerator.init(
            KeyGenParameterSpec.Builder(alias, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE_GCM)
                .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            AUTH_VALIDITY_SECONDS,
                            AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                    }
                }
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(alias: String): SecretKey {
        return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        private const val AUTH_VALIDITY_SECONDS = 12 * 60 * 60
    }
}
