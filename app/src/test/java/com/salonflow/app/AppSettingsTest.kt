package com.salonflow.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val settings = AppSettings(context)

    @Test
    fun cacheDriveKey_storesKeyAndSalt() {
        settings.setPin("1234")
        settings.cacheDriveKey("1234")

        assertTrue("hasDriveKey after cache", settings.hasDriveKey())
        assertNotNull("key bytes not null", settings.getDriveKeyBytes())
        assertNotNull("key salt not null", settings.getDriveKeySalt())
        assertEquals("key bytes length is 32 (256 bits)", 32, settings.getDriveKeyBytes()!!.size)
        assertEquals("key salt length is 16", 16, settings.getDriveKeySalt()!!.size)
    }

    @Test
    fun hasDriveKey_returnsFalseBeforeCache() {
        settings.setPin("1234")
        assertFalse("no drive key before caching", settings.hasDriveKey())
    }

    @Test
    fun hasDriveKey_returnsFalseAfterClearPin() {
        settings.setPin("1234")
        settings.cacheDriveKey("1234")
        settings.clearPin()

        assertFalse("drive key cleared after clearPin", settings.hasDriveKey())
        assertNull("key bytes null after clearPin", settings.getDriveKeyBytes())
        assertNull("key salt null after clearPin", settings.getDriveKeySalt())
    }

    @Test
    fun setPin_clearsDriveKey() {
        settings.setPin("1234")
        settings.cacheDriveKey("1234")
        assertTrue("key cached", settings.hasDriveKey())

        settings.setPin("5678")

        assertFalse("drive key cleared after PIN change", settings.hasDriveKey())
        assertNull("key bytes null after PIN change", settings.getDriveKeyBytes())
        assertNull("key salt null after PIN change", settings.getDriveKeySalt())
        assertTrue("new PIN is set", settings.checkPin("5678"))
    }

    @Test
    fun cacheDriveKey_withDifferentPin_overwrites() {
        settings.setPin("1111")
        settings.cacheDriveKey("1111")
        val firstKey = settings.getDriveKeyBytes()
        val firstSalt = settings.getDriveKeySalt()

        settings.cacheDriveKey("1111")
        val secondKey = settings.getDriveKeyBytes()
        val secondSalt = settings.getDriveKeySalt()

        // Each cacheDriveKey generates a new random salt → different derived key
        assertFalse("different salt on re-cache", firstSalt.contentEquals(secondSalt))
        assertFalse("different key on re-cache", firstKey.contentEquals(secondKey))
    }

    @Test
    fun getLastDriveBackupTime_roundtrip() {
        assertEquals("default is 0", 0L, settings.getLastDriveBackupTime())
        settings.setLastDriveBackupTime(123456789L)
        assertEquals(123456789L, settings.getLastDriveBackupTime())
    }

    @Test
    fun setPin_empty_clearsDriveKey() {
        settings.setPin("1234")
        settings.cacheDriveKey("1234")
        assertTrue("key cached", settings.hasDriveKey())

        settings.setPin("")
        assertFalse("drive key cleared after empty PIN", settings.hasDriveKey())
    }

    @Test
    fun cacheDriveKey_restoresCorrectly() {
        settings.setPin("9999")
        settings.cacheDriveKey("9999")

        val savedKey = settings.getDriveKeyBytes()
        val savedSalt = settings.getDriveKeySalt()

        // Simulate app restart by clearing and re-caching from the raw PIN
        settings.clearPin()
        settings.setPin("9999")
        val rederived = DataCipher.deriveKeyBytes("9999", savedSalt!!)

        assertArrayEquals("re-derived key matches cached key", savedKey, rederived)
    }
}
