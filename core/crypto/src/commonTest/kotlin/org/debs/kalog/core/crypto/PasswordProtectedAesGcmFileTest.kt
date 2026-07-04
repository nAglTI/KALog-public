package org.debs.kalog.core.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlinx.coroutines.runBlocking

class PasswordProtectedAesGcmFileTest {
    @Test
    fun decryptsWithSamePassword() {
        runBlocking {
            val plaintext = "backup-payload".encodeToByteArray()

            val encrypted = PasswordProtectedAesGcmFile.encrypt(
                plaintext = plaintext,
                password = "strong password",
            )

            assertContentEquals(
                expected = plaintext,
                actual = PasswordProtectedAesGcmFile.decrypt(
                    bytes = encrypted,
                    password = "strong password",
                ),
            )
        }
    }

    @Test
    fun rejectsWrongPassword() {
        runBlocking {
            val encrypted = PasswordProtectedAesGcmFile.encrypt(
                plaintext = "backup-payload".encodeToByteArray(),
                password = "strong password",
            )

            assertFails {
                PasswordProtectedAesGcmFile.decrypt(
                    bytes = encrypted,
                    password = "wrong password",
                )
            }
        }
    }
}
