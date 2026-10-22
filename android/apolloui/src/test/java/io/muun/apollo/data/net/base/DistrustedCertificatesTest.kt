package io.muun.apollo.data.net.base

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.cert.Certificate

class DistrustedCertificatesTest {

    private val emudhraIntermediates = TestCertificates.emudhraIntermediates
    private val houstonChain = TestCertificates.houstonChain

    @Test
    fun `should flag each compromised eMudhra certificate`() {
        // GIVEN the 16 published certificates: 8 keys, each issued by emSign and by Sectigo
        val certificates = emudhraIntermediates

        // WHEN each one is checked on its own
        val unflagged = mutableListOf<String>()
        for (certificate in certificates) {
            if (!DistrustedCertificates.containsDistrustedKey(listOf(certificate))) {
                unflagged += certificate.subjectX500Principal.name
            }
        }

        // THEN all of them are flagged
        assertThat(certificates).hasSize(16)
        assertThat(unflagged).isEmpty()
    }

    @Test
    fun `should flag a chain with a compromised certificate anywhere in it`() {
        // GIVEN houston's chain with its intermediate swapped for a compromised one
        val chain = listOf(houstonChain[0], emudhraIntermediates[1], houstonChain[2])

        // WHEN it's checked
        val actual = DistrustedCertificates.containsDistrustedKey(chain)

        // THEN it's flagged
        assertThat(actual).isTrue()
    }

    @Test
    fun `should not flag houston's chain`() {
        // GIVEN houston's real chain
        val chain = houstonChain

        // WHEN it's checked
        val actual = DistrustedCertificates.containsDistrustedKey(chain)

        // THEN it's not flagged
        assertThat(actual).isFalse()
    }

    @Test
    fun `should not flag an empty chain`() {
        // GIVEN an empty chain
        val chain = emptyList<Certificate>()

        // WHEN it's checked
        val actual = DistrustedCertificates.containsDistrustedKey(chain)

        // THEN it's not flagged
        assertThat(actual).isFalse()
    }
}
