package io.muun.apollo.domain

import io.muun.apollo.data.preferences.ApiMigrationsVersionRepository
import io.muun.apollo.domain.action.incoming_swap.RegisterInvoicesAction
import io.muun.apollo.domain.action.migration.FetchSwapServerKeyAction
import io.muun.apollo.domain.action.migration.MigrateChallengeKeysAction
import io.muun.apollo.domain.action.migration.MigrateFingerprintsAction
import io.muun.common.utils.Preconditions
import rx.Observable
import rx.functions.Action0
import rx.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class ApiMigrationsManager
@Inject constructor(
        private val apiMigrationsVersionRepository: ApiMigrationsVersionRepository,
        private val registerInvoices: RegisterInvoicesAction,
        private val fetchSwapServerKeyAction: FetchSwapServerKeyAction,
        private val migrateChallengeKeys: MigrateChallengeKeysAction,
        private val migrateFingerprints: MigrateFingerprintsAction
) {

    private val migrations: MutableMap<Int, Action0> = mutableMapOf()

    private var maxVersion: Int = 0

    init {
        add(1, registerInvoices::actionNow)
        add(2, fetchSwapServerKeyAction::actionNow)
        add(3, migrateChallengeKeys::actionNow)
        add(4, migrateFingerprints::actionNow)
    }

    private fun add(version: Int, action: Action0) {
        Preconditions.checkArgument(version == maxVersion + 1)
        migrations[version] = action
        maxVersion = version
    }

    fun run(): Observable<Unit> {
        return Observable.fromCallable(this::migrate)
                .subscribeOn(Schedulers.io())
    }

    fun reset() {
        apiMigrationsVersionRepository.version = maxVersion
    }

    private fun migrate() {
        val nextVersion = apiMigrationsVersionRepository.version + 1

        for (versionToApply in nextVersion..maxVersion) {
            migrateOne(versionToApply)
        }
    }

    private fun migrateOne(versionToApply: Int) {
        val action = migrations[versionToApply]!! // crash loudly if we missed an index

        try {
            action.call()
            apiMigrationsVersionRepository.version = versionToApply

        } catch (e: Throwable) {
            Timber.e(e, "Applying migration number $versionToApply")
            throw e // rethrow to stop applying migrations and notify callers
        }
    }

    fun hasPending(): Boolean {
        return apiMigrationsVersionRepository.version < maxVersion
    }
}