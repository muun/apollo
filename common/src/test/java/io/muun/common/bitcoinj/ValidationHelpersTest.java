package io.muun.common.bitcoinj;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationHelpersTest {

    private static NetworkParameters networkParameters = MainNetParams.get();

    @Test
    public void testIsValidExtendedPublicKey() throws Exception {

        final String key = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFj"
                + "qJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8";

        assertThat(ValidationHelpers.isValidBase58HdPublicKey(networkParameters, key))
                .isTrue();
    }

    @Test
    public void testIsValidDerivationPath() throws Exception {

        assertThat(ValidationHelpers.isValidDerivationPath("m/0H/1/2H/2/1000000000")).isTrue();
        assertThat(ValidationHelpers.isValidDerivationPath("m/0'/1/2'/2/1000000000")).isTrue();
        assertThat(ValidationHelpers.isValidDerivationPath("m")).isTrue();
        assertThat(ValidationHelpers.isValidDerivationPath("m/")).isFalse();
    }

    @Test
    public void testIsValidAddress() throws Exception {

        final String address1 = "1HB5XMLmzFVj8ALj6mfBsbifRoD4miY36v";
        final String address2 = "39hFnGpcBR66YPps7ACGU8VQACzY4Drzwk";

        assertThat(ValidationHelpers.isValidAddress(networkParameters, address1)).isTrue();
        assertThat(ValidationHelpers.isValidAddress(networkParameters, address2)).isTrue();
    }
}
