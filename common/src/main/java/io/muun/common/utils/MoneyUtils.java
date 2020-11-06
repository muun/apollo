package io.muun.common.utils;

import javax.money.MonetaryAmount;

public class MoneyUtils {

    /**
     * Check two monetary amounts are equal (amount and currency match).
     */
    public static boolean equals(MonetaryAmount m1, MonetaryAmount m2) {

        final boolean sameCurrency = m2 != null
                && m1.getCurrency().getCurrencyCode()
                .equals(m2.getCurrency().getCurrencyCode());

        final boolean sameAmount = m2 != null
                && m1.getNumber().compareTo(m2.getNumber()) == 0;

        return sameAmount && sameCurrency;

    }

}
