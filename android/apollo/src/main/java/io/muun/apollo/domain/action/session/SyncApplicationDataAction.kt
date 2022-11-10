package io.muun.apollo.domain.action.session

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.UserPreferencesRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.ApiMigrationsManager
import io.muun.apollo.domain.LoggingContextManager
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.action.OperationActions
import io.muun.apollo.domain.action.base.BaseAsyncAction3
import io.muun.apollo.domain.action.incoming_swap.RegisterInvoicesAction
import io.muun.apollo.domain.action.keys.SyncPublicKeySetAction
import io.muun.apollo.domain.action.operation.FetchNextTransactionSizeAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.action.session.rc_only.FinishLoginWithRcAction
import io.muun.apollo.domain.errors.fcm.GooglePlayServicesNotAvailableError
import io.muun.apollo.domain.errors.InitialSyncError
import io.muun.apollo.domain.model.LoginWithRc
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.rx.RxHelper
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SyncApplicationDataAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val userRepository: UserRepository,
    private val contactActions: ContactActions,
    private val operationActions: OperationActions,
    private val loggingContextManager: LoggingContextManager,
    private val syncPublicKeySet: SyncPublicKeySetAction,
    private val fetchNextTransactionSize: FetchNextTransactionSizeAction,
    private val fetchRealTimeData: FetchRealTimeDataAction,
    private val createFirstSession: CreateFirstSessionAction,
    private val finishLoginWithRc: FinishLoginWithRcAction,
    private val registerInvoices: RegisterInvoicesAction,
    private val apiMigrationsManager: ApiMigrationsManager,
    private val userPreferencesRepository: UserPreferencesRepository,
) : BaseAsyncAction3<Boolean, Boolean, LoginWithRc?, Void>() {

    override fun action(
        isFirstSession: Boolean,
        hasContactsPermission: Boolean,
        loginWithRc: LoginWithRc?,
    ): Observable<Void> =
        Observable.defer { syncApplicationData(isFirstSession, hasContactsPermission, loginWithRc) }

    /**
     * Synchronize Apollo with Houston.
     */
    private fun syncApplicationData(
        isFirstSession: Boolean,
        hasContactsPermission: Boolean,
        loginWithRc: LoginWithRc?,
    ): Observable<Void> {

        // Before anything else, new (unrecoverable) users need a session:
        val step0 = if (isFirstSession) {
            createFirstSession.action()

        } else if (loginWithRc != null && loginWithRc.keysetFetchNeeded) {
            finishLoginWithRc.action(loginWithRc.rc)

        } else {
            Observable.just(null)
        }

        // We need this before others so that compat users can upgrade to multisig setup
        val step1 = syncPublicKeySet.action()

        // These can run in any order:
        val step2 = Observable.zip(
            fetchUserInfo(),
            fetchNextTransactionSize.action(),
            fetchRealTimeData.action(),
            syncContacts(hasContactsPermission),
            Observable.fromCallable(apiMigrationsManager::reset),
            RxHelper::toVoid
        )

        // These must run after the ones before:
        val step3 = Observable.zip(
            operationActions.fetchReplaceOperations(),
            registerInvoices.action(),
            RxHelper::toVoid
        )

        return Observable.concat(step0, step1, step2, step3)
            .lastOrDefault(null)
            .onErrorResumeNext { throwable ->
                if (throwable is GooglePlayServicesNotAvailableError) {
                    Observable.error(throwable)
                } else {
                    Observable.error(InitialSyncError(throwable))
                }
            }
            .doOnNext { userRepository.storeInitialSyncCompleted() }
            .toVoid()
    }

    private fun fetchUserInfo(): Observable<Void> =
        houstonClient.fetchUser()
            .doOnNext {
                userRepository.store(it.fst)
                userPreferencesRepository.update(it.snd)
                loggingContextManager.setupCrashlytics()
            }
            .toVoid()

    private fun syncContacts(hasContactsPermission: Boolean): Observable<Void> {
        return if (hasContactsPermission) {
            // Sync phone contacts sending PATCH, then fetch full list:
            contactActions.syncPhoneContacts()
                .flatMap { contactActions.fetchReplaceContacts() }

        } else {
            // Just fetch previous contacts, we can't PATCH with local changes:
            contactActions.fetchReplaceContacts()
        }
    }
}
