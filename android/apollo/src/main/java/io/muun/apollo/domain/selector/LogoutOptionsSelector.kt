package io.muun.apollo.domain.selector

import io.muun.common.model.OperationStatus
import rx.Observable
import javax.inject.Inject

class LogoutOptionsSelector @Inject constructor(
    private val userSel: UserSelector,
    private val paymentContextSel: PaymentContextSelector,
    private val operationSel: OperationSelector
) {

    class LogoutOptions(
        private val isRecoverable: Boolean,
        private val hasBalance: Boolean,
        private val hasUnsettledOps: Boolean,
        private val hasPendingIncomingSwaps: Boolean
    ) {

        fun isBlocked(): Boolean {
            if (isRecoverable) {
                return hasPendingIncomingSwaps
            } else {
                return hasBalance || hasUnsettledOps
            }
        }

        fun wouldDeleteWallet(): Boolean {
            return !isRecoverable
        }

        @Deprecated("use isBlocked instead")
        fun canDeleteWallet(): Boolean {
            return isRecoverable || (!hasBalance && !hasUnsettledOps)
        }
    }

    fun watch(): Observable<LogoutOptions> =
        Observable
            .combineLatest(
                userSel.watch(),
                paymentContextSel.watch().map { it.userBalance > 0 },
                operationSel.watchUnsettled()
            ) { user, balance, unsettledOps ->
                val hasPendingIncomingSwap = unsettledOps
                        .filter { it.incomingSwap != null }
                        .filter { it.status == OperationStatus.BROADCASTED }
                        .isNotEmpty()

                LogoutOptions(
                        user.isRecoverable,
                        balance,
                        unsettledOps.isNotEmpty(),
                        hasPendingIncomingSwap
                )
            }

    fun get(): LogoutOptions =
        watch().toBlocking().first()

}