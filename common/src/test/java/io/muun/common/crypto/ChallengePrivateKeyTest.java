package io.muun.common.crypto;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ChallengePrivateKeyTest {

    private final NetworkParameters params = MainNetParams.get();
    private final Context bitcoinContext = new Context(params);

    {
        Context.propagate(Context.getOrCreate(params));
    }

    @Test
    public void sign() {

        final String pw = "This is a password !";
        final byte[] salt = Encodings.hexToBytes("ff2a201264a597b1");
        final int version = 1;

        final ChallengePrivateKey privateKey = ChallengePrivateKey.fromUserInput(pw, salt, version);

        final byte[] data = "This is some data".getBytes();

        final byte[] signedData = Encodings.hexToBytes(
                "30450221009fa4672b2ce2011e545694571aba2fe586b01032633be771315053e13d9f6c"
                        + "e3022068e3ef81c69bf7509c4fbc61b1627203727eff49e74403df01e15d5a16d1bba5");

        assertThat(privateKey.sign(data)).isEqualTo(signedData);
    }

    @Test
    public void decryption() {

        final String rc = "1wer2wer3wer4wer5wer6wer";
        final byte[] salt = Encodings.hexToBytes("ff2a201264a597b1");
        final int version = 1;
        final int birthday = 1234;

        final ChallengePrivateKey privateKey = ChallengePrivateKey.fromUserInput(rc, salt, version);
        final ChallengePublicKey publicKey = privateKey.getChallengePublicKey();

        final PrivateKey secretKey = PrivateKey.getNewRootPrivateKey(bitcoinContext);

        final String encryptedKey = publicKey.encryptPrivateKey(secretKey, birthday);
        final PrivateKey decryptedKey = privateKey.decryptPrivateKey(encryptedKey, params);

        assertThat(secretKey.serializeBase58()).isEqualTo(decryptedKey.serializeBase58());
        assertThat(MuunEncryptedPrivateKey.fromBase58(encryptedKey).birthday).isEqualTo(birthday);
    }
}