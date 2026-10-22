package io.muun.apollo.data.net.base

import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import java.security.cert.Certificate

/**
 * CA keys that must never be trusted, even when a chain validates against the system trust store.
 *
 * These are the 8 eMudhra intermediate CA keys compromised in October 2026 (CVE-2026-111943).
 * Android still accepts their Sectigo "AAA Certificate Services" cross-signs, since it doesn't
 * check revocation. Same list Go blocks in crypto/x509 since 1.26.10 and 1.27.3 (CL 851047),
 * recomputed from emudhra.we-have-a-problem.com/impacted-intermediates.json.
 */
object DistrustedCertificates {

    // SHA-256 of each key's DER SubjectPublicKeyInfo, lowercase hex.
    private val SPKI_SHA256 = setOf(
        "ebed6475c2c2d80fcb199f3fe8dd6e6b3ce60b77eb15d4e42c3dec0c6f5b5105", // EM DV TLS CA - G3B
        "49dd353dc2b919a3770645117d0e60c16115e7d1bc259eefe734b00ea434fe5f", // EM DV TLS CA - G3C
        "e0254c7fcdc265e2f0f8a428dabb75ab4ae039b4872d62f5ee57f0877d5a8a41", // EM EV TLS CA - G3B
        "5c0660a2b164910d8ff589c18e14552e9e0ec237dcefe2cb736d41a4811f81a0", // EM EV TLS CA - G3C
        "43bffa19d6ef651451f6841e019931e96f6f2eaeff28ab4b6f1ce4576de2717b", // EM OV TLS CA - G3B
        "63ecefee431d49523bc1136c4e72d0206a477086caf606cd5cf025686c109cf8", // EM OV TLS CA - G3C
        "ec4ffa1ad0ac88081314b7aaffef6a91f97cd57291bb5ba686c5415f5eb4f6b6", // EM OV CS CA - G3B
        "92c063d67e8f14db71d288e11266ffdaf69cbf115f7bab14f3bc2f68d25e0e30", // EM OV CS CA - G3C
    )

    /**
     * Whether any certificate in the chain carries a distrusted key.
     */
    fun containsDistrustedKey(chain: List<Certificate>): Boolean {
        return chain.any { spkiSha256(it) in SPKI_SHA256 }
    }

    private fun spkiSha256(certificate: Certificate): String {
        // PublicKey.getEncoded() is the DER SubjectPublicKeyInfo for X.509 keys.
        return Encodings.bytesToHex(Hashes.sha256(certificate.publicKey.encoded))
    }
}
