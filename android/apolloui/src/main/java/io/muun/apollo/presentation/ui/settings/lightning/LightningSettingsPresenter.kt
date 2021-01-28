package io.muun.apollo.presentation.ui.settings.lightning

import android.os.Bundle
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.model.UserPreferences
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.home.HomePresenter
import rx.Observable
import javax.inject.Inject

// FIXME: The parent presenter is wrong!
class LightningSettingsPresenter
@Inject constructor(
        private val userPreferencesSelector: UserPreferencesSelector,
        private val updateUserPreferences: UpdateUserPreferencesAction
): SingleFragmentPresenter<LightningSettingsView, HomePresenter>() {

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        val combined = Observable.combineLatest(
                userPreferencesSelector.watch(),
                updateUserPreferences.state,
                UserPreferences::to
        )

        subscribeTo(combined) { pair ->
            handleState(pair.first, pair.second)
        }
    }

    private fun handleState(prefs: UserPreferences, state: ActionState<Unit>) {

        when (state.kind) {
            ActionState.Kind.EMPTY, ActionState.Kind.ERROR -> {
                view.setLoading(false)
                view.update(!prefs.strictMode)
            }
            ActionState.Kind.LOADING -> {
                view.setLoading(true)
            }
            ActionState.Kind.VALUE -> Unit
        }
    }

    fun toggle() {
        updateUserPreferences.run { prefs ->
            prefs.copy(strictMode = !prefs.strictMode)
        }
    }
}