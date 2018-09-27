package io.muun.common.crypto;

import io.muun.common.utils.Hashes;

import org.bitcoinj.core.ECKey;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.spongycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.IOException;

import javax.validation.constraints.NotNull;

public class ChallengePublicKey {

    private final ECKey key;

    private ChallengePublicKey(byte[] publicKey) {
        key = ECKey.fromPublicOnly(publicKey);
    }

    /**
     * Verify a signed data with the public key.
     */
    public boolean verify(byte[] data, byte[] signature) {
        final byte[] hash = Hashes.sha256(data);

        return key.verify(hash, signature);
    }

    /**
     * Serialize public key.
     */
    public byte[] toBytes() {
        return key.getPubKey();
    }

    /**
     * Deserialize a public key.
     */
    public static ChallengePublicKey fromBytes(byte[] publicKey) {
        return new ChallengePublicKey(publicKey);
    }

    /**
     * Transforms a compressed Q `ECPoint` into a `BCECPublicKey`.
     *
     * @return a challenge public key as a `BCECPublicKey`
     */
    @NotNull
    public BCECPublicKey getPublicKey() {
        try {

            final SubjectPublicKeyInfo
                    subjectPublicKeyInfo =
                    SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(
                            new ECPublicKeyParameters(
                                    ECKey.CURVE.getCurve().decodePoint(this.toBytes()),
                                    ECKey.CURVE
                            )
                    );

            return (BCECPublicKey) new KeyFactorySpi.ECDH().generatePublic(subjectPublicKeyInfo);

        } catch (IOException e) {
            throw new CryptographyException(e);
        }
    }

    /**
     * Calculates an IV from the Q point of this EC public key.
     *
     * @return an IV
     */
    public byte[] generateIv() {

        final byte[] pointBytes = this.getPublicKey().getQ().getEncoded(true);

        return ByteUtils.subArray(
                pointBytes,
                pointBytes.length - Cryptography.AES_BLOCK_SIZE,
                pointBytes.length
        );
    }
}
