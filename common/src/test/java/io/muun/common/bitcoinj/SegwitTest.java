package io.muun.common.bitcoinj;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.schemes.TransactionScheme;
import io.muun.common.crypto.tx.TransactionHelpers;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;


public class SegwitTest {

    private static NetworkParameters params = TestNet3Params.get();
    private static NetworkParameters mainNetParams = MainNetParams.get();

    @Test
    public void testSegwitDeserialiseTestNet() throws URISyntaxException, FileNotFoundException {

        // Raw HEX dump of a segwit transaction in Tesnet with 1 input P2SH-P2WPKH, 2 outputs
        final File file = resource("raw-transactions/tx-testnet-segwit.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(params, decode);

        final String txId = "0220e6c4b7baac30badcda8765e5eeb6eb26b2b34fdb815a67136c157ccc5fd8";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(1);
        assertThat(tx.getInput(0).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2SH-P2WPKH
        final String expectedRedeemScriptHash = "7629f34ea0a44840123d99f9b4634cefaac0d68f";
        assertThatIsValidP2sh_P2wpkh(tx, 0, expectedRedeemScriptHash);

        assertThat(tx.getOutputs().size()).isEqualTo(2);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(185908739962L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitDeserialiseMainNet() throws URISyntaxException, FileNotFoundException {

        // Raw HEX dump of a segwit transaction in MainNet with 1 input P2SH-P2WPKH, 1 output
        final File file = resource("raw-transactions/tx-mainnet-segwit.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "c586389e5e4b3acb9d6c8be1c19ae8ab2795397633176f5a6442a261bbdefc3a";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(1);
        assertThat(tx.getInput(0).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2SH-P2WPKH
        final String expectedRedeemScriptHash = "2928f43af18d2d60e8a843540d8086b305341339";
        assertThatIsValidP2sh_P2wpkh(tx, 0, expectedRedeemScriptHash);

        assertThat(tx.getOutputs().size()).isEqualTo(1);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(100000000L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitDeserialiseMainNetMultioutput() throws URISyntaxException,
            FileNotFoundException {

        // Raw HEX dump of a segwit transaction in MainNet with 1 input P2SH-P2WSH, 4 outputs
        final File file = resource("raw-transactions/tx-mainnet-segwit-multioutput.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "559922d1675df8ebf180060860ddf20e6f8f8399c29b712067d8e146ad090e6e";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(1);
        assertThat(tx.getInput(0).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2SH-P2WSH
        final String expectedRedeemScriptHash = "1988a27e3c2df4ddee7fad5a2303d086179b2a30";
        assertThatIsValidP2sh_P2wsh(tx, 0, expectedRedeemScriptHash);

        assertThat(tx.getOutputs().size()).isEqualTo(4);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(113324600L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitDeserialiseMainNetHybrid() throws FileNotFoundException,
            URISyntaxException {

        // Raw HEX dump of a segwit transaction in MainNet with 103 inputs, 4 outputs
        // Input 0 is not segwit
        // Input 1 is segwit, P2SH-P2WPKH
        // Input 2 is not segwit
        // Input 3 is segwit, P2SH-P2WPKH
        final File file = resource("raw-transactions/tx-mainnet-segwit-hybrid.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "d251fe8df11cd1616d6f00d07c05d219280b87b8ea5d2192d6005d60a26e0e6e";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(103);
        assertThat(tx.getInput(0).getWitness()).isEqualTo(TransactionWitness.EMPTY);
        assertThat(tx.getInput(1).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);
        assertThat(tx.getInput(2).getWitness()).isEqualTo(TransactionWitness.EMPTY);
        assertThat(tx.getInput(3).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2SH-P2WPKH
        String expectedRedeemScriptHash = "4e0c3a9011a4fb782ca17c72ce4ddf59b5b8bfb0";
        assertThatIsValidP2sh_P2wpkh(tx, 1, expectedRedeemScriptHash);

        expectedRedeemScriptHash = "121d7291585143d610180e5ac054bae110371798";
        assertThatIsValidP2sh_P2wpkh(tx, 3, expectedRedeemScriptHash);

        assertThat(tx.getOutputs().size()).isEqualTo(3);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(359283061L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testNonSegwitDeserialiseMainNetRegression() throws FileNotFoundException,
            URISyntaxException {

        // Raw HEX dump of a NON segwit transaction in MainNet with 1 input P2PKH, 32 outputs
        final File file = resource("raw-transactions/tx-mainnet-no-segwit-regression.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "4c8148367bbe67eee6a411723850e9c193df2447d323202456c470bb37a5c3fe";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(1);
        assertThat(tx.getInput(0).getWitness()).isEqualTo(TransactionWitness.EMPTY);

        // P2PKH
        final String expectedPubKeyHash = "43849383122ebb8a28268a89700c9f723663b5b8";
        final String addressBase58 = "17A16QmavnUfCW11DAApiJxp7ARnxN5pGX";
        assertThatIsValidP2pkh(tx, 0, expectedPubKeyHash, addressBase58);

        assertThat(tx.getOutputs().size()).isEqualTo(32);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(11330495917L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitDeserialiseNativeP2wpkh() throws FileNotFoundException,
            URISyntaxException {

        // Raw HEX dump of a segwit transaction in MainNet with 2 inputs, 2 outputs
        // Input 0 is not segwit
        // Input 1 is segwit, P2WPKH
        final File file = resource("raw-transactions/tx-mainnet-segwit-native-P2WPKH.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "e8151a2af31c368a35053ddd4bdb285a8595c769a3ad83e0fa02314a602d4609";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(2);
        assertThat(tx.getInput(0).getWitness()).isEqualTo(TransactionWitness.EMPTY);
        assertThat(tx.getInput(1).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2WPKH
        final String witnessProgram = "1d0f172a0ecb48aee1be1f2687d2963ae33f71a1";
        assertThatIsValidP2wpkh(tx, 1, witnessProgram);

        assertThat(tx.getOutputs().size()).isEqualTo(2);
        assertThat(tx.getOutput(0).getValue().getValue()).isEqualTo(112340000L);
        assertThat(tx.getOutput(1).getValue().getValue()).isEqualTo(223450000L);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(335790000L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitDeserialiseNativeP2wsh() throws FileNotFoundException,
            URISyntaxException {

        // Raw HEX dump of a segwit transaction in MainNet with 2 inputs, 2 outputs
        // Input 0 is not segwit
        // Input 1 is segwit, P2WSH
        final File file = resource("raw-transactions/tx-mainnet-segwit-native-P2WSH.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(mainNetParams, decode);

        final String txId = "e0b8142f587aaa322ca32abce469e90eda187f3851043cc4f2a0fff8c13fc84e";
        assertThat(tx.getHash().toString()).isEqualTo(txId);
        assertThat(tx.getInputs().size()).isEqualTo(2);
        assertThat(tx.getInput(0).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);
        assertThat(tx.getInput(1).getWitness()).isNotEqualTo(TransactionWitness.EMPTY);

        // P2WSH
        String witnessProgram = "ba468eea561b26301e4cf69fa34bde4ad60c81e70f059f045ca9a79931004a4d";
        assertThatIsValidP2wsh(tx, 0, witnessProgram);

        witnessProgram = "d9bbfbe56af7c4b7f960a70d7ea107156913d9e5a26b0a71429df5e097ca6537";
        assertThatIsValidP2wsh(tx, 1, witnessProgram);

        assertThat(tx.getOutputs().size()).isEqualTo(2);
        assertThat(tx.getOutput(0).getValue().getValue()).isEqualTo(10000000L);
        assertThat(tx.getOutput(1).getValue().getValue()).isEqualTo(10000000L);
        assertThat(tx.getOutputSum().getValue()).isEqualTo(20000000L);
        assertThat(TransactionHelpers.getHexRawTransaction(tx)).isEqualTo(hex);
    }

    @Test
    public void testSegwitCraftTestNet() {

        final PrivateKey userPrivateKey = PrivateKey.deserializeFromBase58("m",
                "tprv8ZgxMBicQKsPfJEGcJrRbkLucCPdu1S1jjTpYBSiccS7snrXkbFNRLdZ8rCaVyWbRz27ba8yUUJuko"
                        + "MYEyCwFFJXdfBArgSs5viEH3VjNU5");
        final PrivateKey muunPrivateKey = PrivateKey.deserializeFromBase58("m",
                "tprv8ZgxMBicQKsPfMYHj9ZVcM1yWN9qBeXJjfW2sqnG9m13iiNmmqNiopjP7FyDt3TgP1U2EeUeYGWbtQ"
                        + "rKZU3LQqSaTanPAXSCxCo7wrPpdwP");
        final PrivateKey swapperPrivateKey = PrivateKey.deserializeFromBase58("m",
                "tprv8ZgxMBicQKsPfMYHj9ZVcM1yWN9qBeXJjfW2sqnG9m13iiNmmqNiopjP7FyDt3TgP1U2EeUeYGWbtQ"
                        + "rKZU3LQqSaTanPAXSCxCo7wrPpdwP");

        final PublicKey userPublicKey = userPrivateKey.getPublicKey();
        final PublicKey muunPublicKey = muunPrivateKey.getPublicKey();
        final PublicKey swapperPublicKey = swapperPrivateKey.getPublicKey();

        final PublicKeyTriple sourceKey = new PublicKeyTriple(
                userPublicKey,
                muunPublicKey,
                swapperPublicKey
        );

        // Address: 2NE2m9DQZ7tk5UrqvQVAwoMigQaPHtX6eKm
        final Script sourceOutputScript = TransactionScheme.V3.createOutputScript(sourceKey);

        final Transaction tx = new Transaction(params);
        final long feeInSatoshis = 112000L;
        final String prevTxId = "8b6363465979ca3826eacf517d621ba85e1294075808d1738dba0371650311c9";
        final int outputIndex = 0;
        final long amount = 112438120L;

        final PublicKeyTriple destinationKey = sourceKey.deriveNextValidChild(0);

        final Script inputScript =
                TransactionScheme.V3.createInputScript(sourceKey, null, null, null);
        tx.addInput(Sha256Hash.wrap(prevTxId), outputIndex, inputScript);

        final Script outputScript = TransactionScheme.V3.createOutputScript(destinationKey);
        tx.addOutput(Coin.valueOf(amount - feeInSatoshis), outputScript);

        final byte[] hash = TransactionScheme.V3.createDataToSignInput(tx, 0, amount, sourceKey);

        final Signature userSignature = userPrivateKey.signTransactionHash(hash);
        final Signature muunSignature = muunPrivateKey.signTransactionHash(hash);

        final TransactionWitness witness = TransactionScheme.V3.createWitness(
                sourceKey,
                userSignature,
                muunSignature,
                null
        );
        tx.getInput(0).setWitness(witness);

        // End of Tx crafting

        tx.getInput(0).getScriptSig().correctlySpends(
                tx,
                0,
                witness,
                Coin.valueOf(amount),
                sourceOutputScript,
                Script.ALL_VERIFY_FLAGS
        );

        final String hexRawTransaction = TransactionHelpers.getHexRawTransaction(tx);
        final Transaction copyTx = new Transaction(params, tx.bitcoinSerialize());

        assertThat(TransactionHelpers.getHexRawTransaction(copyTx)).isEqualTo(hexRawTransaction);
    }

    @Test
    public void testTransactionDigestAndSignatureForP2sh_P2wsh() throws
            URISyntaxException,
            FileNotFoundException {

        final File file = resource("raw-transactions/bip143-P2SH-P2WSH-unsigned.txt");
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final Transaction transaction = new Transaction(params, Encodings.hexToBytes(hex));


        // Values taken from Bip 143 example for P2SH-P2WSH
        // See https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki#p2sh-p2wsh
        final String witnessScriptHex = "56210307b8ae49ac90a048e9b53357a2354b3334e9c8bee813ecb98e99"
                + "a7e07e8c3ba32103b28f0c28bfab54554ae8c658ac5c3e0ce6e79ad336331f78c428dd43eea8449b"
                + "21034b8113d703413d57761b8b9781957b8c0ac1dfe69f492580ca4195f50376ba4a21033400f6af"
                + "ecb833092a9a21cfdf1ed1376e58c5d1f47de74683123987e967a8f42103a6d48b1131e94ba04d97"
                + "37d61acdaa1322008af9602b3b14862c07a1789aac162102d8b661b0b3302ee2f162b09e07a55ad5"
                + "dfbe673a9f01d9f0c19617681024306b56ae";

        final int inputIndex = 0;
        final int valueInSatoshis = 987654321;
        final Transaction.SigHash sigHashType = Transaction.SigHash.ALL;

        final Sha256Hash sha256Hash = transaction.hashForWitnessSignature(
                inputIndex,
                Encodings.hexToBytes(witnessScriptHex),
                Coin.valueOf(valueInSatoshis),
                sigHashType,
                false
        );

        final String expected = "185c0be5263dce5b4bb50a047973c1b6272bfbd0103a89444597dc40b248ee7c";
        assertThat(sha256Hash.toString()).isEqualTo(expected);
    }

    @Test
    public void testWeightCalculation() throws URISyntaxException, FileNotFoundException {

        testWeightCalculation(
                "raw-transactions/tx-mainnet-segwit-multioutput-multiinput.txt",
                1344,
                336,
                666);

        testWeightCalculation(
                "raw-transactions/tx-mainnet-segwit-hybrid.txt",
                43567,
                10892,
                17197);

        testWeightCalculation(
                "raw-transactions/tx-mainnet-segwit-multioutput.txt",
                1135,
                284,
                475);

        testWeightCalculation(
                "raw-transactions/tx-mainnet-no-segwit-regression.txt",
                5064,
                1266,
                1266);
    }

    private void testWeightCalculation(String filename,
                                       int expectedWeight,
                                       int expectedVirtualSize,
                                       int expectedSize)
            throws URISyntaxException, FileNotFoundException {

        final File file = resource(filename);
        final Scanner in = new Scanner(new FileReader(file));
        final String hex = in.nextLine().toLowerCase();

        final byte[] decode = Encodings.hexToBytes(hex);

        final Transaction tx = new Transaction(params, decode);

        assertThat(tx.getWeight()).isEqualTo(expectedWeight);
        assertThat(tx.getVirtualTransactionSize()).isEqualTo(expectedVirtualSize);
        assertThat(tx.getMessageSize()).isEqualTo(expectedSize);
    }

    private void assertThatIsValidP2pkh(Transaction tx, int inputIndex,
                                        String expectedPubKeyHash,
                                        String addressBase58) {
        final List<ScriptChunk> chunks = tx.getInput(inputIndex).getScriptSig().getChunks();

        final byte[] pubKey = chunks.get(1).data;
        final byte[] pubKeyHash = Hashes.sha256Ripemd160(pubKey);
        final String pubKeyHashHex = Encodings.bytesToHex(pubKeyHash);
        assertThat(pubKeyHashHex).isEqualTo(expectedPubKeyHash);

        final Address address = LegacyAddress.fromPubKeyHash(mainNetParams, pubKeyHash);
        assertThat(address.toString()).isEqualTo(addressBase58);
    }

    private void assertThatIsValidP2sh_P2wpkh(Transaction tx, int inputIndex,
                                              String expectedRedeemScriptHash) {
        final TransactionInput input = tx.getInput(inputIndex);
        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();

        assertThat(isP2sh_P2wpkh(input.getScriptBytes())).isTrue();

        final byte[] pubkey = witness.getPush(witness.getPushCount() - 1);
        final String witnessProgram = getWitnessProgram(input.getScriptBytes());
        assertThat(Encodings.bytesToHex(Hashes.sha256Ripemd160(pubkey))).isEqualTo(witnessProgram);

        final byte[] redeemScriptBytes = getRedeemScriptBytes(input.getScriptBytes());
        final byte[] redeemScriptHash = Hashes.sha256Ripemd160(redeemScriptBytes);
        assertThat(Encodings.bytesToHex(redeemScriptHash)).isEqualTo(expectedRedeemScriptHash);
    }

    private void assertThatIsValidP2sh_P2wsh(Transaction tx, int inputIndex,
                                             String expectedRedeemScriptHash) {
        final TransactionInput input = tx.getInput(inputIndex);
        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();

        assertThat(isP2sh_P2wsh(input.getScriptBytes())).isTrue();

        final byte[] witnessScript = witness.getPush(witness.getPushCount() - 1);
        final String witnessProgram = getWitnessProgram(input.getScriptBytes());

        assertThat(Encodings.bytesToHex(Hashes.sha256(witnessScript))).isEqualTo(witnessProgram);

        final byte[] redeemScriptBytes = getRedeemScriptBytes(input.getScriptBytes());
        final byte[] redeemScriptHash = Hashes.sha256Ripemd160(redeemScriptBytes);
        assertThat(Encodings.bytesToHex(redeemScriptHash)).isEqualTo(expectedRedeemScriptHash);
    }

    private void assertThatIsValidP2wpkh(Transaction tx, int inputIndex, String witnessProgram) {
        final String scriptPubKey = "0014" + witnessProgram;
        assertThat(isP2wpkh(tx, inputIndex, scriptPubKey)).isTrue();

        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();
        final byte[] pubkey = witness.getPush(witness.getPushCount() - 1);
        assertThat(Encodings.bytesToHex(Hashes.sha256Ripemd160(pubkey))).isEqualTo(witnessProgram);
    }

    private void assertThatIsValidP2wsh(Transaction tx, int inputIndex, String witnessProgram) {
        final String scriptPubKey = "0020" + witnessProgram;
        assertThat(isP2wsh(tx, inputIndex, scriptPubKey)).isTrue();

        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();
        final byte[] witnessScript = witness.getPush(witness.getPushCount() - 1);
        assertThat(Encodings.bytesToHex(Hashes.sha256(witnessScript))).isEqualTo(witnessProgram);
    }

    private boolean isP2sh_P2wpkh(byte[] inputScript) {
        return inputScript.length == 23
                && (inputScript[0] & 0xff) == 0x16
                && (inputScript[1] & 0xff) == 0x00
                && (inputScript[2] & 0xff) == 0x14;
    }

    private boolean isP2sh_P2wsh(byte[] inputScript) {
        return inputScript.length == 35
                && (inputScript[0] & 0xff) == 0x22
                && (inputScript[1] & 0xff) == 0x00
                && (inputScript[2] & 0xff) == 0x20;
    }

    private boolean isP2wpkh(Transaction tx, int inputIndex, String scriptPubKeyHex) {
        final TransactionInput input = tx.getInput(inputIndex);
        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();
        final Script scriptPubKey = new Script(Encodings.hexToBytes(scriptPubKeyHex));

        return input.getScriptSig().getChunks().size() == 0 && witness.getPushCount() == 2
                && ScriptPattern.isP2WPKH(scriptPubKey);
    }

    private boolean isP2wsh(Transaction tx, int inputIndex, String scriptPubKeyHex) {
        final TransactionInput input = tx.getInput(inputIndex);
        final TransactionWitness witness = tx.getInput(inputIndex).getWitness();
        final Script scriptPubKey = new Script(Encodings.hexToBytes(scriptPubKeyHex));

        return input.getScriptSig().getChunks().size() == 0 && witness.getPushCount() >= 2
                && ScriptPattern.isP2WSH(scriptPubKey);
    }

    private byte[] getRedeemScriptBytes(byte[] inputScript) {
        return Encodings.hexToBytes(Encodings.bytesToHex(inputScript).substring(2));
    }

    private String getWitnessProgram(byte[] inputScript) {
        return Encodings.bytesToHex(inputScript).substring(6);
    }

    private File resource(String path) throws URISyntaxException {

        final URL resource = getClass().getClassLoader().getResource("io/muun/common/" + path);

        if (resource != null) {
            return new File(resource.toURI());
        }

        // Hack to load test resources when executing tests from Android Studio
        return new File(getClass().getClassLoader().getResource(".").getPath()
                .replace("/build/classes/test/", "/build/resources/test/io/muun/common/" + path));
    }
}
