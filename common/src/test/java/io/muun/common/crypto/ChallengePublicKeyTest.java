package io.muun.common.crypto;

import io.muun.common.utils.Encodings;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChallengePublicKeyTest {

    private final byte[] serializedPublicKey = Encodings.hexToBytes("03959fef3d0a832fe494ac8ae91cec"
            + "0a39f26e9913ae3b111d55092848b37304be");

    @Test
    public void deserializeThenSerialize() throws Exception {

        final ChallengePublicKey passwordPublicKey =
                ChallengePublicKey.fromBytes(serializedPublicKey);

        assertThat(passwordPublicKey.toBytes())
                .isEqualTo(serializedPublicKey);
    }

    @Test
    public void verify() throws Exception {
        
        final ChallengePublicKey passwordPublicKey =
                ChallengePublicKey.fromBytes(serializedPublicKey);

        final byte[] data = "This is some data".getBytes();

        final byte[] signedData = Encodings.hexToBytes(
                "304402203ae9960d0986b824648fb4bc6d78249bedc4304b05063cc6d42a037b177cfcb"
                        + "d0220201b3cc5f8f829ae4a77d5fbb01b594e3ad2d4a3839194abd2c3f0e7cff90258");

        assertThat(passwordPublicKey.verify(data, signedData)).isTrue();
    }
}