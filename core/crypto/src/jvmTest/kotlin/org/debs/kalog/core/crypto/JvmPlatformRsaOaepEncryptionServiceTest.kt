package org.debs.kalog.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class JvmPlatformRsaOaepEncryptionServiceTest {
    @Test
    fun decryptsExportedPrivateKeyReferenceForMigrationCompatibility() = runBlocking {
        val service = JvmPlatformRsaOaepEncryptionService()
        val keyPair = RsaOaepCryptoManager.generateKeyPair()
        val chunks = service.encryptToChunks("hello secure desktop", keyPair.publicKey)

        val decrypted = service.decryptFromChunks(chunks, keyPair.privateKeyRef)

        assertEquals("hello secure desktop", decrypted)
    }

    @Test
    fun decryptsGeneratedPlatformPrivateKeyReference() = runBlocking {
        val service = JvmPlatformRsaOaepEncryptionService(
            applicationId = "org.debs.kalog.test",
        )
        val keyPair = service.generateKeyPair()

        try {
            if (platformPrivateKeysEnabled()) {
                assertIs<PrivateKeyRef.PlatformAlias>(keyPair.privateKeyRef)
            } else {
                assertIs<PrivateKeyRef.Exported>(keyPair.privateKeyRef)
            }
            val chunks = service.encryptToChunks("hello platform key", keyPair.publicKey)

            assertEquals(
                expected = "hello platform key",
                actual = service.decryptFromChunks(chunks, keyPair.privateKeyRef),
            )
        } finally {
            service.deletePrivateKey(keyPair.privateKeyRef)
        }
    }

    @Test
    fun exportsGeneratedPlatformPrivateKeyReferenceForBackup() = runBlocking {
        val service = JvmPlatformRsaOaepEncryptionService(
            applicationId = "org.debs.kalog.test",
        )
        val keyPair = service.generateKeyPair()

        try {
            assertTrue(
                keyPair.privateKeyRef is PrivateKeyRef.PlatformAlias ||
                    keyPair.privateKeyRef is PrivateKeyRef.Exported,
            )
            val exportedPrivateKey = service.exportPrivateKey(keyPair.privateKeyRef)
            val chunks = service.encryptToChunks("backup portable key", keyPair.publicKey)

            assertEquals(
                expected = "backup portable key",
                actual = service.decryptFromChunks(chunks, exportedPrivateKey),
            )
        } finally {
            service.deletePrivateKey(keyPair.privateKeyRef)
        }
    }

    @Test
    fun importsExportedPrivateKeyReferenceIntoPlatformStorage() = runBlocking {
        val service = JvmPlatformRsaOaepEncryptionService(
            applicationId = "org.debs.kalog.test",
        )
        val keyPair = service.generateKeyPair()
        var importedPrivateKeyRef: PrivateKeyRef? = null

        try {
            val exportedPrivateKey = service.exportPrivateKey(keyPair.privateKeyRef)
            importedPrivateKeyRef = service.importPrivateKey(
                publicKey = keyPair.publicKey,
                privateKey = exportedPrivateKey,
            )
            if (platformPrivateKeysEnabled()) {
                assertIs<PrivateKeyRef.PlatformAlias>(importedPrivateKeyRef)
            } else {
                assertIs<PrivateKeyRef.Exported>(importedPrivateKeyRef)
            }

            val chunks = service.encryptToChunks("imported platform key", keyPair.publicKey)
            assertEquals(
                expected = "imported platform key",
                actual = service.decryptFromChunks(chunks, importedPrivateKeyRef),
            )
        } finally {
            service.deletePrivateKey(keyPair.privateKeyRef)
            importedPrivateKeyRef?.let { service.deletePrivateKey(it) }
        }
    }

    private fun platformPrivateKeysEnabled(): Boolean {
        return System.getProperty("os.name")
            .orEmpty()
            .contains("windows", ignoreCase = true)
    }
}
