package io.muun.common.utils;

import org.javamoney.moneta.Money;

import java.math.BigInteger;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

public class BitcoinUtils {

    public static final int BITCOIN_PRECISION = 8;

    public static final BigInteger SATOSHIS_PER_BITCOIN = BigInteger.TEN.pow(BITCOIN_PRECISION);

    private static final long MIN_TX_INPUT_SIZE_IN_BYTES = 148; // based on a P2PKH input
    private static final long MIN_TX_OUTPUT_SIZE_IN_BYTES = 34; // based on a P2PKH output
    private static final long MIN_RELAY_TX_FEE_IN_SATOSHIS_PER_KB = 1000; // default in core client

    private static final long MIN_TX_SIZE_IN_BYTES =
            MIN_TX_INPUT_SIZE_IN_BYTES + MIN_TX_OUTPUT_SIZE_IN_BYTES;

    private static final long MIN_FEE_IN_SATOSHIS =
            MIN_TX_SIZE_IN_BYTES * MIN_RELAY_TX_FEE_IN_SATOSHIS_PER_KB / 1000;

    public static final long DUST_IN_SATOSHIS = MIN_FEE_IN_SATOSHIS * 3;

    /**
     * Transform a bitcoin monetary amount to the equivalent number of satoshis.
     */
    public static long bitcoinsToSatoshis(MonetaryAmount bitcoinAmount) {

        if (!bitcoinAmount.getCurrency().getCurrencyCode().equals("BTC")) {
            throw new IllegalArgumentException("Expected BTC amount");
        }

        // Rounding has to be called before scaling because otherwise the relevant decimal digits
        // get moved and the number isn't really rounded. Afterwards, longValue will truncate
        // instead of round.
        return bitcoinAmount
                .with(Monetary.getDefaultRounding())
                .scaleByPowerOfTen(BITCOIN_PRECISION)
                .getNumber()
                .longValue();
    }

    /**
     * Transform a number of satoshis to the equivalent bitcoin monetary amount.
     */
    public static MonetaryAmount satoshisToBitcoins(long satoshis) {

        return Money.of(satoshis, "BTC").scaleByPowerOfTen(-BITCOIN_PRECISION);
    }
}
