package io.muun.apollo.presentation.ui.home

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity

interface InAppUpdateManager {

    fun interface Factory {
        fun create(
            activity: AppCompatActivity,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): InAppUpdateManager
    }

    /**
     * Check for available updates and start the flexible update flow if one is found.
     */
    fun checkForUpdate()
}
