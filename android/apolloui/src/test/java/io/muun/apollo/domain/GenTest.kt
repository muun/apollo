package io.muun.apollo.domain

import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.common.bitcoinj.ValidationHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GenTest : BaseTest() {

    @Test
    fun `random generated email is valid`() {

        for (i in 1 until 10) {
            assertThat(ValidationHelpers.isValidEmail(Gen.email())).isTrue()
        }
    }
}