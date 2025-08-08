package io.muun.apollo.domain.action.migration

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.errors.ChallengeKeyMigrationError
import io.muun.apollo.domain.utils.replaceTypedError
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengeType
import rx.Observable
import javax.inject.Inject

class MigrateChallengeKeysAction @Inject constructor(
    val keysRepository: KeysRepository,
    val houstonClient: HoustonClient,
) : BaseAsyncAction0<Void>() {

    override fun action(): Observable<Void> =
        if (!keysRepository.hasMigratedChallengeKeys())
            executeMigration()
        else
            Observable.just(null)

    private fun executeMigration() =
        houstonClient.fetchChallengeKeyMigrationData()
            .doOnNext { data ->
                data.newRecoveryCodeKeySalt?.let {
                    keysRepository.seasonPublicChallengeKey(
                        it,
                        ChallengeType.RECOVERY_CODE,
                    )
                }

                data.newPasswordKeySalt.let {
                    keysRepository.seasonPublicChallengeKey(
                        it,
                        ChallengeType.PASSWORD,
                    )
                }

                data.newEncryptedMuunKey?.let {
                    keysRepository.storeEncryptedMuunPrivateKey(it)
                }
            }
            .replaceTypedError(Throwable::class.java) { ChallengeKeyMigrationError(it) }
            .toVoid()
}