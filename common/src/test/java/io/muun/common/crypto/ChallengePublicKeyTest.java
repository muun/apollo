package io.muun.common.crypto;

import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.schemes.TransactionSchemeYpub;
import io.muun.common.utils.Encodings;

import com.google.common.primitives.Bytes;
import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
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

    @Test
    public void name() {

        final String rootPubkey = "ypub6Wo18mZJ6uAUGcccrq4QpdeApEahMwG26wi2AjYnbPREQ1Rigf6AtBFVJBu5"
                + "rXwtPqMZpi8K4FgGgMvadzHV7fBpqmAGC2kZQhYq23ALk8f";

        final String rootPath = "m/49'/0'/0'";

        final PublicKey publicKey = PublicKey.deserializeFromBase58(rootPath, rootPubkey);

        publicKey.networkParameters = MainNetParams.get();

        System.out.println(publicKey.serializeBase58());

        for (int i = 0; i < 10; i++) {
            System.out.println(i);
            System.out.println(TransactionSchemeYpub.createAddress(publicKey.deriveChild(0)
                    .deriveChild(i)));
        }
    }

    @Test
    public void electrumx() {

        final String rootPubkey = "ypub6Wo18mZJ6uAUGcccrq4QpdeApEahMwG26wi2AjYnbPREQ1Rigf6AtBFVJBu5"
                + "rXwtPqMZpi8K4FgGgMvadzHV7fBpqmAGC2kZQhYq23ALk8f";

        final String rootPath = "m/49'/0'/0'";

        final PublicKey publicKey = PublicKey.deserializeFromBase58(rootPath, rootPubkey);

        publicKey.networkParameters = MainNetParams.get();

        System.out.println(publicKey.serializeBase58());

        for (int i = 0; i < 10; i++) {
            System.out.println(i);

            final Address address = TransactionSchemeYpub
                    .createAddress(publicKey.deriveChild(0).deriveChild(i));

            final Script scriptPubKey = TransactionSchemeYpub
                    .createOutputScript(address);

            System.out.println(address);
            final String scriptHash = TransactionSchemeYpub.obtainScriptHash(scriptPubKey);

            System.out.println(scriptHash);
            System.out.println("\n ============================="
                    + "=================================== \n");
        }
    }

}