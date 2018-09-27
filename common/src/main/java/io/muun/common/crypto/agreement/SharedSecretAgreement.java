package io.muun.common.crypto.agreement;

import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.CryptographyException;
import io.muun.common.utils.RandomGenerator;

import com.google.common.annotations.VisibleForTesting;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;

public class SharedSecretAgreement {
    private static final String CURVE_NAME = "secp256k1";
    private static final String AGREEMENT_ALGORITHM = "ECDH";
    private static final String ENCRYPTION_ALGORITHM = "AES";

    static {
        // Needed for supporting large key sizes.
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Generates a shared secret key from a remote public key and a local private key.
     *
     * @param remotePublicKey a `PublicKey` received from a remote source.
     * @param privateKey      a `PrivateKey` only known locally.
     * @return a `SecretKey` shared between the remote source and this receiver.
     */
    private static SecretKey generateSecret(PublicKey remotePublicKey, PrivateKey privateKey) {
        final KeyAgreement keyAgreement;

        try {
            keyAgreement = KeyAgreement.getInstance(
                    AGREEMENT_ALGORITHM,
                    BouncyCastleProvider.PROVIDER_NAME
            );

            keyAgreement.init(privateKey, RandomGenerator.getSecureRandom());
            keyAgreement.doPhase(remotePublicKey, true);

            return keyAgreement.generateSecret(ENCRYPTION_ALGORITHM);

        } catch (GeneralSecurityException e) {
            throw new CryptographyException(e);
        }
    }

    /**
     * Creates a random ephemeral `KeyPair` to be used for share secret agreement.
     *
     * @return a random ephemeral `KeyPair`.
     */
    @VisibleForTesting
    static KeyPair generateEphemeralKeypair() {

        final KeyPairGenerator kpg;

        try {

            kpg = KeyPairGenerator.getInstance(
                    AGREEMENT_ALGORITHM,
                    BouncyCastleProvider.PROVIDER_NAME
            );
            kpg.initialize(new ECGenParameterSpec(CURVE_NAME), RandomGenerator.getSecureRandom());

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        return kpg.generateKeyPair();
    }

    /**
     * Generates a shared secret container that can be used for AES encryption given two
     * remote public keys.
     *
     * @param pk1 remote public key.
     * @param pk2 remote public key.
     * @return a container with a pair (`SharedSecret`, `PublicKey`)
     */
    @NotNull
    public static SecretKeyContainer generateSecretAesKeyFromPublicKeys(
            ChallengePublicKey pk1,
            ChallengePublicKey pk2
    ) {

        final KeyPair ephemeralKeypair = SharedSecretAgreement.generateEphemeralKeypair();

        final SecretKey pk1Secret = SharedSecretAgreement.generateSecret(
                pk1.getPublicKey(),
                ephemeralKeypair.getPrivate()
        );

        final SecretKey pk2Secret = SharedSecretAgreement.generateSecret(
                pk2.getPublicKey(),
                ephemeralKeypair.getPrivate()
        );

        final byte[] aesKey = ByteUtils.xor(pk1Secret.getEncoded(), pk2Secret.getEncoded());

        final byte[] encodedQ = ((BCECPublicKey) ephemeralKeypair.getPublic())
                .getQ()
                .getEncoded(true);

        return new SecretKeyContainer(
                new SecretKeySpec(aesKey, 0, aesKey.length, ENCRYPTION_ALGORITHM),
                ChallengePublicKey.fromBytes(encodedQ)
        );
    }

    /**
     * Returns a shared secret that can be used for AES encryption given a remote public key and
     * two private keys.
     *
     * @param publicKey remote public key.
     * @param privateKey1 local challenge key.
     * @param privateKey2 local challenge key.
     * @return a shared secret key.
     */
    @NotNull
    public static SecretKey extractSecretFromRemotePublic(ChallengePublicKey publicKey,
                                                          ChallengePrivateKey privateKey1,
                                                          ChallengePrivateKey privateKey2
    ) {

        final SecretKey clientSecret1 = SharedSecretAgreement.generateSecret(
                publicKey.getPublicKey(),
                privateKey1.getPrivateKey()
        );

        final SecretKey clientSecret2 = SharedSecretAgreement.generateSecret(
                publicKey.getPublicKey(),
                privateKey2.getPrivateKey()
        );

        final byte[] aesDecryptKey = ByteUtils.xor(
                clientSecret1.getEncoded(),
                clientSecret2.getEncoded()
        );

        return new SecretKeySpec(
                aesDecryptKey,
                0,
                aesDecryptKey.length,
                ENCRYPTION_ALGORITHM
        );
    }

}
