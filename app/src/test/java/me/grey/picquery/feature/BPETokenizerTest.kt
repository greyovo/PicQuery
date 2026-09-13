package me.grey.picquery.feature

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BPETokenizerTest {
    @Test
    fun utf8BytesAreTreatedAsUnsignedValues() {
        val bytes = utf8ByteValues("米")

        assertEquals(listOf(231, 177, 179), bytes.toList())
    }

    @Test
    fun normalizesCaseAndAsciiWhitespace() {
        assertEquals("a photo of a dog", normalizeBpeText(" \tA\r\nphoto\u000Bof\u000C a  DOG "))
    }

    @Test
    fun preservesUnicodeWordsAndPunctuation() {
        assertEquals("米粉 café dog?", normalizeBpeText(" 米粉 CAFÉ Dog? "))
        assertEquals("e\u0301", normalizeBpeText("E\u0301"))
    }

    @Test
    fun keepsTheExistingAsciiWhitespacePatternInsideText() {
        assertEquals("a\u00A0b", normalizeBpeText("A\u00A0B"))
        assertEquals("a\u2003b", normalizeBpeText("A\u2003B"))
    }

    @Test
    fun lowercaseDoesNotDependOnDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("image id", normalizeBpeText(" IMAGE ID "))
            assertEquals("i\u0307", normalizeBpeText("\u0130"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
