package io.muun.common.crypto.tx;

import io.muun.common.utils.Encodings;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.MainNetParams;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionHelpersTest {

    @Test
    public void getAddressFromP2sh_P2wshInput() throws Exception {

        final String txHex = "01000000000101d464e177b194710f5ce1883b2c043e2e3031f99684557cf3c582b42"
                + "a22df7d4f000000002322002036652851de3848c2b811c03a5ce4b37c29782d8b4f593b629487007"
                + "315efdf93ffffffff015ea50600000000001976a914b163217303a0f29b39ffe41561b26a1ec7223"
                + "9a588ac04004730440220672b07b7550b65bc91ab1f0c375cee7aa1993a1b063e60d6e52990ed954"
                + "b407602203aa7a3e9e2031e5534060495c884d47d63dc8edce11c9218b7c001ed10f72b110147304"
                + "40220369ec2abb6e379c8e3d1238dc5036ada8ea5a747bf1daa34c85f9911126f7b4402206797216"
                + "9a60b07ebc4cbd26eb70510867306cf3c932f088af2cb0613183ff50d01475221027dcbaf84e5b2b"
                + "eec0622fe05119c74353aef5bad7ee8aa6612c7cbaaab03f6f02103ab727e22faf25546cb270426a"
                + "a3fc57edb4fef4701f6290549f51cf62180c79952ae00000000";

        final byte[] txBytes = Encodings.hexToBytes(txHex);
        final MainNetParams params = MainNetParams.get();

        final Transaction transaction = new Transaction(params, txBytes);
        for (TransactionInput input : transaction.getInputs()) {
            final String addressFromInput = TransactionHelpers.getAddressFromInput(input, params);
            assertThat(addressFromInput).isEqualTo("34LSfcCbseJpnkt8Grav6tGLkrMRhMH9Mp");
            System.out.println(addressFromInput);
        }
    }

}