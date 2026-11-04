package io.muun.common.crypto;

import io.muun.common.api.PublicKeyJson;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.hd.exception.InvalidSignatureException;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


public class PublicKeyTest {

    private static final NetworkParameters PARAMS = MainNetParams.get();

    @Before
    public void setUp() {
        Context.propagate(new Context(PARAMS));
    }

    @Test
    public void testEqualsAfterDeserializingSerializedDerivedKey() {

        final String hardenedPath = "m/schema:1'/recovery:1'";
        final String absolutePath = hardenedPath + "/change:0/7";
        final PrivateKey rootPrivateKey = PrivateKey.getNewRootPrivateKey(PARAMS);

        final PublicKey basePublicKey = rootPrivateKey
                .deriveFromAbsolutePath(hardenedPath)
                .getPublicKey();

        final PublicKey derivedPublicKey = basePublicKey.deriveFromAbsolutePath(absolutePath);

        final PublicKeyJson publicKeyJson = derivedPublicKey.toJson();
        final PublicKey publicKey = PublicKey.fromJson(publicKeyJson);

        assertThat(publicKey).isEqualTo(derivedPublicKey);
    }

    @Test
    public void testGetFingerprint() {

        final String path = "m/schema:1'/recovery:1'";
        final String base58Serialization =
                "xpub661MyMwAqRbcF3YgLe8xTTTrDHf5bmEQuj5XfQP3bvwHqBpYvt99tcMSXXzroWJoQM4eMDNZNzNYZE"
                + "JfTqxq5S82J644buASmW4Y7VnwUeJ";

        final PublicKey basePublicKey = PublicKey.deserializeFromBase58(path, base58Serialization);

        final byte[] fingerprint = basePublicKey.getFingerprint();
        assertThat(fingerprint).isEqualTo(new byte[] {-49, -29, 7, 97});
    }

    @Test
    public void testVerifyTransactionHashSignature() {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final Signature signature = privateKey.signTransactionHash(txHash);

        assertThatCode(() -> privateKey.getPublicKey()
                .verifyTransactionHashSignature(txHash, signature))
                .doesNotThrowAnyException();
    }

    @Test
    public void testVerifyTransactionHashSignatureRejectsAnotherKey() {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final PrivateKey anotherKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final Signature signature = anotherKey.signTransactionHash(txHash);

        assertThatExceptionOfType(InvalidSignatureException.class)
                .isThrownBy(() -> privateKey.getPublicKey()
                        .verifyTransactionHashSignature(txHash, signature))
                .withMessageContaining("does not match the transaction hash");
    }

    @Test
    public void testVerifyTransactionHashSignatureRejectsNonSighashAll() {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final byte[] sigBytes = privateKey.signTransactionHash(txHash).getBytes();
        sigBytes[sigBytes.length - 1] = (byte) 0x02; // SIGHASH_NONE
        final Signature tampered = new Signature(sigBytes);

        assertThatExceptionOfType(InvalidSignatureException.class)
                .isThrownBy(() -> privateKey.getPublicKey()
                        .verifyTransactionHashSignature(txHash, tampered))
                .withMessageContaining("SIGHASH_ALL");
    }

    @Test
    public void testVerifyTransactionHashSignatureRejectsAnyoneCanPay() {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final byte[] sigBytes = privateKey.signTransactionHash(txHash).getBytes();
        sigBytes[sigBytes.length - 1] = (byte) 0x81; // SIGHASH_ALL | SIGHASH_ANYONECANPAY
        final Signature anyoneCanPay = new Signature(sigBytes);

        assertThatExceptionOfType(InvalidSignatureException.class)
                .isThrownBy(() -> privateKey.getPublicKey()
                        .verifyTransactionHashSignature(txHash, anyoneCanPay))
                .withMessageContaining("SIGHASH_ALL");
    }

    @Test
    public void testVerifyTransactionHashSignatureRejectsMalformedDer() {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final byte[] sigBytes = privateKey.signTransactionHash(txHash).getBytes();
        sigBytes[0] = (byte) 0x31; // a DER sequence starts with 0x30
        final Signature malformed = new Signature(sigBytes);

        assertThatExceptionOfType(InvalidSignatureException.class)
                .isThrownBy(() -> privateKey.getPublicKey()
                        .verifyTransactionHashSignature(txHash, malformed))
                .withMessageContaining("canonically encoded");
    }

    @Test
    public void testVerifyTransactionHashSignatureRejectsHighS()
            throws SignatureDecodeException {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(PARAMS);
        final byte[] txHash = Hashes.sha256(new byte[] {1, 2, 3});
        final byte[] sigBytes = privateKey.signTransactionHash(txHash).getBytes();

        final TransactionSignature lowS =
                TransactionSignature.decodeFromBitcoin(sigBytes, true, true);

        // Replacing s with n - s, which ECDSA accepts just as well.
        final Signature highS = new Signature(
                new TransactionSignature(
                        lowS.r,
                        ECKey.CURVE.getN().subtract(lowS.s),
                        lowS.sighashFlags
                ).encodeToBitcoin()
        );

        assertThatExceptionOfType(InvalidSignatureException.class)
                .isThrownBy(() -> privateKey.getPublicKey()
                        .verifyTransactionHashSignature(txHash, highS))
                .withMessageContaining("canonically encoded");
    }
}
