package io.muun.apollo.data.net.base

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Test
import org.junit.runner.RunWith
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Runs [DistrustedChainCheck.withPlatformTrust] against the device's real trust store, which unit
 * tests can't reach.
 */
@RunWith(AndroidJUnit4::class)
class DistrustedChainCheckPlatformTest {

    companion object {

        // EM DV TLS CA - G3B, issued by AAA Certificate Services (valid until 2028-12-31)
        // SHA-256 45A883F349AEDFD5F3A649B5498FFB121712BA8B448F3F2A91DBC5A766FCDDBE
        private const val EMUDHRA_G3B_BY_SECTIGO = """
            -----BEGIN CERTIFICATE-----
            MIIDzjCCAragAwIBAgIRAKAk/fS6Zlx4XOSgIEg4WLcwDQYJKoZIhvcNAQELBQAw
            ezELMAkGA1UEBhMCR0IxGzAZBgNVBAgMEkdyZWF0ZXIgTWFuY2hlc3RlcjEQMA4G
            A1UEBwwHU2FsZm9yZDEaMBgGA1UECgwRQ29tb2RvIENBIExpbWl0ZWQxITAfBgNV
            BAMMGEFBQSBDZXJ0aWZpY2F0ZSBTZXJ2aWNlczAeFw0yNTA1MTQwMDAwMDBaFw0y
            ODEyMzEyMzU5NTlaMD8xCzAJBgNVBAYTAk5MMRMwEQYDVQQKDAplTXVkaHJhIEJW
            MRswGQYDVQQDDBJFTSBEViBUTFMgQ0EgLSBHM0IwdjAQBgcqhkjOPQIBBgUrgQQA
            IgNiAATnntnASpdzxHNeUREdqFrCvV5kqzdLgOEsvCA47B5PDjuBL8mFnh5nr7BO
            L4vJf3SFumWqkR7opORbYtXMA9Vr5nizw4y03JN8okYOI4RTyJKTXSnUT5HhVga2
            PM7atGijggE1MIIBMTAfBgNVHSMEGDAWgBSgEQojPpbxB+zirynvgqV/0DCktDAd
            BgNVHQ4EFgQU4h4v5xwZOQ5VS2LBICRXNSSKJRQwDgYDVR0PAQH/BAQDAgEGMBIG
            A1UdEwEB/wQIMAYBAf8CAQAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUFBwMC
            MDEGA1UdIAQqMCgwCAYGZ4EMAQIBMA0GCysGAQQBg44hAQABMA0GCysGAQQBg44h
            AQJkMEMGA1UdHwQ8MDowOKA2oDSGMmh0dHA6Ly9jcmwuY29tb2RvY2EuY29tL0FB
            QUNlcnRpZmljYXRlU2VydmljZXMuY3JsMDQGCCsGAQUFBwEBBCgwJjAkBggrBgEF
            BQcwAYYYaHR0cDovL29jc3AuY29tb2RvY2EuY29tMA0GCSqGSIb3DQEBCwUAA4IB
            AQBzQ1IoqpdotM6gFrR4Q3YrKifOLeaj5XXunldpVE/vZn2TxC2y0s8oYiLR5ICE
            wqVXSB7N6BM1Mp+Nif7Jbhb2IswAfSR4Iik8+B0iRmi7lYoNlycCgHjmsjOmZaxM
            QkPmjXAJbG5f4V41OsvJZmS7lI3r0IuvEDVX3GAZ13on6IImSs9ybNpHQ7NCqkMQ
            7wH4uAO1dtpFycgJSdHaCOhHjrox2BTYGLdpUrYwY3EOAjlZ8UGgsxs7mG+xquMl
            bN1LFURwfnkVMP3T/DBIZ/ys2+lNFyxK3FlArrV4/000uTnVB1lO8oGORYdXoT5V
            GUyRCEIDhF5TBUuKF4E3kf1h
            -----END CERTIFICATE-----
            """

        private const val HOST = "pub.api.muun.io"
    }

    @Test
    fun shouldRejectACompromisedIntermediateTheDeviceTrustsThroughSectigo() {
        // GIVEN a server that sends an eMudhra intermediate cross-signed by a root Android trusts
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(EMUDHRA_G3B_BY_SECTIGO.trimIndent().byteInputStream())
        val chainCheck = DistrustedChainCheck.withPlatformTrust()

        // WHEN its chain is checked
        val error = catchThrowable { chainCheck.check(listOf(certificate), HOST) }

        // THEN it's rejected
        assertThat(error).isInstanceOf(SSLPeerUnverifiedException::class.java)
    }
}
