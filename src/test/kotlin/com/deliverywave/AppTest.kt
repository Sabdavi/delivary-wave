package com.deliverywave

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppTest {
    @Test
    fun `greeting returns expected text`() {
        assertEquals("Hello, Kotlin!", greeting("Kotlin"))
    }
}

