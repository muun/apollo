package io.muun.apollo.domain.model;

import io.muun.common.utils.Preconditions;

public interface WithPaymentContext {

    /**
     * Obtain the PaymentContext currently in use, or throw if it's not set.
     */
    default PaymentContext getPaymentContext() {
        final PaymentContext currentlyInUse = PaymentContext.Companion.getCurrentlyInUse();
        Preconditions.checkNotNull(currentlyInUse);

        return currentlyInUse;
    }

    default boolean hasPaymentContext() {
        return PaymentContext.Companion.getCurrentlyInUse() != null;
    }
}
