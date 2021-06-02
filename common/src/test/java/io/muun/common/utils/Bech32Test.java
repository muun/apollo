package io.muun.common.utils;

import io.muun.common.utils.internal.Bech32;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Bech32Test {

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
            "?1ezyfcl",
    };

    // test vectors
    private static String[] VALID_BECH32M = {
            "A1LQFN3A",
            "a1lqfn3a",
            "an83characterlonghumanreadablepartthatcontainsthetheexcludedcharactersbioandnumber11sg"
                    + "7hg6",
            "abcdef1l7aum6echk45nj3s0wdvt2fg8x9yrzpqzd3ryx",
            "11lllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllu"
                    + "dsr8",
            "split1checkupstagehandshakeupstreamerranterredcaperredlc445v",
            "?1v759aa"
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

    @Test
    public void bech32DecodeValid() {
        for (String originalString : VALID_BECH32) {
            final Bech32.Decoded decoded = Bech32.decode(originalString);
            verifyBech32Encode(originalString, decoded.hrp, decoded.data);
        }
    }

    @Test
    public void bech32mDecodeValid() {
        for (String originalString : VALID_BECH32M) {
            final Bech32.Decoded decoded = Bech32.decode(originalString);
            verifyBech32mEncode(originalString, decoded.hrp, decoded.data);
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

    private void verifyBech32Encode(String originalString, String hrp, byte[] dataPartBytes) {
        final String encoded = Bech32.encode(hrp, dataPartBytes);

        assertEquals(originalString.toLowerCase(), encoded.toLowerCase());
    }

    private void verifyBech32mEncode(String encodedAddress, String hrp, byte[] dataPartBytes) {
        final String encoded = Bech32.encodeBech32m(hrp, dataPartBytes);

        assertEquals(encodedAddress.toLowerCase(), encoded.toLowerCase());
    }

}
