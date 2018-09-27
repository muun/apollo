package io.muun.common.utils;

import javax.money.MonetaryAmount;

public class MoneyUtils {

    /**
     * @return true if the two amounts have the same number and same currency, false otherwise.
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
