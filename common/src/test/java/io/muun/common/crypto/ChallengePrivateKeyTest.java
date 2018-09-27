package io.muun.common.crypto;

import io.muun.common.utils.Encodings;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChallengePrivateKeyTest {

    @Test
    public void sign() throws Exception {

        final String pw = "This is a password !";

        final byte[] salt = Encodings.hexToBytes("ff2a201264a597b1");

        final ChallengePrivateKey passwordPrivateKey = ChallengePrivateKey.fromUserInput(pw, salt);

        final byte[] data = "This is some data".getBytes();

        final byte[] signedData = Encodings.hexToBytes(
                "30450221009fa4672b2ce2011e545694571aba2fe586b01032633be771315053e13d9f6c"
                        + "e3022068e3ef81c69bf7509c4fbc61b1627203727eff49e74403df01e15d5a16d1bba5");

        assertThat(passwordPrivateKey.sign(data)).isEqualTo(signedData);
    }
}