package io.muun.common.crypto;

import io.muun.common.api.PublicKeyJson;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class PublicKeyTest {

    private static final NetworkParameters PARAMS = MainNetParams.get();

    @Test
    public void testEqualsAfterDeserializingSerializedDerivedKey() {

        Context.propagate(new Context(PARAMS));

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

        Context.propagate(new Context(PARAMS));

        final String path = "m/schema:1'/recovery:1'";
        final String base58Serialization =
                "xpub661MyMwAqRbcF3YgLe8xTTTrDHf5bmEQuj5XfQP3bvwHqBpYvt99tcMSXXzroWJoQM4eMDNZNzNYZE"
                + "JfTqxq5S82J644buASmW4Y7VnwUeJ";

        final PublicKey basePublicKey = PublicKey.deserializeFromBase58(path, base58Serialization);

        final byte[] fingerprint = basePublicKey.getFingerprint();
        assertThat(fingerprint).isEqualTo(new byte[] {-49, -29, 7, 97});
    }
}