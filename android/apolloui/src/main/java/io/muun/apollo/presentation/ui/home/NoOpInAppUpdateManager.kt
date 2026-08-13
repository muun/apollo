package io.muun.apollo.presentation.ui.home

class NoOpInAppUpdateManager : InAppUpdateManager {

    override fun checkForUpdate() {
        // No-op
    }
}
