package io.muun.apollo.data.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ReportedErrorsTest {

    @Test
    fun `should report an error the first time`() {
        // GIVEN an error nobody reported yet
        val reportedErrors = ReportedErrors()
        val error = RuntimeException("boom")

        // WHEN it's reported
        val actual = reportedErrors.markReported(error)

        // THEN it goes through
        assertThat(actual).isTrue()
    }

    @Test
    fun `should not report the same error instance twice`() {
        // GIVEN an error already reported, e.g. by an AsyncAction
        val reportedErrors = ReportedErrors()
        val error = RuntimeException("boom")
        reportedErrors.markReported(error)

        // WHEN it's reported again, e.g. by the presenter handling the action's state
        val actual = reportedErrors.markReported(error)

        // THEN it's skipped
        assertThat(actual).isFalse()
    }

    @Test
    fun `should report a different error with the same message`() {
        // GIVEN an error already reported
        val reportedErrors = ReportedErrors()
        reportedErrors.markReported(RuntimeException("boom"))

        // WHEN another failure of the same kind is reported
        val actual = reportedErrors.markReported(RuntimeException("boom"))

        // THEN it goes through, as it's a separate failure
        assertThat(actual).isTrue()
    }
}
