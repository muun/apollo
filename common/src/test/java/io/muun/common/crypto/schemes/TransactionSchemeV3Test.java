package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.utils.TestUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
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
        TestUtils.setBitcoinjContext(params);

        final PrivateKey userRootPrivateKey = PrivateKey.getNewRootPrivateKey(params);
        final PrivateKey muunRootPrivateKey = PrivateKey.getNewRootPrivateKey(params);
        final PrivateKey swapperRootPrivateKey = PrivateKey.getNewRootPrivateKey(params);

        final PublicKeyTriple triple = new PublicKeyTriple(
                userRootPrivateKey.getPublicKey(),
                muunRootPrivateKey.getPublicKey(),
                swapperRootPrivateKey.getPublicKey()
        );

        // create a dummy previous transaction with a V2 (non-witness) output

        final Transaction prevTxV2 = new Transaction(params);

        prevTxV2.addOutput(Coin.COIN, TransactionScheme.V2.createOutputScript(triple));

        // create a dummy previous transaction with a V3 (non-native witness) output

        final Transaction prevTxV3 = new Transaction(params);

        prevTxV3.addOutput(Coin.COIN, TransactionScheme.V3.createOutputScript(triple));

        // create a transaction with two inputs, one V2 and one V3

        final Transaction tx = new Transaction(params);

        tx.addInput(prevTxV2.getOutput(0));
        tx.addInput(prevTxV3.getOutput(0));
        tx.addOutput(Coin.COIN, TransactionScheme.V3.createOutputScript(triple));

        // compute the message to sign for the first input before adding any witness data

        final byte[] dataToSignInput1BeforeWitness = TransactionScheme.V2
                .createDataToSignInput(tx, 0, tx.getInput(0).getValue().value, triple);

        // sign the second input and add the witness

        final byte[] dataToSignInput2 = TransactionScheme.V3
                .createDataToSignInput(tx, 1, Coin.COIN.value, triple);

        final Signature userSig = userRootPrivateKey.signTransactionHash(dataToSignInput2);
        final Signature muunSig = muunRootPrivateKey.signTransactionHash(dataToSignInput2);

        final TransactionInput input = tx.getInput(1);
        input.setScriptSig(TransactionScheme.V3.createInputScript(triple, null, null, null));
        input.setWitness(TransactionScheme.V3.createWitness(triple, userSig, muunSig, null));

        // recompute the message to sign for the first input after adding a witness

        final byte[] dataToSignInput1AfterWitness = TransactionScheme.V2
                .createDataToSignInput(tx, 0, tx.getInput(0).getValue().value, triple);

        assertThat(dataToSignInput1BeforeWitness).isEqualTo(dataToSignInput1AfterWitness);
    }
}