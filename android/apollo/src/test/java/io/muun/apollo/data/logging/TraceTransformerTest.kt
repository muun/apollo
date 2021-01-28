package io.muun.apollo.data.logging

import io.muun.apollo.BaseTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TraceTransformerTest: BaseTest() {

    // NOTE: not a full test suite so far, only for previously failing cases.

    @Test
    fun `matches fully qualified IDs`() {
        val re = TraceExpr.FQ_ID.toRegex()

        assertThat(re.matches("foo.bar.baz")).isTrue()
        assertThat(re.matches("foo.Class.lambda\$method\$1")).isTrue()
        assertThat(re.matches("foo.-\$\$Lambda\$ClassWithStatics\$AbCd_eFgI.method")).isTrue()
    }

    @Test
    fun `matches line locations`() {
        val re = TraceExpr.LOCATION.toRegex()

        assertThat(re.matches("file:1")).isTrue()
        assertThat(re.matches("file.java:123")).isTrue()
        assertThat(re.matches("file.kt:1234")).isTrue()
        assertThat(re.matches("lambda")).isTrue()
    }

    @Test
    fun `parses trace lines`() {
        fun assertParses(line: String) =
            assertThat(TraceExpr.parse(line)).isInstanceOf(TraceLine::class.java)

        assertParses("at foo.Class.method(file:123)")
        assertParses("at foo.Class.lambda\$methodWithLambda\$1(file:123)")
        assertParses("at foo.bar.-\$\$Lambda\$SomeID.method(lambda)")
    }

    @Test
    fun `ignores original exception headers`() {
        assertThat(TraceExpr.parseTraceCause("Original exception:")).isNull()
    }
}