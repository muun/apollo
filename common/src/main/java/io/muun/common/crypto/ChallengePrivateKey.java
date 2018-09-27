package io.muun.common.crypto;

import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.util.PrivateKeyInfoFactory;
import org.spongycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;

import javax.validation.constraints.NotNull;

public class ChallengePrivateKey {

    private final ECKey key;

    /**
     * Constructor.
     */
    private ChallengePrivateKey(ECKey key) {

        this.key = key;
    }

    /**
     * Sign a given data with the private key.
     */
    public byte[] sign(byte[] data) {
        final byte[] hash = Hashes.sha256(data);
        final ECKey.ECDSASignature signature = key.sign(Sha256Hash.wrap(hash));

        return signature.encodeToDER();
    }

    public ChallengePublicKey getChallengePublicKey() {
        return ChallengePublicKey.fromBytes(key.getPubKey());
    }

    /**
     * Generates an EC private key instance from a `BigInteger` private key.
     *
     * @return an EC private key.
     */
    @NotNull
    public PrivateKey getPrivateKey() {
        try {
            return new KeyFactorySpi.ECDH().generatePrivate(
                    PrivateKeyInfoFactory.createPrivateKeyInfo(
                            new ECPrivateKeyParameters(
                                    key.getPrivKey(),
                                    ECKey.CURVE
                            )
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs a password private key from a password.
     */
    public static ChallengePrivateKey fromUserInput(String text, byte[] salt) {

        final byte[] inputBytes = Encodings.stringToBytes(text);
        final byte[] inputSecret = Hashes.scrypt256(inputBytes, salt);

        final BigInteger point = new BigInteger(1, inputSecret).mod(ECKey.CURVE.getN());

        final ECKey ecKey = ECKey.fromPrivate(point);

        return new ChallengePrivateKey(ecKey);
    }
}
