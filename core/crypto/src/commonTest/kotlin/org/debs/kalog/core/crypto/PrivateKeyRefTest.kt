package org.debs.kalog.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PrivateKeyRefTest {
    @Test
    fun rejectsRawPrivateKeyValuesWithoutReferencePrefix() {
        assertFailsWith<IllegalStateException> {
            PrivateKeyRef.deserialize("legacy-private-key")
        }
    }

    @Test
    fun roundTripsExportedKeyReference() {
        val ref = PrivateKeyRef.Exported("private-key")

        assertEquals(ref, PrivateKeyRef.deserialize(ref.serialize()))
    }

    @Test
    fun roundTripsPlatformAliasReference() {
        val ref = PrivateKeyRef.PlatformAlias(
            provider = "windows-cng",
            alias = "kalog.chat.123",
        )

        assertEquals(ref, PrivateKeyRef.deserialize(ref.serialize()))
        assertNull(ref.exportedValueOrNull())
    }
}
