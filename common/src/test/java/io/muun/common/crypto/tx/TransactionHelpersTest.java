package io.muun.common.crypto.tx;

import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.schemes.TransactionScheme;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwapV2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionHelpersTest {

    private static final NetworkParameters NETWORK = MainNetParams.get();
    private static final Signature DUMMY_SIG = new Signature(new byte[72]);
    private static final Sha256Hash DUMMY_TXID = Sha256Hash.wrap(
            "e5027aebfb312b5e01f48f17b8f0ca40e6c932e77d479ccad40f491c1f996f9d"
    );

    @Before
    public void setUp() {
        Context.propagate(new Context(NETWORK));
    }

    @Test
    public void extractAddressesFromUserScripts() {

        final PublicKeyTriple inputKeyTriple = buildPublicKeyTriple();
        final PublicKeyTriple outputKeyTriple = buildPublicKeyTriple();

        for (TransactionScheme scheme : TransactionScheme.getAll()) {

            final MuunAddress inputAddress = scheme.createAddress(inputKeyTriple, NETWORK);
            final MuunAddress outputAddress = scheme.createAddress(outputKeyTriple, NETWORK);

            final Script inputScript =
                    scheme.createInputScript(inputKeyTriple, DUMMY_SIG, DUMMY_SIG, DUMMY_SIG);

            final Script outputScript = scheme.createOutputScript(outputAddress);

            final TransactionWitness witness = scheme
                    .createWitness(inputKeyTriple, DUMMY_SIG, DUMMY_SIG, DUMMY_SIG);

            final Transaction transaction = buildTransaction(inputScript, outputScript, witness);

            assertThat(TransactionHelpers.getAddressesFromTransaction(transaction))
                    .containsExactlyInAnyOrder(
                            inputAddress.getAddress(),
                            outputAddress.getAddress()
                    );
        }
    }

    @Test
    public void extractAddressesSwapV1() {

        final byte[] inputWitnessScript = TransactionSchemeSubmarineSwap.createWitnessScript(
                buildPublicKey().toAddress(),
                new byte[32],
                buildPublicKey().getPublicKeyBytes(),
                700_00
        );

        final byte[] outputWitnessScript = TransactionSchemeSubmarineSwap.createWitnessScript(
                buildPublicKey().toAddress(),
                new byte[32],
                buildPublicKey().getPublicKeyBytes(),
                700_00
        );

        final Address inputAddress = TransactionSchemeSubmarineSwap
                .createAddress(NETWORK, inputWitnessScript);

        final Address outputAddress = TransactionSchemeSubmarineSwap
                .createAddress(NETWORK, outputWitnessScript);

        final Script inputScript = TransactionSchemeSubmarineSwap
                .createInputScript(inputWitnessScript);

        final Script outputScript = TransactionSchemeSubmarineSwap
                .createOutputScript(outputWitnessScript);

        final Transaction transaction =
                buildTransaction(inputScript, outputScript, TransactionWitness.EMPTY);

        assertThat(TransactionHelpers.getAddressesFromTransaction(transaction))
                .containsExactlyInAnyOrder(
                        inputAddress.toString(),
                        outputAddress.toString()
                );
    }

    @Test
    public void extractAddressesSwapV2() {

        final byte[] inputWitnessScript = TransactionSchemeSubmarineSwapV2.createWitnessScript(
                new byte[32],
                buildPublicKey().getPublicKeyBytes(),
                buildPublicKey().getPublicKeyBytes(),
                buildPublicKey().getPublicKeyBytes(),
                250
        );

        final byte[] outputWitnessScript = TransactionSchemeSubmarineSwapV2.createWitnessScript(
                new byte[32],
                buildPublicKey().getPublicKeyBytes(),
                buildPublicKey().getPublicKeyBytes(),
                buildPublicKey().getPublicKeyBytes(),
                250
        );

        final Address inputAddress = TransactionSchemeSubmarineSwapV2
                .createAddress(NETWORK, inputWitnessScript);

        final Address outputAddress = TransactionSchemeSubmarineSwapV2
                .createAddress(NETWORK, outputWitnessScript);

        final Script inputScript = TransactionSchemeSubmarineSwapV2
                .createInputScript(inputWitnessScript);

        final Script outputScript = TransactionSchemeSubmarineSwapV2
                .createOutputScript(outputWitnessScript);

        final TransactionWitness witness = TransactionSchemeSubmarineSwapV2
                .createWitnessForCollaboration(DUMMY_SIG, DUMMY_SIG, inputWitnessScript);

        final Transaction transaction = buildTransaction(inputScript, outputScript, witness);

        assertThat(TransactionHelpers.getAddressesFromTransaction(transaction))
                .containsExactlyInAnyOrder(
                        inputAddress.toString(),
                        outputAddress.toString()
                );
    }

    private PublicKey buildPublicKey() {

        return PrivateKey.getNewRootPrivateKey(NETWORK).getPublicKey();
    }

    private PublicKeyTriple buildPublicKeyTriple() {

        return new PublicKeyTriple(buildPublicKey(), buildPublicKey(), buildPublicKey());
    }

    private Transaction buildTransaction(
            Script inputScript,
            Script outputScript,
            TransactionWitness witness) {

        final Transaction transaction = new Transaction(NETWORK);

        transaction.addInput(DUMMY_TXID, 0, inputScript);
        transaction.addOutput(Coin.COIN, outputScript);
        transaction.getInput(0).setWitness(witness);

        return transaction;
    }

    @Test
    public void getAddressFromP2ShOfP2WshInput() {

        final String txHex = "01000000000101d464e177b194710f5ce1883b2c043e2e3031f99684557cf3c582b42"
                + "a22df7d4f000000002322002036652851de3848c2b811c03a5ce4b37c29782d8b4f593b629487007"
                + "315efdf93ffffffff015ea50600000000001976a914b163217303a0f29b39ffe41561b26a1ec7223"
                + "9a588ac04004730440220672b07b7550b65bc91ab1f0c375cee7aa1993a1b063e60d6e52990ed954"
                + "b407602203aa7a3e9e2031e5534060495c884d47d63dc8edce11c9218b7c001ed10f72b110147304"
                + "40220369ec2abb6e379c8e3d1238dc5036ada8ea5a747bf1daa34c85f9911126f7b4402206797216"
                + "9a60b07ebc4cbd26eb70510867306cf3c932f088af2cb0613183ff50d01475221027dcbaf84e5b2b"
                + "eec0622fe05119c74353aef5bad7ee8aa6612c7cbaaab03f6f02103ab727e22faf25546cb270426a"
                + "a3fc57edb4fef4701f6290549f51cf62180c79952ae00000000";

        final Transaction transaction = TransactionHelpers.getTransactionFromHexRaw(NETWORK, txHex);

        for (TransactionInput input : transaction.getInputs()) {
            final String addressFromInput = TransactionHelpers.getAddressFromInput(input);
            assertThat(addressFromInput).isEqualTo("34LSfcCbseJpnkt8Grav6tGLkrMRhMH9Mp");
        }
    }

    @Test
    public void getAddressFromP2WpkhInput() {

        final String txHex = "02000000000101a2b6c6a6dc50e3e955ce1ed85d8991928d476d62ac4a333f7762c76"
                + "ea946b40b0700000000fdffffff02a5a2000000000000160014c6f7cbc56b2b4f4437e3abee3fa1c"
                + "34ac35a1b8292aa22000000000017a91420853643ab2b0e79d9bed92d44cdbf63923e4adc8702473"
                + "044022055c47237d307cbf0c8743f01dd827a5bf7cf1090534b7ec5d0572a01205b049e022023fa0"
                + "74cce20e06ef25df522d914fb782b8722c279f77e66d978de4c7361c579012102206c703d50c7a6d"
                + "0b2ea2c4769f43ddb98d42e961876e04be773f9b3565337123c610900";

        final Transaction transaction = TransactionHelpers.getTransactionFromHexRaw(NETWORK, txHex);

        for (TransactionInput input : transaction.getInputs()) {
            final String addressFromInput = TransactionHelpers.getAddressFromInput(input);
            assertThat(addressFromInput).isEqualTo("bc1q840c7k0ceak5a6h0s3cghdceepkf0fywee8p33");
        }
    }

    @Test
    public void getAddressFromP2WshInput() {

        final String txHex = "0100000000010190a523f69cbe5dfc441610862a5b444647e171267acd601dd2d0b28"
                + "403f651520000000000ffffffff029ef688000000000022002034100004b8225147db88fb2ddacd3"
                + "3da889ddaec762fc882adf469db26c5acea7330000000000000220020fbaae363df587f0f31cd7b1"
                + "041a947e0d4bb8ce88728438b6d4877ee6000366b04004830450221008ed20ff2e3b75a313911750"
                + "de2b9f85734e9a524d3782c08523e22e855ee9074022025347d637b54a29e2c65604280035c77840"
                + "9a9f21d66aa6406115c8c88be163a014830450221009ecc958ef94ba4040aaaf5c9ad164697f83d6"
                + "b089761cc2a4df66bfa4117c0af0220533b9894174441dc8db13905517342ef30c8751885bfd4a26"
                + "f60b648f5dc39ac014752210275cf3d29d339680a4ecc379cf35b9c46155d4d5765dd66e92143e4e"
                + "f784dd6382102d5fee614c1b314a8946291443584552ecc50e5ebd09e6bd4b2d334ee5137277d52a"
                + "e00000000";

        final Transaction transaction = TransactionHelpers.getTransactionFromHexRaw(NETWORK, txHex);

        for (TransactionInput input : transaction.getInputs()) {
            final String addressFromInput = TransactionHelpers.getAddressFromInput(input);
            assertThat(addressFromInput)
                    .isEqualTo("bc1qgxaegev7xwd4n6yqkmwxqmd5q2tuzd2q6ldlfp8u2eq2ffw5tdasacjczh");
        }
    }
}