package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.UserPreferencesRepository
import io.muun.apollo.domain.model.UserPreferences
import rx.Observable
import javax.inject.Inject

class UserPreferencesSelector @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    fun watch(): Observable<UserPreferences> {
        return userPreferencesRepository.watch()
    }

    fun get(): UserPreferences {
        return watch().toBlocking().first()
    }
}