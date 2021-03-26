package io.muun.apollo.presentation.ui.activity.extension

import android.os.Bundle
import io.muun.apollo.presentation.ui.base.ActivityExtension
import io.muun.apollo.presentation.ui.base.Presenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.utils.PresenterProvider
import java.util.*
import javax.inject.Inject

@PerActivity
class PersistentPresenterExtension @Inject constructor(): ActivityExtension() {

    companion object {
        const val PERSISTENT_PRESENTER_ID = "__persistentPresenterId"
    }

    var persistentPresenterId: String? = null
    var persistentPresenter: Presenter<*>? = null

    fun get(inState: Bundle?, freshPresenter: Presenter<*>): Presenter<*> {
        val existingPresenterId = inState?.getString(PERSISTENT_PRESENTER_ID)

        if (existingPresenterId != null) {
            persistentPresenterId = existingPresenterId
            persistentPresenter = PresenterProvider.get(existingPresenterId)

            if (persistentPresenter == null) {
                // This can happen if we're being restored after the process was killed by the
                // system, if the ActivityManager decided to preserve our instance state.
                persistentPresenter = freshPresenter
                PresenterProvider.register(persistentPresenterId!!, freshPresenter)
            }

        } else {
            persistentPresenterId = UUID.randomUUID().toString()
            persistentPresenter = freshPresenter
            PresenterProvider.register(persistentPresenterId!!, freshPresenter)
        }

        return persistentPresenter!!
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PERSISTENT_PRESENTER_ID, persistentPresenterId)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (persistentPresenterId != null && activity.isFinishing) {
            PresenterProvider.unregister(persistentPresenterId!!)
        }
    }
}