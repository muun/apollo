package io.muun.common.bitcoinj;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class BlockHelpers {

    private static final byte MOST_SIGNIFICANT_BIT_MASK = (byte) 0x80;

    /**
     * Gets the data from the coinbase transaction's first input. See BIP-34 for more info.
     */
    public static int getBlockHeightFromCoinbase(Block block) {

        Preconditions.checkState(block.getVersion() != 1,
                "Version 1 blocks don't have their height");

        final List<Transaction> transactions = block.getTransactions();

        Preconditions.checkNotNull(transactions);

        final Transaction coinbaseTransaction = transactions.get(0);

        final TransactionInput firstInput = coinbaseTransaction.getInputs().get(0);

        final byte[] sigScriptData = firstInput.getScriptBytes();

        Preconditions.checkNotNull(sigScriptData);

        // We extract the data by hand, because bitcoinj merges consecutive PUSHes.

        final byte numberOfBytes = sigScriptData[0];

        Preconditions.checkArgument(sigScriptData.length >= numberOfBytes + 1,
                "Not enough bytes in coinbase's block height");

        Preconditions.checkArgument(numberOfBytes <= 4, "Height must be at most 4 bytes");

        // We copy create a 4-bytes byte[] and copy the height's pushed bytes to the beginning of
        // it. This way, if they are less than 4, the most significant bytes will be 0, as we are
        // working in little endian.

        final byte[] heightBytes = new byte[]{0, 0, 0, 0};

        System.arraycopy(sigScriptData, 1, heightBytes, 0, numberOfBytes);

        // We move the sign bit to the last byte if needed

        if (numberOfBytes < 4) {

            // First copy it to the last byte
            heightBytes[heightBytes.length - 1] = (byte) (heightBytes[numberOfBytes - 1]
                    & MOST_SIGNIFICANT_BIT_MASK);

            // Remove it from the previous byte
            heightBytes[numberOfBytes - 1] = (byte) (heightBytes[numberOfBytes - 1]
                    & ~MOST_SIGNIFICANT_BIT_MASK);
        }

        return parseSignMagnitudeLittleEndianInt32(heightBytes);
    }

    private static int parseSignMagnitudeLittleEndianInt32(byte[] numberBytes) {

        Preconditions.checkNotNull(numberBytes);
        Preconditions.checkArgument(numberBytes.length == 4);

        // The sign is stored in the MSB of its last byte, we save its value before parsing the
        // number.
        //
        // Note that a positive number's sign-magnitude representation matches with its
        // two-complement one (both with the same endianness). As we have already saved its sign,
        // we transform it to positive, and solve the easier problem of parsing a two-complement
        // little endian number (which is Java's choice of number representation).
        //
        // The last thing to do, is to consider its endianness, as we get it in little endian, and
        // Java uses big endian.

        final boolean isNegative = (numberBytes[numberBytes.length - 1] & MOST_SIGNIFICANT_BIT_MASK)
                != 0;

        numberBytes[numberBytes.length - 1] = (byte) (numberBytes[numberBytes.length - 1]
                & ~MOST_SIGNIFICANT_BIT_MASK);

        return ByteBuffer.wrap(numberBytes).order(ByteOrder.LITTLE_ENDIAN).getInt()
                * (isNegative ? -1 : 1);
    }
}
