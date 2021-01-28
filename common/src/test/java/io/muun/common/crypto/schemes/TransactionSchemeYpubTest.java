package io.muun.common.crypto.schemes;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.TestUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

public class TransactionSchemeYpubTest {

    @Test
    public void name() {

        final TestNet3Params params = TestNet3Params.get();
        TestUtils.setBitcoinjContext(params);

        final PrivateKey rootPriv = PrivateKey.deserializeFromBase58(
                "m/49'/1'/0'",
                "tprv8gRrNu65W2Msef2BdBSUgFdRTGzC8EwVXnV7UGS3faeXtuMVt"
                        + "GfEdidVeGbThs4ELEoayCAzZQ4uUji9DUiAs7erdVskqju7hrBcDvDsdbY");

        final PrivateKey priv = rootPriv.deriveNextValidChild(0).deriveNextValidChild(0);
        final PublicKey pub = priv.getPublicKey();
        final Address address = TransactionSchemeYpub.createAddress(pub);
        System.out.println(address);

        final org.bitcoinj.core.Transaction prevTransaction =
                new org.bitcoinj.core.Transaction(params);

        final org.bitcoinj.core.Transaction transaction =
                new org.bitcoinj.core.Transaction(params);

        transaction.addInput(prevTransaction.getHash(), 0,
                TransactionSchemeYpub.createInputScript(pub));
        transaction.addOutput(Coin.COIN, address);

        final byte[] dataToSignInput = TransactionSchemeYpub.createDataToSignInput(transaction,
                0, Coin.COIN.value, pub);
        final Signature signature = priv.signTransactionHash(dataToSignInput);
        transaction.getInput(0).setWitness(TransactionSchemeYpub.createWitness(pub, signature));

        System.out.println(Encodings.bytesToHex(transaction.unsafeBitcoinSerialize()));
    }
}