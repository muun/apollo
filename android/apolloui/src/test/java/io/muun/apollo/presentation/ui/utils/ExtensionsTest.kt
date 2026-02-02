package io.muun.apollo.presentation.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ExtensionsTest {

    @Test
    fun `normalizeDecimalInput - English locale spanish country keeps dot`() {
        val result = normalizeDecimalInput("1.2", Locale("en", "ES"))
        assertEquals("1.2", result)
    }

    @Test
    fun `normalizeDecimalInput - South africa locale south africa replaces dot to comma`() {
        val result = normalizeDecimalInput("1.2", Locale("af", "ZA"))
        assertEquals("1,2", result)
    }

    @Test
    fun `normalizeDecimalInput - Spanish locale spanish country replaces dot to comma`() {
        val result = normalizeDecimalInput("1.2", Locale("es", "ES"))
        assertEquals("1,2", result)
    }

    @Test
    fun `normalizeDecimalInput - English locale keeps dot`() {
        val result = normalizeDecimalInput("1.2", Locale.US)
        assertEquals("1.2", result)
    }

    @Test
    fun `normalizeDecimalInput - English locale keeps comma`() {
        val result = normalizeDecimalInput("1,2", Locale.US)
        assertEquals("1,2", result)
    }

    @Test
    fun `normalizeDecimalInput - handle input without decimal separator`() {
        val resultES = normalizeDecimalInput("123", Locale("es", "ES"))
        assertEquals("123", resultES)

        val resultUS = normalizeDecimalInput("123", Locale.US)
        assertEquals("123", resultUS)
    }

    @Test
    fun `normalizeDecimalInput - preserve leading zeros`() {
        val resultES = normalizeDecimalInput("0.5", Locale("es", "ES"))
        assertEquals("0,5", resultES)

        val resultUS = normalizeDecimalInput("0.5", Locale.US)
        assertEquals("0.5", resultUS)
    }

    @Test
    fun `normalizeDecimalInput - handle very small decimals`() {
        val result = normalizeDecimalInput("0.00000001", Locale("es", "ES"))
        assertEquals("0,00000001", result)
    }
}