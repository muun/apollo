package io.muun.common.utils.internal;

import io.muun.common.utils.ByteArray;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static io.muun.common.utils.Preconditions.checkArgument;

/**
 * Based on Java ref implementation in https://github.com/sipa/bech32/pull/19/files.
 * Add necessary changes to adapt to our codestyle.
 */
public class Bech32 {

    private static final int BIP_0173_CHARACTER_LIMIT = 90;
    private static final int BECH32_DATA_MIN_LENGTH = 6;

    // Bech32 character set for encoding
    private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

    // Bech32 character set for decoding
    private static final byte[] INVERTED_CHARSET = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            15, -1, 10, 17, 21, 20, 26, 30,  7,  5, -1, -1, -1, -1, -1, -1,
            -1, 29, -1, 24, 13, 25,  9,  8, 23, -1, 18, 22, 31, 27, 19, -1,
            1 ,  0,  3, 16, 11, 28, 12, 14,  6,  4,  2, -1, -1, -1, -1, -1,
            -1, 29, -1, 24, 13, 25,  9,  8, 23, -1, 18, 22, 31, 27, 19, -1,
            1 ,  0,  3, 16, 11, 28, 12, 14,  6,  4,  2, -1, -1, -1, -1, -1
    };

    private static final String BECH32_SEPARATOR = "1";
    private static final int BECH32_CONST = 1;
    private static final int BECH32M_CONST = 0x2bc830a3;
    private static final int[] GEN = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};

    public enum Encoding {
        BECH32, BECH32M
    }

    public static class Decoded {
        public final Encoding encoding;
        public final String hrp;
        public final byte[] data;

        private Decoded(Encoding encoding, String hrp, byte[] data) {
            this.encoding = encoding;
            this.hrp = hrp;
            this.data = data;
        }
    }

    /**
     * Encode the human-readable part and data part into a bech32 encoded string.
     *
     * @param header the human-readable part
     * @param data the data part
     *
     * @return a bech32 encoded string.
     */
    public static String encode(String header, byte[] data) {
        return encode(header, data, BECH32_CONST);
    }

    private static String encode(String header, byte[] data, int checksumConst) {
        checkArgument(!header.isEmpty(), "Header shouldn't be empty");
        checkArgument(header.length() <= 83, "Header is too long");

        for (byte b : header.getBytes()) {
            checkArgument(b >= 33 && b <= 126, "Only printable US-ASCII allowed in header");
        }

        final byte[] checksum = createChecksum(header.getBytes(), data, checksumConst);
        final byte[] combined = ByteArray.concat(data, checksum);

        final StringBuilder builder = new StringBuilder(header.length() + 1 + combined.length);

        builder.append(header);
        builder.append(BECH32_SEPARATOR);

        for (byte digit : combined) {
            builder.append(CHARSET.charAt(digit));
        }

        return builder.toString();
    }

    /**
     * Encode the human-readable part and data part into a bech32 encoded string using the newer
     * bech32m variant defined in BIP 350.
     *
     * @param header the human-readable part
     * @param data the data part
     *
     * @return a bech32m encoded string.
     */
    public static String encodeBech32m(String header, byte[] data) {
        return encode(header, data, BECH32M_CONST);
    }

    /**
     * Decodes a bech32 encoded string into its human-readable part and data part.
     *
     * @param bech the bec32 encoded string
     *
     * @return an object containing the encoding, human-readable part and data part
     * @throws IllegalArgumentException if there's a decoding error
     */
    public static Decoded decode(String bech) {
        return decode(bech, BIP_0173_CHARACTER_LIMIT);
    }

    /**
     * Decodes a bech32 encoded string, with a custom maximum number of characters, into its
     * human-readable part and data part.
     * NOTE: BIP173 enforces a limitation of 90 characters, if extend the LIMIT parameter beyond
     * this, be aware that the effectiveness of checksum decreases as the length increases.
     * It is highly recommended NOT exceed 1023 characters, as the module could only
     * guarantee detecting 1 error.
     *
     * @param bech the bec32 encoded string
     * @param limit the maximum string length acceptable
     *
     * @return an object containing the encoding, human-readable part and data part
     * @throws IllegalArgumentException if there's a decoding error
     */
    public static Decoded decode(String bech, long limit) {
        if (!bech.equals(bech.toLowerCase()) && !bech.equals(bech.toUpperCase())) {
            throw new IllegalArgumentException("bech32 cannot mix upper and lower case");
        }

        checkArgument(bech.length() <= limit, "Input is too long");

        for (byte b : bech.getBytes()) {
            checkArgument(b >= 33 && b <= 126, "Only printable US-ASCII allowed");
        }

        bech = bech.toLowerCase();
        final int pos = bech.lastIndexOf(BECH32_SEPARATOR);
        if (pos < 1) {
            throw new IllegalArgumentException("bech32 missing separator");

        } else if (pos + BECH32_DATA_MIN_LENGTH + 1 > bech.length()) {
            throw new IllegalArgumentException("bech32 separator misplaced: data too short");

        } else if (bech.length() < 8) {
            throw new IllegalArgumentException("bech32 input too short");
        }

        final byte[] hrp = bech.substring(0, pos).getBytes();

        final ByteBuffer buffer = ByteBuffer.allocate(bech.length() - 1 - pos);

        for (int i = pos + 1; i < bech.length(); i++) {
            final byte b = INVERTED_CHARSET[bech.charAt(i)];
            checkArgument(b != -1, "Illegal character in input");
            buffer.put(b);
        }

        // Data with checksum
        final byte[] checkedData = buffer.array();
        final Encoding encoding = verifyChecksum(hrp, checkedData);
        checkArgument(encoding != null, "Invalid checksum");

        // Data without checksum
        final byte[] data = Arrays.copyOfRange(checkedData, 0, checkedData.length - 6);

        return new Decoded(encoding, new String(hrp), data);
    }

    private static int polymod(byte[] values) {
        int chk = 1;

        for (byte b : values) {
            final byte top = (byte) (chk >> 0x19);
            chk = b ^ ((chk & 0x1ffffff) << 5);
            for (int i = 0; i < 5; i++) {
                chk ^= ((top >> i) & 1) == 1 ? GEN[i] : 0;
            }
        }

        return chk;
    }

    private static byte[] hrpExpand(byte[] hrp) {
        final byte[] buf1 = new byte[hrp.length];
        final byte[] buf2 = new byte[hrp.length];
        final byte[] mid = new byte[1];

        for (int i = 0; i < hrp.length; i++) {
            buf1[i] = (byte) (hrp[i] >> 5);
        }

        mid[0] = 0x00;

        for (int i = 0; i < hrp.length; i++) {
            buf2[i] = (byte) (hrp[i] & 0x1f);
        }

        final byte[] ret = new byte[(hrp.length * 2) + 1];
        System.arraycopy(buf1, 0, ret, 0, buf1.length);
        System.arraycopy(mid, 0, ret, buf1.length, mid.length);
        System.arraycopy(buf2, 0, ret, buf1.length + mid.length, buf2.length);

        return ret;
    }

    private static Encoding verifyChecksum(byte[] hrp, byte[] data) {
        final byte[] exp = hrpExpand(hrp);

        final byte[] values = new byte[exp.length + data.length];
        System.arraycopy(exp, 0, values, 0, exp.length);
        System.arraycopy(data, 0, values, exp.length, data.length);

        final int check = polymod(values);
        if (check == BECH32_CONST) {
            return Encoding.BECH32;
        } else if (check == BECH32M_CONST) {
            return Encoding.BECH32M;
        }
        return null;
    }

    private static  byte[] createChecksum(byte[] hrp, byte[] data, int checksumConst) {
        final byte[] zeroes = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        final byte[] expanded = hrpExpand(hrp);
        final byte[] values = new byte[zeroes.length + expanded.length + data.length];

        System.arraycopy(expanded, 0, values, 0, expanded.length);
        System.arraycopy(data, 0, values, expanded.length, data.length);
        System.arraycopy(zeroes, 0, values, expanded.length + data.length, zeroes.length);

        final int polymod = polymod(values) ^ checksumConst;
        final byte[] ret = new byte[6];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (byte) ((polymod >> 5 * (5 - i)) & 0x1f);
        }

        return ret;
    }
}
