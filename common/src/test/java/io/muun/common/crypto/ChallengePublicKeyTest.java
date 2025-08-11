package io.muun.common.crypto;

import io.muun.common.utils.Encodings;

import com.google.common.primitives.Bytes;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChallengePublicKeyTest {

    private final byte[] serializedPublicKey = Encodings.hexToBytes("03959fef3d0a832fe494ac8ae91cec"
            + "0a39f26e9913ae3b111d55092848b37304be");

    private final byte[] salt = Encodings.hexToBytes("ff2a201264a597b1");

    private final int version = 1;

    @Test
    public void deserializeLegacy() {

        final byte[] legacySerialization = Bytes.concat(serializedPublicKey, salt);

        final ChallengePublicKey deserialized = ChallengePublicKey.deserializeLegacy(
                legacySerialization
        );

        assertThat(deserialized.toBytes()).isEqualTo(serializedPublicKey);
        assertThat(deserialized.getSalt()).isEqualTo(salt);
        assertThat(deserialized.getVersion()).isEqualTo(1);
    }

    @Test
    public void serializeThenDeserialize() {

        final ChallengePublicKey passwordPublicKey =
                new ChallengePublicKey(serializedPublicKey, salt, version);

        final byte[] serialization = passwordPublicKey.serialize();

        final ChallengePublicKey deserialized = ChallengePublicKey.deserialize(serialization);

        assertThat(deserialized.toBytes()).isEqualTo(serializedPublicKey);
        assertThat(deserialized.getSalt()).isEqualTo(salt);
        assertThat(deserialized.getVersion()).isEqualTo(version);
    }

    @Test
    public void verify() {

        final ChallengePublicKey passwordPublicKey =
                new ChallengePublicKey(serializedPublicKey, salt, version);

        final byte[] data = "This is some data".getBytes();

        final byte[] signedData = Encodings.hexToBytes(
                "304402203ae9960d0986b824648fb4bc6d78249bedc4304b05063cc6d42a037b177cfcb"
                        + "d0220201b3cc5f8f829ae4a77d5fbb01b594e3ad2d4a3839194abd2c3f0e7cff90258");

        assertThat(passwordPublicKey.verify(data, signedData)).isTrue();
    }

    /**
     * This test is in sync with challenge_keys_test.go#TestChecksum. The idea is to use the same
     * input data, so we can assert that a checksum generated in Java has the same value as a
     * checksum generated in go.
     */
    @Test
    public void getChecksum() {
        final ChallengePublicKey publicKey = new ChallengePublicKey(
                serializedPublicKey,
                salt,
                version
        );

        assertThat(publicKey.getChecksum()).isEqualTo("6d2c70f7530e96a6");
    }
}