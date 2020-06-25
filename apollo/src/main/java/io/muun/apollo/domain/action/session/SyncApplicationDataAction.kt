package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.action.HardwareWalletActions
import io.muun.apollo.domain.action.OperationActions
import io.muun.apollo.domain.action.SigninActions
import io.muun.apollo.domain.action.base.BaseAsyncAction2
import io.muun.apollo.domain.action.base.BaseAsyncAction3
import io.muun.apollo.domain.action.keys.SyncPublicKeySetAction
import io.muun.apollo.domain.action.operation.FetchNextTransactionSizeAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.errors.InitialSyncError
import io.muun.apollo.domain.model.User
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.rx.RxHelper
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SyncApplicationDataAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val hardwareWalletActions: HardwareWalletActions,
    private val contactActions: ContactActions,
    private val operationActions: OperationActions,
    private val signinActions: SigninActions,
    private val syncPublicKeySet: SyncPublicKeySetAction,
    private val fetchNextTransactionSize: FetchNextTransactionSizeAction,
    private val fetchRealTimeData: FetchRealTimeDataAction,
    private val createFirstSession: CreateFirstSessionAction

): BaseAsyncAction3<Boolean, Boolean, Boolean, Void>() {

    override fun action(isFirstSession: Boolean, hasContactsPermission: Boolean, hasRC: Boolean) =
        Observable.defer { syncApplicationData(isFirstSession, hasContactsPermission, hasRC) }

    /**
     * Synchronize Apollo with Houston.
     */
    private fun syncApplicationData(isFirstSession: Boolean,
                                    hasContactsPermission: Boolean,
                                    hasRecoveryCode: Boolean): Observable<Void> {

        val syncContacts = if (hasContactsPermission) {
            // Sync phone contacts sending PATCH, then fetch full list:
            contactActions.syncPhoneContacts()
                .flatMap { contactActions.fetchReplaceContacts() }

        } else {
            // Just fetch previous contacts, we can't PATCH with local changes:
            contactActions.fetchReplaceContacts()
        }

        // Before anything else, new (unrecoverable) users need a session:
        val step0 = if (isFirstSession) {
            createFirstSession.action()
        } else {
            Observable.just(null)
        }

        // These can run in any order:
        val step1 = Observable.zip(
            fetchUserInfo(),
            fetchNextTransactionSize.action(),
            hardwareWalletActions.fetchReplaceHardwareWallets(),
            fetchRealTimeData.action(),
            syncContacts,
            RxHelper::toVoid
        )

        // These must run after the ones before:
        val step2 = Observable.zip(
            operationActions.fetchReplaceOperations(),
            syncPublicKeySet.action(),
            RxHelper::toVoid
        )

        return Observable.concat(step0, step1, step2)
            .lastOrDefault(null)
            .onErrorResumeNext { throwable -> Observable.error(InitialSyncError(throwable)) }
            .doOnNext { userRepository.storeInitialSyncCompleted() }
            .toVoid()
    }


    private fun fetchUserInfo(): Observable<User> =
        houstonClient.fetchUser()
            .doOnNext { userRepository.store(it) }
            .doOnNext { signinActions.setupCrashlytics() }
}
