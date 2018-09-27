package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionSchemeV3Test {

    @Test
    public void testNonSegwitSigningInThePresenceOfSegwitInputs() {

        // It turns out that the bitcoinj fork that we are using, during the signing algorithm for
        // non-witness inputs, uses the extended transaction serialization (ie. it includes the
        // witness data) instead of the basic transaction serialization. This is a mistake, since it
        // breaks retrocompatibility, they should have used the basic serialization.
        //
        // In order to check that we fixed this, we compute the message to sign for a non-segwit
        // input before and after adding a witness, and verify that they are equal.

        final MainNetParams params = MainNetParams.get();
        final PrivateKey userRootPrivateKey = PrivateKey.getNewRootPrivateKey(params);
        final PrivateKey muunRootPrivateKey = PrivateKey.getNewRootPrivateKey(params);

        final PublicKeyPair pair = new PublicKeyPair(
                userRootPrivateKey.getPublicKey(),
                muunRootPrivateKey.getPublicKey()
        );

        // create a dummy previous transaction with a V2 (non-witness) output

        final Transaction prevTxV2 = new Transaction(params);

        prevTxV2.addOutput(
                Coin.COIN,
                TransactionSchemeV2.createOutputScript(TransactionSchemeV2.createAddress(pair))
        );

        // create a dummy previous transaction with a V3 (non-native witness) output

        final Transaction prevTxV3 = new Transaction(params);

        prevTxV3.addOutput(
                Coin.COIN,
                TransactionSchemeV3.createOutputScript(TransactionSchemeV3.createAddress(pair))
        );

        // create a transaction with two inputs, one V2 and one V3

        final Transaction tx = new Transaction(params);

        tx.addInput(prevTxV2.getOutput(0));
        tx.addInput(prevTxV3.getOutput(0));
        tx.addOutput(
                Coin.COIN,
                TransactionSchemeV3.createOutputScript(TransactionSchemeV3.createAddress(pair))
        );

        // compute the message to sign for the first input before adding any witness data

        final byte[] dataToSignInput1BeforeWitness =
                TransactionSchemeV2.createDataToSignInput(tx, 0, pair);

        // sign the second input and add the witness

        final byte[] dataToSignInput2 =
                TransactionSchemeV3.createDataToSignInput(tx, 1, Coin.COIN.value, pair);

        final Signature signature2user = userRootPrivateKey.signTransactionHash(dataToSignInput2);
        final Signature signature2muun = muunRootPrivateKey.signTransactionHash(dataToSignInput2);

        tx.getInput(1).setScriptSig(TransactionSchemeV3.createInputScript(pair));
        tx.setWitness(1, TransactionSchemeV3.createWitness(pair, signature2user, signature2muun));

        // recompute the message to sign for the first input after adding a witness

        final byte[] dataToSignInput1AfterWitness =
                TransactionSchemeV2.createDataToSignInput(tx, 0, pair);

        assertThat(dataToSignInput1BeforeWitness).isEqualTo(dataToSignInput1AfterWitness);
    }
}