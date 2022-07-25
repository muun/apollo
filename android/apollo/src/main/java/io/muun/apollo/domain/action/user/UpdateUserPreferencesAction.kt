package io.muun.apollo.domain.action.user

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.UserPreferencesRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.model.user.UserPreferences
import rx.Completable
import rx.Observable
import rx.Single
import javax.inject.Inject

typealias UserPreferencesMutator = (UserPreferences) -> UserPreferences

class UpdateUserPreferencesAction @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val houstonClient: HoustonClient,
) : BaseAsyncAction1<UserPreferencesMutator, Unit>() {

    override fun action(mutator: UserPreferencesMutator): Observable<Unit> {
        return userPreferencesRepository.watch()
            .first()
            .toSingle()
            .map(mutator)
            .flatMap { prefs ->
                Single.defer {
                    houstonClient.updateUserPreferences(prefs)
                        .andThen(Single.just(prefs))
                }
            }
            .flatMapCompletable { prefs ->
                Completable.fromAction {
                    userPreferencesRepository.update(prefs)
                }
            }
            .andThen(Observable.just(Unit))
    }

}