package io.muun.apollo.domain.selector

import io.muun.common.model.OperationStatus
import io.muun.common.utils.Preconditions
import rx.Observable
import javax.inject.Inject

class LogoutOptionsSelector @Inject constructor(
    private val userSel: UserSelector,
    private val paymentContextSel: PaymentContextSelector,
    private val operationSel: OperationSelector,
) {

    class LogoutOptions(
        private val isRecoverable: Boolean,
        private val hasBalance: Boolean,
        private val hasUnsettledOps: Boolean,
        private val hasPendingIncomingSwaps: Boolean,
    ) {

        fun isLogoutBlocked(): Boolean {
            Preconditions.checkArgument(isRecoverable)
            return hasPendingIncomingSwaps
        }

        fun canDeleteWallet(): Boolean {
            return !hasBalance && !hasUnsettledOps
        }

        fun isRecoverable(): Boolean {
            return isRecoverable
        }
    }

    private fun watch(): Observable<LogoutOptions> =
        Observable
            .combineLatest(
                userSel.watch(),
                paymentContextSel.watch().map { it.userBalance > 0 },
                operationSel.watchUnsettled()
            ) { user, balance, unsettledOps ->
                val hasPendingIncomingSwap = unsettledOps
                    .filter { it.isIncomingSwap }
                    .any { it.status == OperationStatus.BROADCASTED }

                LogoutOptions(
                    user.isRecoverable,
                    balance,
                    unsettledOps.isNotEmpty(),
                    hasPendingIncomingSwap
                )
            }

    fun get(): LogoutOptions =
        watch().toBlocking().first()

    fun isRecoverable(): Boolean {
        if (userSel.getOptional().isPresent) {
            try {
                return get().isRecoverable()
            } catch (e: Exception) {
                // Turns there's a lot of reasons we can fail to fetch the options.
                // 1. We have no user
                // 2. We have no exchange rate windows
                // Others could exist too and callers of this method should not be concerned with
                // them.
            }
        }

        // If we don't have enough data to decide we'll assume its some early or inconsistent
        // state and we default to false
        // That is, if we're not sure don't delete anything just in case
        return false
    }
}