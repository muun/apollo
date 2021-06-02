package io.muun.common.utils;

import io.muun.common.exception.AddressFormatException;
import io.muun.common.utils.internal.Bech32;

import com.google.common.primitives.Bytes;
import org.bitcoinj.core.NetworkParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Based on Java ref implementation in https://github.com/sipa/bech32/pull/19/files.
 * Add necessary changes to adapt to our codestyle.
 * Changed method signatures/interfaces.
 */
public class Bech32SegwitAddress {

    public static final String MAINNET_HEADER = "bc";
    public static final String TESTNET_HEADER = "tb";
    public static final String REGTEST_HEADER = "bcrt";

    /**
     * Decodes a Bech32 Segwit address into a its encoded witness program, along with its witness
     * version.
     *
     * @param params The network parameters that determine which network the address is from
     * @param addr a Segwit Bech32 address
     *
     * @return a Pair containing  encoded witness program, along with its witness version
     * @throws AddressFormatException if an error ocurred during decoding
     */
    public static Pair<Byte, byte[]> decode(NetworkParameters params, String addr) {

        final String hrp = getHeader(params);

        final Bech32.Decoded decoded;
        try {
            decoded = Bech32.decode(addr);
        } catch (IllegalArgumentException e) {
            throw new AddressFormatException(e.getMessage());
        }

        final String hrpgot = decoded.hrp;

        if (!hrp.equalsIgnoreCase(hrpgot)) {
            throw new AddressFormatException("mismatching bech32 human readeable part");
        }

        if (!hrpgot.equalsIgnoreCase("bc")
                && !hrpgot.equalsIgnoreCase("tb")
                && !hrpgot.equalsIgnoreCase("bcrt")) {
            throw new AddressFormatException("invalid segwit human readable part");
        }

        final byte[] data = decoded.data;
        final byte[] decodedData = convertBits(
                Arrays.copyOfRange(data, 1, data.length), 5, 8, false);

        if (decodedData.length < 2 || decodedData.length > 40) {
            throw new AddressFormatException("invalid decoded data length");
        }

        final byte witnessVersion = data[0];
        if (witnessVersion > 16) {
            throw new AddressFormatException("invalid decoded witness version");
        }
        if (witnessVersion > 0 && decoded.encoding == Bech32.Encoding.BECH32) {
            throw new AddressFormatException(
                    "witness version 1+ addresses must be encoded using bech32m");
        } else if (witnessVersion == 0 && decoded.encoding == Bech32.Encoding.BECH32M) {
            throw new AddressFormatException(
                    "witness version 0 addresses must be encoded using legacy bech32"
            );
        }

        if (witnessVersion == 0 && decodedData.length != 20 && decodedData.length != 32) {
            throw new AddressFormatException("decoded witness version 0 with unknown length");
        }

        return Pair.of(witnessVersion, decodedData);
    }

    /**
     * Encodes a human-readable part, a witness program and its witness version into a Bech32
     * Segwit address. Programs with witness version >0 will use the newer Bech32m standard defined
     * in BIP 350.
     *
     * @param params The network parameters that determine which network the address is from
     * @param witnessVersion the witness version of the program
     * @param witnessProgram the witness program
     *
     * @return a Bech32 Segwit address String
     * @throws AddressFormatException if an error ocurred
     */
    public static String encode(NetworkParameters params,
                                byte witnessVersion,
                                byte[] witnessProgram) {

        final byte[] prog = convertBits(witnessProgram, 8, 5, true);
        final byte[] data = new byte[1 + prog.length];

        System.arraycopy(new byte[]{witnessVersion}, 0, data, 0, 1);
        System.arraycopy(prog, 0, data, 1, prog.length);

        final String header = getHeader(params);
        if (witnessVersion == 0) {
            return Bech32.encode(header, data);
        }
        return Bech32.encodeBech32m(header, data);
    }

    /**
     * Extract output script (aka scriptPubKey) from a witness program.
     *
     * @param witver the witness version of the program
     * @param witprog the witness program
     *
     * @return an array byte representation of the output script
     */
    public static byte[] getScriptPubkey(byte witver, byte[] witprog) {
        final byte v = (witver > 0) ? (byte) (witver + 0x50) : (byte) 0;
        final byte[] ver = new byte[]{v, (byte) witprog.length};

        final byte[] ret = new byte[witprog.length + ver.length];
        System.arraycopy(ver, 0, ret, 0, ver.length);
        System.arraycopy(witprog, 0, ret, ver.length, witprog.length);

        return ret;
    }

    /**
     * Perform a conversion of the bytes in data:
     * - first, translate the values in each byte to fromBits, most significant bit first
     * - then, re-arrange those bits into groups of toBits bits.
     *
     * <p>For more information see decoding section of Bech32:
     * https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
     */
    private static byte[] convertBits(byte[] src, int fromBits, int toBits, boolean padWithZeros) {
        return convertBits(Bytes.asList(src), fromBits, toBits, padWithZeros);
    }

    /**
     * Perform a conversion of the bytes in data:
     * - first, translate the values in each byte to fromBits, most significant bit first
     * - then, re-arrange those bits into groups of toBits bits.
     *
     * <p>For more information see decoding section of Bech32:
     * https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
     */
    private static byte[] convertBits(List<Byte> data, int fromBits, int toBits, boolean pad) {

        int acc = 0;
        int bits = 0;
        final int maxv = (1 << toBits) - 1;
        final List<Byte> ret = new ArrayList<>();

        for (Byte value : data) {
            final short b = (short) (value & 0xff);

            if (b < 0) {
                throw new AddressFormatException();

            } else if ((b >> fromBits) > 0) {
                throw new AddressFormatException();
            }

            acc = (acc << fromBits) | b;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret.add((byte) ((acc >> bits) & maxv));
            }
        }

        if (pad && (bits > 0)) {
            ret.add((byte) ((acc << (toBits - bits)) & maxv));

        } else if (bits >= fromBits || (byte) (((acc << (toBits - bits)) & maxv)) != 0) {
            throw new AddressFormatException("panic");
        }

        return Bytes.toArray(ret);
    }

    private static String getHeader(NetworkParameters networkParameters) {
        if (NetworkParameters.ID_MAINNET.equals(networkParameters.getId())) {
            return MAINNET_HEADER;

        } else if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return REGTEST_HEADER;

        } else {
            return TESTNET_HEADER;
        }
    }
}