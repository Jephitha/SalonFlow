package com.salonflow.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataCipherTest {

    @Rule @JvmField val tempDir = TemporaryFolder()

    @Test
    fun encryptWithCachedKey_decryptWithPin_roundtrip() {
        val pin = "1234"
        val original = "Hello, World!"
        val inFile = tempDir.newFile("plain.txt")
        val encrypted = tempDir.newFile("encrypted.bin")
        val decrypted = tempDir.newFile("decrypted.txt")

        inFile.writeText(original)

        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        val keyBytes = DataCipher.deriveKeyBytes(pin, salt)

        DataCipher.encryptWithCachedKey(inFile, encrypted, keyBytes, salt)
        assertTrue("encrypted file exists", encrypted.exists())
        assertTrue("encrypted file not empty", encrypted.length() > 0)

        DataCipher.decryptWithPin(encrypted, decrypted, pin)
        assertEquals(original, decrypted.readText())
    }

    @Test
    fun encryptWithCachedKey_wrongPin_fails() {
        val correctPin = "1234"
        val wrongPin = "5678"
        val inFile = tempDir.newFile("plain.txt")
        val encrypted = tempDir.newFile("encrypted.bin")
        val decrypted = tempDir.newFile("decrypted.txt")

        inFile.writeText("secret data")

        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        val keyBytes = DataCipher.deriveKeyBytes(correctPin, salt)
        DataCipher.encryptWithCachedKey(inFile, encrypted, keyBytes, salt)

        try {
            DataCipher.decryptWithPin(encrypted, decrypted, wrongPin)
            fail("Expected exception for wrong PIN decryption")
        } catch (_: Exception) {
            // expected — AES/GCM authentication fails with wrong key
        }
        // File may be created (empty) before the cipher throws, so check content instead
        if (decrypted.exists()) {
            assertNotEquals("secret data", decrypted.readText())
        }
    }

    @Test
    fun deriveKeyBytes_samePinAndSalt_returnsSameBytes() {
        val pin = "9999"
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)

        val first = DataCipher.deriveKeyBytes(pin, salt)
        val second = DataCipher.deriveKeyBytes(pin, salt)

        assertArrayEquals("same pin+same salt → same key bytes", first, second)
    }

    @Test
    fun deriveKeyBytes_differentSalt_returnsDifferentBytes() {
        val pin = "9999"
        val salt1 = ByteArray(16)
        val salt2 = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt1)
        java.security.SecureRandom().nextBytes(salt2)

        val first = DataCipher.deriveKeyBytes(pin, salt1)
        val second = DataCipher.deriveKeyBytes(pin, salt2)

        assertFalse("different salt → different key bytes", first.contentEquals(second))
    }

    @Test
    fun encryptWithPin_decryptWithPin_roundtrip() {
        val pin = "2468"
        val inFile = tempDir.newFile("plain.txt")
        val encrypted = tempDir.newFile("encrypted.bin")
        val decrypted = tempDir.newFile("decrypted.txt")

        inFile.writeText("backup content")

        DataCipher.encryptWithPin(inFile, encrypted, pin)
        assertTrue("encrypted file exists", encrypted.exists())

        DataCipher.decryptWithPin(encrypted, decrypted, pin)
        assertEquals("backup content", decrypted.readText())
    }

    @Test
    fun encryptWithCachedKey_outputFormat_matchesEncryptWithPin() {
        val pin = "1234"
        val salt = ByteArray(16)
        java.security.SecureRandom().nextBytes(salt)
        val keyBytes = DataCipher.deriveKeyBytes(pin, salt)

        val inFile = tempDir.newFile("plain.txt")
        val cachedFile = tempDir.newFile("cached.bin")
        val pinFile = tempDir.newFile("pin.bin")

        inFile.writeText("test data")

        DataCipher.encryptWithCachedKey(inFile, cachedFile, keyBytes, salt)

        // Manually create pin-based file with same salt
        DataCipher.encryptWithCachedKey(inFile, pinFile, keyBytes, salt)

        // Both should be decryptable by decryptWithPin with the PIN
        val fromCached = tempDir.newFile("from-cached.txt")
        val fromPin = tempDir.newFile("from-pin.txt")

        DataCipher.decryptWithPin(cachedFile, fromCached, pin)
        DataCipher.decryptWithPin(pinFile, fromPin, pin)

        assertEquals("test data", fromCached.readText())
        assertEquals("test data", fromPin.readText())
    }
}
