package com.salonflow.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ColorsTest {
    @Test
    fun brand_isDarkGreen() {
        assertEquals(Color(0xFF274C43), Brand)
    }

    @Test
    fun accent_isRed() {
        assertEquals(Color(0xFFD45B43), Accent)
    }
}
