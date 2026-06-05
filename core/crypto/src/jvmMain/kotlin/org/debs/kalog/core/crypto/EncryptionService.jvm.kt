package org.debs.kalog.core.crypto

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.win32.StdCallLibrary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

actual fun createEncryptionService(): EncryptionService = JvmPlatformRsaOaepEncryptionService()

class JvmPlatformRsaOaepEncryptionService(
    private val applicationId: String = "org.debs.kalog",
    private val delegate: RsaOaepEncryptionService = RsaOaepEncryptionService(),
) : EncryptionService {
    private val backend: JvmRsaPrivateKeyBackend? by lazy {
        val normalizedOsName = System.getProperty("os.name").orEmpty().lowercase(Locale.US)
        when {
            normalizedOsName.contains("windows") -> WindowsCngRsaPrivateKeyBackend(applicationId)
            normalizedOsName.contains("mac") -> MacOsSecKeyRsaPrivateKeyBackend(applicationId)
            else -> null
        }
    }

    override val algorithmLabel: String = delegate.algorithmLabel
    override val maxPayloadBytesPerChunk: Int = delegate.maxPayloadBytesPerChunk

    override suspend fun generateKeyPair(): GeneratedKeyPair {
        return backend?.generateKeyPair() ?: delegate.generateKeyPair()
    }

    override suspend fun generateAttachmentKey(): GeneratedAttachmentKey = delegate.generateAttachmentKey()

    override suspend fun encrypt(message: String, publicKey: String): String = delegate.encrypt(message, publicKey)

    override suspend fun decrypt(message: String, privateKey: String): String = delegate.decrypt(message, privateKey)

    override suspend fun decrypt(message: String, privateKeyRef: PrivateKeyRef): String {
        return when (privateKeyRef) {
            is PrivateKeyRef.Exported -> delegate.decrypt(message, privateKeyRef.value)
            is PrivateKeyRef.PlatformAlias -> platformBackendFor(privateKeyRef).decryptFromChunks(
                chunks = listOf(message),
                alias = privateKeyRef.alias,
            )
        }
    }

    override suspend fun encryptAttachment(bytes: ByteArray, key: String): ByteArray {
        return delegate.encryptAttachment(bytes, key)
    }

    override suspend fun decryptAttachment(bytes: ByteArray, key: String): ByteArray {
        return delegate.decryptAttachment(bytes, key)
    }

    override suspend fun encryptToChunks(
        message: String,
        publicKey: String,
        chunkSizeBytes: Int,
    ): List<String> {
        return delegate.encryptToChunks(message, publicKey, chunkSizeBytes)
    }

    override suspend fun decryptFromChunks(chunks: List<String>, privateKey: String): String {
        return delegate.decryptFromChunks(chunks, privateKey)
    }

    override suspend fun decryptFromChunks(chunks: List<String>, privateKeyRef: PrivateKeyRef): String {
        return when (privateKeyRef) {
            is PrivateKeyRef.Exported -> delegate.decryptFromChunks(chunks, privateKeyRef.value)
            is PrivateKeyRef.PlatformAlias -> platformBackendFor(privateKeyRef).decryptFromChunks(
                chunks = chunks,
                alias = privateKeyRef.alias,
            )
        }
    }

    override suspend fun exportPrivateKey(privateKeyRef: PrivateKeyRef): PrivateKeyRef.Exported {
        return when (privateKeyRef) {
            is PrivateKeyRef.Exported -> privateKeyRef
            is PrivateKeyRef.PlatformAlias -> PrivateKeyRef.Exported(
                platformBackendFor(privateKeyRef).exportPrivateKey(alias = privateKeyRef.alias),
            )
        }
    }

    override suspend fun importPrivateKey(
        publicKey: String,
        privateKey: PrivateKeyRef.Exported,
    ): PrivateKeyRef {
        return backend?.importPrivateKey(publicKey = publicKey, privateKey = privateKey.value)
            ?: privateKey
    }

    override suspend fun deletePrivateKey(privateKeyRef: PrivateKeyRef) {
        if (privateKeyRef is PrivateKeyRef.PlatformAlias) {
            platformBackendFor(privateKeyRef).deleteKey(privateKeyRef.alias)
        }
    }

    private fun platformBackendFor(privateKeyRef: PrivateKeyRef.PlatformAlias): JvmRsaPrivateKeyBackend {
        val platformBackend = checkNotNull(backend) {
            "Platform private key reference '${privateKeyRef.provider}' cannot be used on this desktop OS."
        }
        check(platformBackend.providerId == privateKeyRef.provider) {
            "Platform private key provider '${privateKeyRef.provider}' is not available on this desktop OS."
        }
        return platformBackend
    }
}

private interface JvmRsaPrivateKeyBackend {
    val providerId: String

    suspend fun generateKeyPair(): GeneratedKeyPair

    suspend fun decryptFromChunks(chunks: List<String>, alias: String): String

    suspend fun exportPrivateKey(alias: String): String

    suspend fun importPrivateKey(publicKey: String, privateKey: String): PrivateKeyRef.PlatformAlias

    suspend fun deleteKey(alias: String)
}

private class WindowsCngRsaPrivateKeyBackend(
    applicationId: String,
) : JvmRsaPrivateKeyBackend {
    override val providerId: String = WINDOWS_CNG_PROVIDER_ID
    private val aliasPrefix = "${applicationId.sanitizeAliasPrefix()}.rsa"
    private val providerHandle: Pointer by lazy {
        openProvider(MICROSOFT_SOFTWARE_KEY_STORAGE_PROVIDER)
    }
    private val legacyProviderHandle: Pointer? by lazy {
        runCatching { openProvider(MICROSOFT_PLATFORM_CRYPTO_PROVIDER) }.getOrNull()
    }
    private val keyHandles = ConcurrentHashMap<String, Pointer>()

    override suspend fun generateKeyPair(): GeneratedKeyPair = withContext(Dispatchers.Default) {
        val alias = "$aliasPrefix.${UUID.randomUUID()}"
        val keyReference = PointerByReference()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptCreatePersistedKey(
                providerHandle,
                keyReference,
                WString(NCRYPT_RSA_ALGORITHM),
                WString(alias),
                0,
                0,
            ),
            operation = "create persisted RSA key",
        )

        val keyHandle = keyReference.value
        var keepHandle = false
        try {
            setDwordProperty(keyHandle, NCRYPT_LENGTH_PROPERTY, RSA_KEY_SIZE_BITS)
            setDwordProperty(keyHandle, NCRYPT_KEY_USAGE_PROPERTY, NCRYPT_ALLOW_DECRYPT_FLAG)
            setDwordProperty(
                keyHandle,
                NCRYPT_EXPORT_POLICY_PROPERTY,
                NCRYPT_ALLOW_EXPORT_FLAG or NCRYPT_ALLOW_PLAINTEXT_EXPORT_FLAG,
            )
            checkStatus(
                status = NCryptLibrary.INSTANCE.NCryptFinalizeKey(keyHandle, 0),
                operation = "finalize RSA key",
            )
            keyHandles[alias] = keyHandle
            keepHandle = true
            GeneratedKeyPair(
                publicKey = Base64.getEncoder().encodeToString(exportPublicKeyPkcs1Der(keyHandle)),
                privateKeyRef = PrivateKeyRef.PlatformAlias(
                    provider = WINDOWS_CNG_PROVIDER_ID,
                    alias = alias,
                ),
            )
        } finally {
            if (!keepHandle) {
                NCryptLibrary.INSTANCE.NCryptFreeObject(keyHandle)
            }
        }
    }

    override suspend fun decryptFromChunks(chunks: List<String>, alias: String): String = withContext(Dispatchers.Default) {
        val keyHandle = keyHandles.computeIfAbsent(alias) { openKey(it) }
        chunks
            .flatMap { chunk -> decryptChunk(keyHandle, Base64.getDecoder().decode(chunk)).asIterable() }
            .toByteArray()
            .decodeToString()
    }

    override suspend fun exportPrivateKey(alias: String): String = withContext(Dispatchers.Default) {
        val keyHandle = keyHandles.computeIfAbsent(alias) { openKey(it) }
        Base64.getEncoder().encodeToString(exportPrivateKeyPkcs1Der(keyHandle))
    }

    override suspend fun importPrivateKey(publicKey: String, privateKey: String): PrivateKeyRef.PlatformAlias =
        withContext(Dispatchers.Default) {
            val alias = "$aliasPrefix.${UUID.randomUUID()}"
            val keyReference = PointerByReference()
            checkStatus(
                status = NCryptLibrary.INSTANCE.NCryptCreatePersistedKey(
                    providerHandle,
                    keyReference,
                    WString(NCRYPT_RSA_ALGORITHM),
                    WString(alias),
                    0,
                    0,
                ),
                operation = "create imported persisted RSA key",
            )

            val keyHandle = keyReference.value
            var keepHandle = false
            try {
                setDwordProperty(keyHandle, NCRYPT_LENGTH_PROPERTY, RSA_KEY_SIZE_BITS)
                setDwordProperty(keyHandle, NCRYPT_KEY_USAGE_PROPERTY, NCRYPT_ALLOW_DECRYPT_FLAG)
                setDwordProperty(
                    keyHandle,
                    NCRYPT_EXPORT_POLICY_PROPERTY,
                    NCRYPT_ALLOW_EXPORT_FLAG or NCRYPT_ALLOW_PLAINTEXT_EXPORT_FLAG,
                )
                setBytesProperty(
                    keyHandle,
                    BCRYPT_RSAFULLPRIVATE_BLOB,
                    decodePkcs1RsaPrivateKeyToCngBlob(Base64.getDecoder().decode(privateKey)),
                )
                checkStatus(
                    status = NCryptLibrary.INSTANCE.NCryptFinalizeKey(keyHandle, 0),
                    operation = "finalize imported RSA key",
                )
                val exportedPublicKey = Base64.getEncoder().encodeToString(exportPublicKeyPkcs1Der(keyHandle))
                check(exportedPublicKey == publicKey) {
                    "Imported Windows CNG private key does not match the stored public key."
                }
                keyHandles[alias] = keyHandle
                keepHandle = true
                PrivateKeyRef.PlatformAlias(
                    provider = WINDOWS_CNG_PROVIDER_ID,
                    alias = alias,
                )
            } finally {
                if (!keepHandle) {
                    runCatching { NCryptLibrary.INSTANCE.NCryptDeleteKey(keyHandle, 0) }
                    runCatching { NCryptLibrary.INSTANCE.NCryptFreeObject(keyHandle) }
                }
            }
        }

    override suspend fun deleteKey(alias: String) = withContext(Dispatchers.Default) {
        val keyHandle = keyHandles.remove(alias) ?: openKey(alias)
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptDeleteKey(keyHandle, 0),
            operation = "delete RSA key",
        )
        Unit
    }

    private fun openProvider(providerName: String): Pointer {
        val providerReference = PointerByReference()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptOpenStorageProvider(
                providerReference,
                WString(providerName),
                0,
            ),
            operation = "open $providerName",
        )
        return providerReference.value
    }

    private fun openKey(alias: String): Pointer {
        tryOpenKey(providerHandle, alias)?.let { return it }
        legacyProviderHandle?.let { legacyHandle ->
            tryOpenKey(legacyHandle, alias)?.let { return it }
        }
        error("Windows CNG open RSA key failed for alias '$alias'.")
    }

    private fun tryOpenKey(provider: Pointer, alias: String): Pointer? {
        val keyReference = PointerByReference()
        val status = NCryptLibrary.INSTANCE.NCryptOpenKey(
            provider,
            keyReference,
            WString(alias),
            0,
            0,
        )
        return if (status == ERROR_SUCCESS) keyReference.value else null
    }

    private fun setDwordProperty(handle: Pointer, property: String, value: Int) {
        val memory = Memory(Int.SIZE_BYTES.toLong())
        memory.setInt(0, value)
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptSetProperty(
                handle,
                WString(property),
                memory,
                Int.SIZE_BYTES,
                0,
            ),
            operation = "set $property",
        )
    }

    private fun setBytesProperty(handle: Pointer, property: String, value: ByteArray) {
        val memory = value.toMemory()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptSetProperty(
                handle,
                WString(property),
                memory,
                value.size,
                0,
            ),
            operation = "set $property",
        )
    }

    private fun exportPublicKeyPkcs1Der(keyHandle: Pointer): ByteArray {
        val sizeReference = IntByReference()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptExportKey(
                keyHandle,
                null,
                WString(BCRYPT_RSAPUBLIC_BLOB),
                null,
                null,
                0,
                sizeReference,
                0,
            ),
            operation = "measure RSA public key export",
        )
        val output = Memory(sizeReference.value.toLong())
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptExportKey(
                keyHandle,
                null,
                WString(BCRYPT_RSAPUBLIC_BLOB),
                null,
                output,
                sizeReference.value,
                sizeReference,
                0,
            ),
            operation = "export RSA public key",
        )
        return encodePkcs1RsaPublicKey(output.getByteArray(0, sizeReference.value))
    }

    private fun exportPrivateKeyPkcs1Der(keyHandle: Pointer): ByteArray {
        val sizeReference = IntByReference()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptExportKey(
                keyHandle,
                null,
                WString(BCRYPT_RSAFULLPRIVATE_BLOB),
                null,
                null,
                0,
                sizeReference,
                0,
            ),
            operation = "measure RSA private key export",
        )
        val output = Memory(sizeReference.value.toLong())
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptExportKey(
                keyHandle,
                null,
                WString(BCRYPT_RSAFULLPRIVATE_BLOB),
                null,
                output,
                sizeReference.value,
                sizeReference,
                0,
            ),
            operation = "export RSA private key",
        )
        return encodePkcs1RsaPrivateKey(output.getByteArray(0, sizeReference.value))
    }

    private fun decryptChunk(keyHandle: Pointer, encryptedBytes: ByteArray): ByteArray {
        val input = encryptedBytes.toMemory()
        val paddingInfo = WindowsCngOaepPaddingInfo().apply {
            pszAlgId = WString(BCRYPT_SHA256_ALGORITHM)
            pbLabel = Pointer.NULL
            cbLabel = 0
            write()
        }
        val sizeReference = IntByReference()
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptDecrypt(
                keyHandle,
                input,
                encryptedBytes.size,
                paddingInfo.pointer,
                null,
                0,
                sizeReference,
                NCRYPT_PAD_OAEP_FLAG,
            ),
            operation = "measure RSA decrypt",
        )
        val output = Memory(sizeReference.value.toLong())
        checkStatus(
            status = NCryptLibrary.INSTANCE.NCryptDecrypt(
                keyHandle,
                input,
                encryptedBytes.size,
                paddingInfo.pointer,
                output,
                sizeReference.value,
                sizeReference,
                NCRYPT_PAD_OAEP_FLAG,
            ),
            operation = "decrypt RSA chunk",
        )
        return output.getByteArray(0, sizeReference.value)
    }

    private fun checkStatus(status: Int, operation: String) {
        check(status == ERROR_SUCCESS) {
            "Windows CNG $operation failed with status 0x${status.toUInt().toString(16)}."
        }
    }

    private interface NCryptLibrary : StdCallLibrary {
        fun NCryptOpenStorageProvider(
            phProvider: PointerByReference,
            pszProviderName: WString,
            dwFlags: Int,
        ): Int

        fun NCryptCreatePersistedKey(
            hProvider: Pointer,
            phKey: PointerByReference,
            pszAlgId: WString,
            pszKeyName: WString,
            dwLegacyKeySpec: Int,
            dwFlags: Int,
        ): Int

        fun NCryptOpenKey(
            hProvider: Pointer,
            phKey: PointerByReference,
            pszKeyName: WString,
            dwLegacyKeySpec: Int,
            dwFlags: Int,
        ): Int

        fun NCryptSetProperty(
            hObject: Pointer,
            pszProperty: WString,
            pbInput: Pointer,
            cbInput: Int,
            dwFlags: Int,
        ): Int

        fun NCryptFinalizeKey(hKey: Pointer, dwFlags: Int): Int

        fun NCryptExportKey(
            hKey: Pointer,
            hExportKey: Pointer?,
            pszBlobType: WString,
            pParameterList: Pointer?,
            pbOutput: Pointer?,
            cbOutput: Int,
            pcbResult: IntByReference,
            dwFlags: Int,
        ): Int

        fun NCryptDecrypt(
            hKey: Pointer,
            pbInput: Pointer,
            cbInput: Int,
            pPaddingInfo: Pointer?,
            pbOutput: Pointer?,
            cbOutput: Int,
            pcbResult: IntByReference,
            dwFlags: Int,
        ): Int

        fun NCryptDeleteKey(hKey: Pointer, dwFlags: Int): Int

        fun NCryptFreeObject(hObject: Pointer): Int

        companion object {
            val INSTANCE: NCryptLibrary = Native.load("ncrypt", NCryptLibrary::class.java)
        }
    }

    private companion object {
        private const val WINDOWS_CNG_PROVIDER_ID = "windows-cng"
        private const val MICROSOFT_SOFTWARE_KEY_STORAGE_PROVIDER = "Microsoft Software Key Storage Provider"
        private const val MICROSOFT_PLATFORM_CRYPTO_PROVIDER = "Microsoft Platform Crypto Provider"
        private const val NCRYPT_RSA_ALGORITHM = "RSA"
        private const val NCRYPT_LENGTH_PROPERTY = "Length"
        private const val NCRYPT_KEY_USAGE_PROPERTY = "Key Usage"
        private const val NCRYPT_EXPORT_POLICY_PROPERTY = "Export Policy"
        private const val BCRYPT_RSAPUBLIC_BLOB = "RSAPUBLICBLOB"
        private const val BCRYPT_RSAFULLPRIVATE_BLOB = "RSAFULLPRIVATEBLOB"
        private const val BCRYPT_SHA256_ALGORITHM = "SHA256"
        private const val NCRYPT_ALLOW_DECRYPT_FLAG = 0x00000001
        private const val NCRYPT_ALLOW_EXPORT_FLAG = 0x00000001
        private const val NCRYPT_ALLOW_PLAINTEXT_EXPORT_FLAG = 0x00000002
        private const val NCRYPT_PAD_OAEP_FLAG = 0x00000004
        private const val RSA_KEY_SIZE_BITS = 2048
        private const val ERROR_SUCCESS = 0
    }
}

@Structure.FieldOrder("pszAlgId", "pbLabel", "cbLabel")
class WindowsCngOaepPaddingInfo : Structure() {
    @JvmField
    var pszAlgId: WString? = null

    @JvmField
    var pbLabel: Pointer? = null

    @JvmField
    var cbLabel: Int = 0
}

private class MacOsSecKeyRsaPrivateKeyBackend(
    applicationId: String,
) : JvmRsaPrivateKeyBackend {
    override val providerId: String = MACOS_SECKEY_PROVIDER_ID
    private val aliasPrefix = "${applicationId.sanitizeAliasPrefix()}.rsa"
    private val keyRefs = ConcurrentHashMap<String, CoreFoundation.CFTypeRef>()

    override suspend fun generateKeyPair(): GeneratedKeyPair = withContext(Dispatchers.Default) {
        val alias = "$aliasPrefix.${UUID.randomUUID()}"
        val privateKey = createPrivateKey(alias)
        keyRefs[alias] = privateKey
        val publicKey = MacOsSecurityFramework.INSTANCE.SecKeyCopyPublicKey(privateKey)
            ?: error("macOS SecKey failed to copy public key.")
        try {
            GeneratedKeyPair(
                publicKey = Base64.getEncoder().encodeToString(
                    normalizeRsaPublicKeyToPkcs1(copyExternalRepresentation(publicKey)),
                ),
                privateKeyRef = PrivateKeyRef.PlatformAlias(
                    provider = MACOS_SECKEY_PROVIDER_ID,
                    alias = alias,
                ),
            )
        } finally {
            publicKey.release()
        }
    }

    override suspend fun decryptFromChunks(chunks: List<String>, alias: String): String = withContext(Dispatchers.Default) {
        val privateKey = keyRefs.computeIfAbsent(alias) { findPrivateKey(it) }
        chunks
            .flatMap { chunk -> decryptChunk(privateKey, Base64.getDecoder().decode(chunk)).asIterable() }
            .toByteArray()
            .decodeToString()
    }

    override suspend fun exportPrivateKey(alias: String): String = withContext(Dispatchers.Default) {
        val privateKey = keyRefs.computeIfAbsent(alias) { findPrivateKey(it) }
        Base64.getEncoder().encodeToString(
            normalizeRsaPrivateKeyToPkcs1(copyExternalRepresentation(privateKey)),
        )
    }

    override suspend fun importPrivateKey(publicKey: String, privateKey: String): PrivateKeyRef.PlatformAlias =
        withContext(Dispatchers.Default) {
            val alias = "$aliasPrefix.${UUID.randomUUID()}"
            val importedPrivateKey = createPrivateKeyFromData(alias, privateKey)
            var keepKey = false
            try {
                val importedPublicKey = MacOsSecurityFramework.INSTANCE.SecKeyCopyPublicKey(importedPrivateKey)
                    ?: error("macOS SecKey failed to copy imported public key.")
                try {
                    val exportedPublicKey = Base64.getEncoder().encodeToString(
                        normalizeRsaPublicKeyToPkcs1(copyExternalRepresentation(importedPublicKey)),
                    )
                    check(exportedPublicKey == publicKey) {
                        "Imported macOS SecKey private key does not match the stored public key."
                    }
                } finally {
                    importedPublicKey.release()
                }
                keyRefs[alias] = importedPrivateKey
                keepKey = true
                PrivateKeyRef.PlatformAlias(
                    provider = MACOS_SECKEY_PROVIDER_ID,
                    alias = alias,
                )
            } finally {
                if (!keepKey) {
                    importedPrivateKey.release()
                    val query = privateKeyQuery(alias)
                    try {
                        runCatching { MacOsSecurityFramework.INSTANCE.SecItemDelete(query) }
                    } finally {
                        query.release()
                    }
                }
            }
        }

    override suspend fun deleteKey(alias: String) = withContext(Dispatchers.Default) {
        val query = privateKeyQuery(alias)
        try {
            val status = MacOsSecurityFramework.INSTANCE.SecItemDelete(query)
            check(status == ERR_SEC_SUCCESS || status == ERR_SEC_ITEM_NOT_FOUND) {
                "macOS SecKey private key delete failed with OSStatus $status."
            }
        } finally {
            query.release()
            keyRefs.remove(alias)?.release()
        }
    }

    private fun createPrivateKey(alias: String): CoreFoundation.CFTypeRef {
        val privateKeyAttributes = mutableDictionary()
        val attributes = mutableDictionary()
        val error = PointerByReference()
        try {
            privateKeyAttributes.setSharedValue(MacOsSecurityConstants.kSecAttrIsPermanent, MacOsSecurityConstants.kCFBooleanTrue)
            privateKeyAttributes.setSharedValue(MacOsSecurityConstants.kSecAttrIsExtractable, MacOsSecurityConstants.kCFBooleanTrue)
            privateKeyAttributes.setOwnedValue(MacOsSecurityConstants.kSecAttrApplicationTag, cfData(alias.toByteArray(StandardCharsets.UTF_8)))

            attributes.setSharedValue(MacOsSecurityConstants.kSecAttrKeyType, MacOsSecurityConstants.kSecAttrKeyTypeRSA)
            attributes.setOwnedValue(MacOsSecurityConstants.kSecAttrKeySizeInBits, cfNumber(RSA_KEY_SIZE_BITS))
            attributes.setOwnedValue(MacOsSecurityConstants.kSecPrivateKeyAttrs, privateKeyAttributes)

            return MacOsSecurityFramework.INSTANCE.SecKeyCreateRandomKey(attributes, error)
                ?: error("macOS SecKey key generation failed${errorMessage(error)}.")
        } finally {
            attributes.release()
        }
    }

    private fun createPrivateKeyFromData(alias: String, privateKey: String): CoreFoundation.CFTypeRef {
        val attributes = privateKeyImportAttributes(alias)
        val privateKeyData = cfData(Base64.getDecoder().decode(privateKey))
        val error = PointerByReference()
        try {
            return MacOsSecurityFramework.INSTANCE.SecKeyCreateWithData(
                privateKeyData,
                attributes,
                error,
            ) ?: error("macOS SecKey private key import failed${errorMessage(error)}.")
        } finally {
            privateKeyData.release()
            attributes.release()
        }
    }

    private fun privateKeyImportAttributes(alias: String): CoreFoundation.CFMutableDictionaryRef {
        return mutableDictionary().apply {
            setSharedValue(MacOsSecurityConstants.kSecAttrKeyType, MacOsSecurityConstants.kSecAttrKeyTypeRSA)
            setSharedValue(MacOsSecurityConstants.kSecAttrKeyClass, MacOsSecurityConstants.kSecAttrKeyClassPrivate)
            setOwnedValue(MacOsSecurityConstants.kSecAttrKeySizeInBits, cfNumber(RSA_KEY_SIZE_BITS))
            setSharedValue(MacOsSecurityConstants.kSecAttrIsPermanent, MacOsSecurityConstants.kCFBooleanTrue)
            setSharedValue(MacOsSecurityConstants.kSecAttrIsExtractable, MacOsSecurityConstants.kCFBooleanTrue)
            setOwnedValue(MacOsSecurityConstants.kSecAttrApplicationTag, cfData(alias.toByteArray(StandardCharsets.UTF_8)))
        }
    }

    private fun findPrivateKey(alias: String): CoreFoundation.CFTypeRef {
        val query = privateKeyQuery(alias)
        try {
            query.setSharedValue(MacOsSecurityConstants.kSecReturnRef, MacOsSecurityConstants.kCFBooleanTrue)

            val result = PointerByReference()
            val status = MacOsSecurityFramework.INSTANCE.SecItemCopyMatching(query, result)
            check(status == ERR_SEC_SUCCESS) {
                "macOS SecKey private key lookup failed with OSStatus $status."
            }
            return CoreFoundation.CFTypeRef(result.value)
        } finally {
            query.release()
        }
    }

    private fun privateKeyQuery(alias: String): CoreFoundation.CFMutableDictionaryRef {
        return mutableDictionary().apply {
            setSharedValue(MacOsSecurityConstants.kSecClass, MacOsSecurityConstants.kSecClassKey)
            setSharedValue(MacOsSecurityConstants.kSecAttrKeyType, MacOsSecurityConstants.kSecAttrKeyTypeRSA)
            setSharedValue(MacOsSecurityConstants.kSecAttrKeyClass, MacOsSecurityConstants.kSecAttrKeyClassPrivate)
            setOwnedValue(MacOsSecurityConstants.kSecAttrApplicationTag, cfData(alias.toByteArray(StandardCharsets.UTF_8)))
        }
    }

    private fun copyExternalRepresentation(key: CoreFoundation.CFTypeRef): ByteArray {
        val error = PointerByReference()
        val data = MacOsSecurityFramework.INSTANCE.SecKeyCopyExternalRepresentation(key, error)
            ?: error("macOS SecKey public key export failed${errorMessage(error)}.")
        try {
            return data.bytePtr.getByteArray(0, data.length)
        } finally {
            data.release()
        }
    }

    private fun decryptChunk(privateKey: CoreFoundation.CFTypeRef, encryptedBytes: ByteArray): ByteArray {
        val input = cfData(encryptedBytes)
        val error = PointerByReference()
        try {
            val output = MacOsSecurityFramework.INSTANCE.SecKeyCreateDecryptedData(
                privateKey,
                MacOsSecurityConstants.kSecKeyAlgorithmRSAEncryptionOAEPSHA256,
                input,
                error,
            ) ?: error("macOS SecKey decrypt failed${errorMessage(error)}.")
            try {
                return output.bytePtr.getByteArray(0, output.length)
            } finally {
                output.release()
            }
        } finally {
            input.release()
        }
    }

    private fun errorMessage(errorReference: PointerByReference): String {
        val pointer = errorReference.value ?: return ""
        val description = CoreFoundation.INSTANCE.CFCopyDescription(CoreFoundation.CFTypeRef(pointer))
            ?: return ""
        return try {
            ": ${description.stringValue()}"
        } finally {
            description.release()
        }
    }

    private companion object {
        private const val MACOS_SECKEY_PROVIDER_ID = "macos-seckey"
        private const val RSA_KEY_SIZE_BITS = 2048
        private const val ERR_SEC_SUCCESS = 0
        private const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }
}

private interface MacOsSecurityFramework : Library {
    fun SecKeyCreateRandomKey(
        parameters: CoreFoundation.CFDictionaryRef,
        error: PointerByReference?,
    ): CoreFoundation.CFTypeRef?

    fun SecKeyCopyPublicKey(key: CoreFoundation.CFTypeRef): CoreFoundation.CFTypeRef?

    fun SecKeyCopyExternalRepresentation(
        key: CoreFoundation.CFTypeRef,
        error: PointerByReference?,
    ): CoreFoundation.CFDataRef?

    fun SecKeyCreateWithData(
        keyData: CoreFoundation.CFDataRef,
        attributes: CoreFoundation.CFDictionaryRef,
        error: PointerByReference?,
    ): CoreFoundation.CFTypeRef?

    fun SecKeyCreateDecryptedData(
        key: CoreFoundation.CFTypeRef,
        algorithm: CoreFoundation.CFStringRef,
        ciphertext: CoreFoundation.CFDataRef,
        error: PointerByReference?,
    ): CoreFoundation.CFDataRef?

    fun SecItemCopyMatching(
        query: CoreFoundation.CFDictionaryRef,
        result: PointerByReference,
    ): Int

    fun SecItemDelete(query: CoreFoundation.CFDictionaryRef): Int

    companion object {
        val INSTANCE: MacOsSecurityFramework = Native.load("Security", MacOsSecurityFramework::class.java)
    }
}

private object MacOsSecurityConstants {
    val coreFoundation: CoreFoundation = CoreFoundation.INSTANCE
    val cfTypeDictionaryKeyCallbacks: Pointer = NativeLibrary
        .getInstance("CoreFoundation")
        .getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    val cfTypeDictionaryValueCallbacks: Pointer = NativeLibrary
        .getInstance("CoreFoundation")
        .getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")
    val kCFBooleanTrue: CoreFoundation.CFBooleanRef = CoreFoundation.CFBooleanRef(
        NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFBooleanTrue").getPointer(0),
    )
    val kCFBooleanFalse: CoreFoundation.CFBooleanRef = CoreFoundation.CFBooleanRef(
        NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFBooleanFalse").getPointer(0),
    )
    val kSecClass: CoreFoundation.CFStringRef = securityStringConstant("kSecClass")
    val kSecClassKey: CoreFoundation.CFStringRef = securityStringConstant("kSecClassKey")
    val kSecAttrKeyType: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrKeyType")
    val kSecAttrKeyTypeRSA: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrKeyTypeRSA")
    val kSecAttrKeySizeInBits: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrKeySizeInBits")
    val kSecPrivateKeyAttrs: CoreFoundation.CFStringRef = securityStringConstant("kSecPrivateKeyAttrs")
    val kSecAttrIsPermanent: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrIsPermanent")
    val kSecAttrIsExtractable: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrIsExtractable")
    val kSecAttrApplicationTag: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrApplicationTag")
    val kSecAttrKeyClass: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrKeyClass")
    val kSecAttrKeyClassPrivate: CoreFoundation.CFStringRef = securityStringConstant("kSecAttrKeyClassPrivate")
    val kSecReturnRef: CoreFoundation.CFStringRef = securityStringConstant("kSecReturnRef")
    val kSecKeyAlgorithmRSAEncryptionOAEPSHA256: CoreFoundation.CFStringRef = securityStringConstant(
        "kSecKeyAlgorithmRSAEncryptionOAEPSHA256",
    )

    private fun securityStringConstant(name: String): CoreFoundation.CFStringRef {
        return CoreFoundation.CFStringRef(
            NativeLibrary.getInstance("Security").getGlobalVariableAddress(name).getPointer(0),
        )
    }
}

private fun encodePkcs1RsaPublicKey(cngPublicBlob: ByteArray): ByteArray {
    val buffer = ByteBuffer.wrap(cngPublicBlob).order(ByteOrder.LITTLE_ENDIAN)
    val magic = buffer.int
    require(magic == 0x31415352) { "Unsupported RSA public key blob magic 0x${magic.toUInt().toString(16)}." }
    buffer.int
    val publicExponentSize = buffer.int
    val modulusSize = buffer.int
    buffer.int
    buffer.int

    val publicExponent = ByteArray(publicExponentSize)
    val modulus = ByteArray(modulusSize)
    buffer.get(publicExponent)
    buffer.get(modulus)

    return derSequence(
        derInteger(modulus) + derInteger(publicExponent),
    )
}

private fun encodePkcs1RsaPrivateKey(cngPrivateBlob: ByteArray): ByteArray {
    val buffer = ByteBuffer.wrap(cngPrivateBlob).order(ByteOrder.LITTLE_ENDIAN)
    val magic = buffer.int
    require(magic == 0x33415352) { "Unsupported RSA private key blob magic 0x${magic.toUInt().toString(16)}." }
    buffer.int
    val publicExponentSize = buffer.int
    val modulusSize = buffer.int
    val prime1Size = buffer.int
    val prime2Size = buffer.int

    val publicExponent = ByteArray(publicExponentSize)
    val modulus = ByteArray(modulusSize)
    val prime1 = ByteArray(prime1Size)
    val prime2 = ByteArray(prime2Size)
    val exponent1 = ByteArray(prime1Size)
    val exponent2 = ByteArray(prime2Size)
    val coefficient = ByteArray(prime1Size)
    val privateExponent = ByteArray(modulusSize)
    buffer.get(publicExponent)
    buffer.get(modulus)
    buffer.get(prime1)
    buffer.get(prime2)
    buffer.get(exponent1)
    buffer.get(exponent2)
    buffer.get(coefficient)
    buffer.get(privateExponent)

    return derSequence(
        derInteger(byteArrayOf(0)) +
            derInteger(modulus) +
            derInteger(publicExponent) +
            derInteger(privateExponent) +
            derInteger(prime1) +
            derInteger(prime2) +
            derInteger(exponent1) +
            derInteger(exponent2) +
            derInteger(coefficient),
    )
}

private fun decodePkcs1RsaPrivateKeyToCngBlob(der: ByteArray): ByteArray {
    val key = readPkcs1RsaPrivateKey(der)
    val modulus = key.modulus.toUnsignedBytes()
    val prime1 = key.prime1.toUnsignedBytes()
    val prime2 = key.prime2.toUnsignedBytes()
    val modulusSize = modulus.size
    val prime1Size = prime1.size
    val prime2Size = prime2.size
    val publicExponent = key.publicExponent.toUnsignedBytes()

    return buildList<Byte> {
        addIntLe(0x33415352)
        addIntLe(modulusSize * 8)
        addIntLe(publicExponent.size)
        addIntLe(modulusSize)
        addIntLe(prime1Size)
        addIntLe(prime2Size)
        addAll(publicExponent.asList())
        addAll(modulus.toFixedSize(modulusSize).asList())
        addAll(prime1.toFixedSize(prime1Size).asList())
        addAll(prime2.toFixedSize(prime2Size).asList())
        addAll(key.exponent1.toUnsignedBytes().toFixedSize(prime1Size).asList())
        addAll(key.exponent2.toUnsignedBytes().toFixedSize(prime2Size).asList())
        addAll(key.coefficient.toUnsignedBytes().toFixedSize(prime1Size).asList())
        addAll(key.privateExponent.toUnsignedBytes().toFixedSize(modulusSize).asList())
    }.toByteArray()
}

private fun readPkcs1RsaPrivateKey(der: ByteArray): RsaPrivateKeyComponents {
    val reader = DerReader(der)
    val sequenceReader = DerReader(reader.readElement(expectedTag = 0x30))
    sequenceReader.readElement(expectedTag = 0x02)
    return RsaPrivateKeyComponents(
        modulus = sequenceReader.readElement(expectedTag = 0x02),
        publicExponent = sequenceReader.readElement(expectedTag = 0x02),
        privateExponent = sequenceReader.readElement(expectedTag = 0x02),
        prime1 = sequenceReader.readElement(expectedTag = 0x02),
        prime2 = sequenceReader.readElement(expectedTag = 0x02),
        exponent1 = sequenceReader.readElement(expectedTag = 0x02),
        exponent2 = sequenceReader.readElement(expectedTag = 0x02),
        coefficient = sequenceReader.readElement(expectedTag = 0x02),
    )
}

private data class RsaPrivateKeyComponents(
    val modulus: ByteArray,
    val publicExponent: ByteArray,
    val privateExponent: ByteArray,
    val prime1: ByteArray,
    val prime2: ByteArray,
    val exponent1: ByteArray,
    val exponent2: ByteArray,
    val coefficient: ByteArray,
)

private fun derSequence(content: ByteArray): ByteArray {
    return byteArrayOf(0x30) + derLength(content.size) + content
}

private fun derInteger(value: ByteArray): ByteArray {
    val unsigned = value.dropLeadingZeroes().ensurePositiveDerInteger()
    return byteArrayOf(0x02) + derLength(unsigned.size) + unsigned
}

private fun derLength(length: Int): ByteArray {
    require(length >= 0) { "DER length must be non-negative." }
    if (length < 0x80) return byteArrayOf(length.toByte())

    val bytes = mutableListOf<Byte>()
    var remaining = length
    while (remaining > 0) {
        bytes.add(0, (remaining and 0xff).toByte())
        remaining = remaining ushr 8
    }
    return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
}

private fun normalizeRsaPublicKeyToPkcs1(der: ByteArray): ByteArray {
    val reader = DerReader(der)
    val sequence = reader.readElement(expectedTag = 0x30)
    val sequenceReader = DerReader(sequence)
    return when (sequenceReader.peekTag()) {
        0x02 -> der
        0x30 -> {
            sequenceReader.readElement(expectedTag = 0x30)
            val bitString = sequenceReader.readElement(expectedTag = 0x03)
            require(bitString.isNotEmpty() && bitString[0].toInt() == 0) {
                "RSA SubjectPublicKeyInfo bit string is malformed."
            }
            bitString.copyOfRange(1, bitString.size)
        }
        else -> error("Unsupported RSA public key DER format.")
    }
}

private fun normalizeRsaPrivateKeyToPkcs1(der: ByteArray): ByteArray {
    val reader = DerReader(der)
    val sequence = reader.readElement(expectedTag = 0x30)
    val sequenceReader = DerReader(sequence)
    sequenceReader.readElement(expectedTag = 0x02)
    return when (sequenceReader.peekTag()) {
        0x02 -> der
        0x30 -> {
            sequenceReader.readElement(expectedTag = 0x30)
            sequenceReader.readElement(expectedTag = 0x04)
        }
        else -> error("Unsupported RSA private key DER format.")
    }
}

private class DerReader(
    private val bytes: ByteArray,
) {
    private var offset: Int = 0

    fun peekTag(): Int {
        require(offset < bytes.size) { "Unexpected end of DER value." }
        return bytes[offset].toInt() and 0xff
    }

    fun readElement(expectedTag: Int): ByteArray {
        val tag = readByte()
        require(tag == expectedTag) { "Unexpected DER tag 0x${tag.toString(16)}." }
        val length = readLength()
        require(offset + length <= bytes.size) { "DER value length exceeds input size." }
        return bytes.copyOfRange(offset, offset + length).also {
            offset += length
        }
    }

    private fun readLength(): Int {
        val first = readByte()
        if ((first and 0x80) == 0) return first

        val byteCount = first and 0x7f
        require(byteCount in 1..4) { "Unsupported DER length size." }
        var length = 0
        repeat(byteCount) {
            length = (length shl 8) or readByte()
        }
        return length
    }

    private fun readByte(): Int {
        require(offset < bytes.size) { "Unexpected end of DER value." }
        return bytes[offset++].toInt() and 0xff
    }
}

private fun ByteArray.dropLeadingZeroes(): ByteArray {
    val firstNonZero = indexOfFirst { it.toInt() != 0 }
    return if (firstNonZero == -1) byteArrayOf(0) else copyOfRange(firstNonZero, size)
}

private fun ByteArray.ensurePositiveDerInteger(): ByteArray {
    return if (isNotEmpty() && (this[0].toInt() and 0x80) != 0) byteArrayOf(0) + this else this
}

private fun ByteArray.toUnsignedBytes(): ByteArray = dropLeadingZeroes()

private fun ByteArray.toFixedSize(size: Int): ByteArray {
    require(this.size <= size) { "RSA integer does not fit target field size." }
    return if (this.size == size) this else ByteArray(size - this.size) + this
}

private fun ByteArray.toMemory(): Memory {
    val memory = Memory(size.toLong().coerceAtLeast(1L))
    if (isNotEmpty()) {
        memory.write(0, this, 0, size)
    }
    return memory
}

private fun MutableList<Byte>.addIntLe(value: Int) {
    add((value and 0xff).toByte())
    add(((value ushr 8) and 0xff).toByte())
    add(((value ushr 16) and 0xff).toByte())
    add(((value ushr 24) and 0xff).toByte())
}

private fun String.sanitizeAliasPrefix(): String {
    return map { char ->
        when {
            char.isLetterOrDigit() -> char
            char == '.' || char == '-' || char == '_' -> char
            else -> '_'
        }
    }.joinToString(separator = "")
}

private fun mutableDictionary(): CoreFoundation.CFMutableDictionaryRef {
    return MacOsSecurityConstants.coreFoundation.CFDictionaryCreateMutable(
        null,
        CoreFoundation.CFIndex(0),
        MacOsSecurityConstants.cfTypeDictionaryKeyCallbacks,
        MacOsSecurityConstants.cfTypeDictionaryValueCallbacks,
    ) ?: error("Unable to create CoreFoundation dictionary.")
}

private fun cfData(bytes: ByteArray): CoreFoundation.CFDataRef {
    val memory = bytes.toMemory()
    return MacOsSecurityConstants.coreFoundation.CFDataCreate(
        null,
        if (bytes.isEmpty()) Pointer.NULL else memory,
        CoreFoundation.CFIndex(bytes.size.toLong()),
    ) ?: error("Unable to create CoreFoundation data.")
}

private fun cfNumber(value: Int): CoreFoundation.CFNumberRef {
    return MacOsSecurityConstants.coreFoundation.CFNumberCreate(
        null,
        CoreFoundation.CFIndex(K_CF_NUMBER_SINT32_TYPE.toLong()),
        IntByReference(value),
    ) ?: error("Unable to create CoreFoundation number.")
}

private fun CoreFoundation.CFMutableDictionaryRef.setSharedValue(
    key: CoreFoundation.CFStringRef,
    value: CoreFoundation.CFTypeRef,
) {
    setValue(key, value)
}

private fun CoreFoundation.CFMutableDictionaryRef.setOwnedValue(
    key: CoreFoundation.CFStringRef,
    value: CoreFoundation.CFTypeRef,
) {
    setValue(key, value)
    value.release()
}

private const val K_CF_NUMBER_SINT32_TYPE = 3
