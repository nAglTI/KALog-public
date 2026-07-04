package org.debs.kalog.core.crypto

actual fun createEncryptionService(): EncryptionService = RsaOaepEncryptionService()
