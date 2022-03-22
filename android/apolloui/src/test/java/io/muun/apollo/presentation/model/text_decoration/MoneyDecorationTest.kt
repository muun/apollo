package io.muun.apollo.presentation.model.text_decoration

import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.Test
import java.util.*

/**
 * This test uses a DSL for expressing inputs and expected results.
 * - A pipe `|` represents the caret position.
 * - An underscore `_` represents the grouping separator for the current locale (aka it's a locale
 *  agnostic grouping separator).
 *  Why using an underscore? Because we want to automate inputs for different grouping separators
 *  without overlapping symbols or having to modify each test case if we happen to change or
 *  fix one.
 * - A dot `.` represents the decimal separator for the current locale (aka it's a locale agnostic
 *  decimal separator).
 */
// TODO handle PASTE correctly and add LOTS more tests for paste (e.g 123|.34 PASTE 567.34)
class MoneyDecorationTest {

    private val locales = listOf(Locale("es"), Locale("en"))

    @Test
    fun `should allow adding a digit on an empty input`() {
        locales.forEach {
            Given(it, "|")
                    .add("1")
                    .expect("1|")
        }
    }

    @Test
    fun `when the input is empty should allow adding a single zero on the left`() {
        locales.forEach {
            Given(it, "|")
                    .add("0")
                    .expect("0|")
        }
    }

    @Test
    fun `when adding a dot as first character should add a zero to the left too`() {
        locales.forEach {
            Given(it, "|")
                    .add(".")
                    .expect("0.|")
        }

    }

    @Test
    fun `should be able to add zeros after the decimal separator`() {
        locales.forEach {
            Given(it, "1.|")
                    .add("0")
                    .add("0")
                    .add("0")
                    .expect("1.000|")
        }
    }

    @Test
    fun `when adding more than 3 digits should show thousand separators`() {
        locales.forEach {
            Given(it, "123|")
                    .add("4567")
                    .expect("1_234_567|")
        }
    }

    @Test
    fun `when deleting a thousand separator should delete the character at its left`() {
        locales.forEach {
            Given(it, "12_|345")
                    .deleteOneCharacter()
                    .expect("1|_345")
        }
    }

    @Test
    fun `when adding more than maxFractionalDigits, string is truncated`() {

        locales.forEach {
            // Add allowed at the beginning:
            Given(it, "1.|23", 3).add("1").expect("1.1|23")

            // Add extra at the beginning:
            Given(it, "1.|234", 3).add("1").expect("1.1|23")

            // Add allowed in the middle:
            Given(it, "1.1|3", 3).add("2").expect("1.12|3")

            // Add extra in the middle:
            Given(it, "1.1|34", 3).add("2").expect("1.12|3")

            // Add allowed at the end:
            Given(it, "1.12|", 3).add("3").expect("1.123|")

            // Add extra at the end:
            Given(it, "1.123|", 3).add("3").expect("1.123|")
        }
    }

    @Test
    fun `test several interesting cases`() {
        locales.forEach {

            Given(it, "12|_345")
                    .add("9")
                    .expect("129_|345")

            Given(it, "12.|3456")
                    .deleteOneCharacter()
                    .expect("12|3_456")

            Given(it, "12_345.|678")
                    .paste(4, 8, "9")
                    .expect("123_9|78")

            Given(it, "12_345.|678")
                    .paste(4, 8, "99")
                    .expect("1_239_9|78")

            Given(it, "123_4|56_789")
                    .add(".")
                    .expect("1_234.|56789")
        }
    }

    @Test
    fun `test no decimals (aka SAT input)`() {
        locales.forEach {

            // Test manual input aka add
            Given(it, "1|", 0).add(".").expect("1|")

            Given(it, "1|23", 0).add(".").expect("1|23")

            Given(it, "|123", 0).add(".").expect("|123")

            // Test input initializes with some amount

            Given(it, "|", 0).add("0").expect("0|")

            Given(it, "0|", 0).add("0").expect("0|")

            Given(it, "|", 0).add("2000").expect("2_000|")

            // This is kindof a benevolent, simple paste
            Given(it, "44|", 0).add("123.123").expect("44_123|")

            Given(it, "4|4", 0).add("123.123").expect("41_23|4")

            Given(it, "|44", 0).add("123.123").expect("12_3|44")

            // TODO test paste
        }
    }

    /**
     * Provides a fluent API for generating text operation cases.
     */
    private class Given(locale: Locale,
                        initialValue: String,
                        maxFractionalDigits: Int = Int.MAX_VALUE) : DecorationHandler {

        private val decoration = MoneyDecoration(locale, this)
        private var text: StringBuilder = StringBuilder(cleanText(initialValue))

        private var caretPosition = initialValue.indexOf('|')

        init {
            decoration.setMaxFractionalDigits(maxFractionalDigits)

            if (caretPosition < 0) {
                throw RuntimeException("Caret position missing")
            }
        }

        fun deleteOneCharacter(): Given {
            caretPosition--
            decoration.beforeTextChanged(text, caretPosition, 1, 0)
            text.deleteCharAt(caretPosition)
            decoration.afterTextChanged(text)
            return this
        }

        fun paste(from: Int, to: Int, insertedDsl: String): Given {
            val inserted = cleanText(insertedDsl)
            val replacedCount = from - to
            decoration.beforeTextChanged(text, from, replacedCount, inserted.length)
            text.replace(from, to, inserted)
            caretPosition = inserted.length - replacedCount
            decoration.afterTextChanged(text)
            return this
        }

        fun add(insertedDsl: String): Given {
            val inserted = cleanText(insertedDsl)
            decoration.beforeTextChanged(text, caretPosition, 0, inserted.length)
            text.insert(caretPosition, inserted)
            caretPosition += inserted.length
            decoration.afterTextChanged(text)
            return this
        }

        fun expect(expected: String) {
            val expectedPosition = expected.indexOf('|')

            assertThat(text.toString()).isEqualTo(cleanText(expected))
            assertThat(caretPosition).isEqualTo(expectedPosition)
        }

        fun cleanText(dslText: String): String {
            return dslText.replace("|", "")
                    .replace("_", decoration.groupingSeparator + "")
                    .replace(".", decoration.decimalSeparator + "")
        }

        override fun setTextSize(unit: Int, size: Float) {
            // do nothing
        }

        override fun getSelectionStart(): Int {
            return this.caretPosition
        }

        override fun setSelection(pos: Int) {
            this.caretPosition = pos
        }

        override fun setText(text: CharSequence) {
            this.text = StringBuilder(text)
        }

        override fun length() = 0

    }
}
