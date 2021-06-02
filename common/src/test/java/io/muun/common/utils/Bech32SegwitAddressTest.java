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

public class Bech32SegwitAddressTest {

    private static String[][] VALID_ADDRESS = {
            // test vectors provided in BIP
            new String[]{"bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3",
                    "00201863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"},
            new String[]{"tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx",
                    "0014751e76e8199196d454941c45d1b3a323f1433bd6"},
            new String[]{"BC1QW508D6QEJXTDG4Y5R3ZARVARY0C5XW7KV8F3T4",
                    "0014751e76e8199196d454941c45d1b3a323f1433bd6"},
            new String[]{"tb1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q0sl5k7",
                    "00201863143c14c5166804bd19203356da136c985678cd4d27a1b8c6329604903262"},
            new String[]{
                    "bc1pw508d6qejxtdg4y5r3zarvary0c5xw7kw508d6qejxtdg4y5r3zarvary0c5xw7kt5nd6y",
                    "5128751e76e8199196d454941c45d1b3a323f1433bd6751e76e8199196d454941c45d1b3a323f1"
                            + "433bd6"},
            new String[]{"BC1SW50QGDZ25J", "6002751e"},
            new String[]{"bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs",
                    "5210751e76e8199196d454941c45d1b3a323"},
            new String[]{"tb1qqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesrxh6hy",
                    "0020000000c4a5cad46221b2a187905e5266362b99d5e91c6ce24d165dab93e86433"},
            new String[]{"tb1pqqqqp399et2xygdj5xreqhjjvcmzhxw4aywxecjdzew6hylgvsesf3hn0c",
                    "5120000000c4a5cad46221b2a187905e5266362b99d5e91c6ce24d165dab93e86433"},
            new String[]{"bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0",
                    "512079be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"},
            // https://goo.gl/FwhgNV
            new String[]{"bc1qwqdg6squsna38e46795at95yu9atm8azzmyvckulcc7kytlcckxswvvzej",
                    "0020701a8d401c84fb13e6baf169d59684e17abd9fa216c8cc5b9fc63d622ff8c58d"},
            // Pay-to-Taproot (P2Tr)
            new String[]{"bcrt1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqc8gma6",
                    "512079be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"},
    };

    // test vectors
    private static String[] INVALID_ADDRESS = {
            "tc1qw508d6qejxtdg4y5r3zarvary0c5xw7kg3g4ty",       // Invalid human-readable part
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5",       // bad checksum
            // Invalid checksum (Bech32 instead of Bech32m)
            "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqh2y7hd",
            "tb1z0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqglt7rf",
            "BC1S0XLXVLHEMJA6C4DQV22UAPCTQUPFHLXM9H8Z3K2E72Q4K9HCZ7VQ54WELL",
            // Invalid checksum (Bech32m instead of Bech32)
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kemeawh",
            "tb1q0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vq24jc47",
            // Invalid character in checksum
            "bc1p38j9r5y49hruaue7wxjce0updqjuyyx0kh56v8s25huc6995vvpql3jow4",
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
    public void bech32DecodeValidAddresses() {
        for (String[] s : VALID_ADDRESS) {
            assertAddressIsValid(s[0], s[1]);
        }
    }

    @Test
    public void bech32DecodeInvalidAddresses() {
        for (String s : INVALID_ADDRESS) {
            assertAddressIsInvalid(s);
        }
    }

    private void assertAddressIsValid(String encodedAddress, String expectedScriptPubkey) {
        final Bech32.Decoded decoded = Bech32.decode(encodedAddress);

        verifyBech32Encode(encodedAddress, decoded);

        final NetworkParameters params = getNetworkParamsFromHeader(decoded.hrp);
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

    private void assertAddressIsInvalid(String encodedAddress) {
        try {
            final Bech32.Decoded decoded = Bech32.decode(encodedAddress);

            final NetworkParameters params = getNetworkParamsFromHeader(decoded.hrp);
            Bech32SegwitAddress.decode(params, encodedAddress);

            fail("expected address to fail validation");
        } catch (Exception e) {
            return;
        }
    }

    private void verifyBech32Encode(String encodedAddress, Bech32.Decoded decoded) {
        final String encoded;
        if (decoded.encoding == Bech32.Encoding.BECH32) {
            encoded = Bech32.encode(decoded.hrp, decoded.data);
        } else {
            encoded = Bech32.encodeBech32m(decoded.hrp, decoded.data);
        }

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
