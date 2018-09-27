package io.muun.common.crypto.hd;

import io.muun.common.Optional;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KeyCrypterTest {

    private final NetworkParameters networkParameters = MainNetParams.get();
    private final Context bitcoinContext = new Context(networkParameters);

    private final KeyCrypter keyCrypter = new KeyCrypter();

    @Test
    public void testEncryption() throws Exception {

        final PrivateKey privateKey = PrivateKey.getNewRootPrivateKey(bitcoinContext);

        final String password = "asd";

        final String encrypted = keyCrypter.encrypt(privateKey, password);

        final Optional<PrivateKey> decryptedKey = keyCrypter.decrypt(encrypted, password);

        assertThat(decryptedKey.get()).isEqualTo(privateKey);

        final Optional<PrivateKey> nonDecrypted = keyCrypter.decrypt(encrypted, password + "asd");

        assertThat(nonDecrypted).isEqualTo(Optional.empty());
    }

    @Test
    public void testEncryptionWithAnotherPath() throws Exception {

        final PrivateKey rootPrivateKey = PrivateKey.getNewRootPrivateKey(bitcoinContext);

        final PrivateKey privateKey = rootPrivateKey.deriveFromAbsolutePath("m/asd:123");

        final String password = "asd";

        final String encrypted = keyCrypter.encrypt(privateKey, password);

        final Optional<PrivateKey> decryptedKey = keyCrypter.decrypt(encrypted, password);

        assertThat(decryptedKey.get()).isEqualTo(privateKey);
    }

}