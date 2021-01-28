package io.muun.common.utils;

import io.muun.common.utils.internal.Bech32;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Bech32SegwitTest {

    // test vectors
    private static String[] VALID_BECH32 = {
            "A12UEL5L",
            "a12uel5l",
            "an83characterlonghumanreadablepartthatcontainsthenumber1andtheexcludedcharactersbio1"
                    + "tt5tgs",
            "abcdef1qpzry9x8gf2tvdw0s3jn54khce6mua7lmqqqxw",
            "11qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
                    + "c8247j",
            "split1checkupstagehandshakeupstreamerranterredcaperred2y9e3w",
            "?1ezyfcl"
    };

    // test vectors
    private static String[] INVALID_BECH32 = {
            0x20 + "1nwldj5",                                       // HRP character out of range
            0x7F + "1axkwrx",                                       // HRP character out of range
            0x80 + "1eym55h",                                       // HRP character out of range
            "an84characterslonghumanreadablepartthatcontainsthe"
                    + "number1andtheexcludedcharactersbio1569pvx",  // Max length exceeded
            "pzry9x0s0muk",                                         // No separator character
            "1pzry9x0s0muk",                                        // Empty HRP
            "x1b4n0q5v",                                            // Invalid data character
            "li1dgmt3",                                             // Too short checksum/data part
            "de1lg7wt" + 0xFF,                                      // Invalid character in checksum
            "A1G7SGD8",                                             // checksum calculated with
                                                                    // uppercase form of HRP
            "10a06t8",                                              // Empty HRP
            "1qzzfhee"                                              // Empty HRP
    };

    private static String[][] VALID_ADDRESS = {
            // example provided in BIP
            new String[]{"bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
                    "00201863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"},
            new String[]{"tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
                    "0014751e76e8199196d454941c45d1b3a323f1433bd6"},

            // test vectors
            new String[]{"BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4",
                    "0014751e76e8199196d454941c45d1b3a323f1433bd6"},
            new String[]{"tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                    "00201863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"},
            new String[]{
                    "bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7k7grplx",
                    "5128751e76e8199196d454941c45d1b3a323f1433bd6751e76e8199196d454941c45d1b3a32"
                            + "3f1433bd6"},
            new String[]{"BC1SW50QA3JX3S", "6002751e"},
            new String[]{"bc1zw508d6qejxtdg4y5r3zarvaryvg6kdaj",
                    "5210751e76e8199196d454941c45d1b3a323"},
            new String[]{"tb1qqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesrxh6hy",
                    "0020000000c4a5cad46221b2a187905e5266362b99d5e91c6ce24d165dab93e86433"},
            // https://goo.gl/FwhgNV
            new String[]{"bc1qwqdg6squsna38e46795at95yu9atm8azzmyvckulcc7kytlcckxswvvzej",
                    "0020701a8d401c84fb13e6baf169d59684e17abd9fa216c8cc5b9fc63d622ff8c58d"},
    };

    // test vectors
    private static String[] INVALID_ADDRESS = {
            "tc1qw508d6qejxtdg4y5r3zarvary0c5xw7kg3g4ty",       // Invalid human-readable part
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5",       // bad checksum
            "BC13W508D6QEJXTDG4Y5R3ZARVARY0C5XW7KN40WF2",       // Invalid witness version
            "bc1rw5uspcuh",                                     // Invalid program length
            "bc10w508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg"
                    + "4y5r3zarvary0c5xw7kw5rljs90",            // Invalid program length
            "BC1QR508D6QEJXTDG4Y5R3ZARVARYV98GJ9P",             // Invalid program length for
                                                                // witness version 0 (per BIP141)
            "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefv"
                    + "pysxf3q0sL5k7",                          // Mixed case
            "bc1zw508d6qejxtdg4y5r3zarvaryvqyzf3du",            // Zero padding of more than 4 bits
            "tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefv"
                    + "pysxf3pjxtptv",                          // Non-zero padding in 8-to-5 conv
            "bc1gmk9yu",                                        // Empty data section
            "tb1pw508d6qejxtdg4y5r3zarqfsj6c3",
    };

    @Test
    public void bech32DecodeValid() {
        for (String originalString : VALID_BECH32) {

            final Pair<String, byte[]> decodedParts = Bech32.decode(originalString);
            final String hrp = decodedParts.fst;
            final byte[] dataPartBytes = decodedParts.snd;

            verifyBech32Encode(originalString, hrp, dataPartBytes);
        }
    }

    @Test
    public void bech32DecodeInvalid() {
        for (String s : INVALID_BECH32) {
            try {
                Bech32.decode(s);
                fail(s + " should not be a valid bech32 string");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void bech32DecodeValidAddresses() {
        for (String[] s : VALID_ADDRESS) {
            verifyAddress(s[0], s[1]);
        }
    }

    @Test
    public void bech32DecodeInvalidAddresses() {
        for (String s : INVALID_ADDRESS) {
            try {
                verifyAddress(null, s);
                fail(s + " should not be a valid address");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void verifyAddress(String encodedAddress, String expectedScriptPubkey) {
        final Pair<String, byte[]> decodedParts = Bech32.decode(encodedAddress);
        final String hrp = decodedParts.fst;
        final byte[] dataPartBytes = decodedParts.snd;

        verifyBech32Encode(encodedAddress, hrp, dataPartBytes);

        final NetworkParameters params = getNetworkParamsFromHeader(hrp);
        final byte witVer;
        final byte[] witProg;
        final Pair<Byte, byte[]> pair;

        try {
            pair = Bech32SegwitAddress.decode(params, encodedAddress);
            witVer = pair.fst;
            witProg = pair.snd;

        } catch (Exception e) {
            e.printStackTrace();
            fail();
            return;
        }

        final byte[] scriptPubkey = Bech32SegwitAddress.getScriptPubkey(witVer, witProg);
        assertEquals(expectedScriptPubkey, Hex.toHexString(scriptPubkey));

        final String address = Bech32SegwitAddress.encode(params, witVer, witProg);
        assertEquals(encodedAddress.toLowerCase(), address.toLowerCase());
    }

    private void verifyBech32Encode(String encodedAddress, String hrp, byte[] dataPartBytes) {
        final String encoded = Bech32.encode(hrp, dataPartBytes);

        assertEquals(encodedAddress.toLowerCase(), encoded.toLowerCase());
    }

    private NetworkParameters getNetworkParamsFromHeader(String hrp) {
        if (Bech32SegwitAddress.MAINNET_HEADER.equals(hrp)) {
            return MainNetParams.get();

        } else if (Bech32SegwitAddress.REGTEST_HEADER.equals(hrp)) {
            return RegTestParams.get();

        } else {
            return TestNet3Params.get();
        }
    }
}
