package io.muun.common.crypto;

import io.muun.common.crypto.hd.PrivateKey;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrivateKeyTest {

    private static final NetworkParameters PARAMS = TestNet3Params.get();

    @Test
    public void testEqualsAfterDeserializingSerializedDerivedKey() {

        Context.propagate(new Context(PARAMS));

        final String hardenedDerivationPath = "m/schema:1'/recovery:1'";
        final String absoluteDerivationPath = hardenedDerivationPath + "/change:0/7";
        final PrivateKey rootPrivateKey = PrivateKey.getNewRootPrivateKey(PARAMS);

        final PrivateKey basePrivateKey = rootPrivateKey
                .deriveFromAbsolutePath(hardenedDerivationPath);

        final PrivateKey derivedPrivateKey = basePrivateKey
                .deriveFromAbsolutePath(absoluteDerivationPath);

        final String base58Serialization = derivedPrivateKey.serializeBase58();
        final PrivateKey privateKey = PrivateKey.deserializeFromBase58(
                absoluteDerivationPath,
                base58Serialization
        );

        assertThat(privateKey).isEqualTo(derivedPrivateKey);
    }
}