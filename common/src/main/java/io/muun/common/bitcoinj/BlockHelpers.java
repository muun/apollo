package io.muun.common.bitcoinj;

import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.Block;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class BlockHelpers {

    private BlockHelpers() {
        throw new AssertionError();
    }

    /**
     * As per BIP 34, blocks (with version 2 or more) contain the height of the block in the script
     * of the only input of the coinbase transaction.
     */
    public static int getBlockHeightFromCoinbase(Block block) {

        Preconditions.checkArgument(
                block.getVersion() != 1,
                "version 1 blocks don't have the height in its coinbase transaction"
        );

        final List<org.bitcoinj.core.Transaction> transactions = block.getTransactions();

        Preconditions.checkArgument(transactions != null);
        Preconditions.checkArgument(block.getVersion() > 1);

        final byte[] script = transactions
                .get(0) // get coinbase tx
                .getInput(0) // get first input
                .getScriptBytes(); // get script bytes

        Preconditions.checkArgument(script != null && script.length > 0);
        Preconditions.checkArgument(script[0] <= 4 && script.length > script[0]);

        final ByteBuffer buffer = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(script, 1, script[0]);

        buffer.rewind();

        return buffer.getInt();
    }

    /**
     * Find the time t for which P(time until numBlocks are mined ≤ t) = certainty. That is, the
     * number of seconds one has to wait in order to be certain that numBlocks have been mined, with
     * a given certainty level.
     */
    public static int timeInSecsForBlocksWithCertainty(int numBlocks, double certainty) {

        // Since the emission of blocks is a poisson process with a number of arrivals per unit of
        // time (lambda) of 1/10 min, the time between blocks follows an exponential distribution:
        // x~exp(lambda). It turns out that's equivalent to saying that x has a gamma distribution:
        // x~gamma(1, lambda).
        //
        // Knowing that the sum of the gamma random variables x_i~gamma(k_i, r) is
        // sum(x_i)~gamma(sum(k_i), r), we can infer that the time of emission of k bitcoin blocks
        // is x~gamma(k, lambda).
        //
        // So, if T is the time it takes for k blocks to be emitted, then:
        //
        //     P(T ≤ t) = F(t; k, lambda)
        //
        // where F is the cumulative distribution function of gamma. It turns out that F can be
        // computed as:
        //
        //     P(T ≤ t) = 1 - e^(-lambda * t) * sum for i=0 to k-1 of ((lambda * t)^i / i!)
        //
        // For example, for K=1 (ie. just one block) and P = 0.9 (90% certainty), we get t = 24 min.

        if (numBlocks == 0) {
            return 0;
        }

        Preconditions.checkArgument(numBlocks > 0);
        Preconditions.checkArgument(certainty > 0 && certainty < 1);

        // find the time T that makes the gammaCdf at least a given certainty using exponential &
        // binary search over T

        int left = 0;
        int right = 1;

        while (gammaCdf(right, numBlocks, 1. / 600) < certainty) {
            right *= 2;
        }

        do {
            final int mid = (left + right) / 2;

            if (gammaCdf(mid, numBlocks, 1. / 600) < certainty) {
                left = mid;
            } else {
                right = mid;
            }
        } while (left + 1 < right);

        return right;
    }

    /**
     * Compute F(t; k, lambda) = 1 - e^(-lambda * t) * sum for i=0 to k-1 of ((lambda * t)^i / i!).
     */
    private static double gammaCdf(int time, int numBlocks, double lambda) {

        double sum = 1;
        double numerator = 1;
        double denominator = 1;

        for (int i = 1; i < numBlocks; i++) {
            numerator *= lambda * time;
            denominator *= i;
            sum += numerator / denominator;
        }

        return 1 - Math.exp(-lambda * time) * sum;
    }
}
