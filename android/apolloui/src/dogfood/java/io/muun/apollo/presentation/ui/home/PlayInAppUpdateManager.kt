package io.muun.apollo.presentation.ui.home

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.muun.apollo.R
import timber.log.Timber

class PlayInAppUpdateManager(
    private val activity: AppCompatActivity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) : InAppUpdateManager, DefaultLifecycleObserver {

    companion object {
        private const val DAYS_FOR_FLEXIBLE_UPDATE = 3
    }

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

    private var statusSnackbar: Snackbar? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.PENDING -> showStatusSnackbar(R.string.in_app_update_pending)
            InstallStatus.DOWNLOADING -> showStatusSnackbar(R.string.in_app_update_downloading)
            InstallStatus.DOWNLOADED -> {
                dismissStatusSnackbar()
                showRestartSnackbar()
            }

            InstallStatus.FAILED -> {
                dismissStatusSnackbar()
                Timber.i("InAppUpdate: download failed")
            }

            InstallStatus.CANCELED -> dismissStatusSnackbar()
            else -> { /* INSTALLING, INSTALLED, UNKNOWN — no UI needed */
            }
        }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun checkForUpdate() {
        appUpdateManager.registerListener(installStateListener)

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->

            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {

                val isFlexibleUpdate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                val updateStalenessInDays = appUpdateInfo.clientVersionStalenessDays() ?: -1
                val updateType = if (isFlexibleUpdate) "flexible" else "immediate"

                Timber.i(
                    "InAppUpdate: %s update available. Staleness: %s",
                    updateType,
                    updateStalenessInDays
                )

                if (isFlexibleUpdate && updateStalenessInDays > DAYS_FOR_FLEXIBLE_UPDATE) {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                    )
                    Timber.i("InAppUpdate: flexible update dialog shown")
                }
            }
        }.addOnFailureListener { e ->
            Timber.e(e, "InAppUpdate: failed to check for updates")
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        // If the user backgrounds the app during download, check completion on resume
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showRestartSnackbar()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        appUpdateManager.unregisterListener(installStateListener)
        owner.lifecycle.removeObserver(this)
    }

    private fun showStatusSnackbar(messageRes: Int) {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)
        val snackbar = statusSnackbar
        if (snackbar?.isShown == true) {
            snackbar.setText(messageRes)
        } else {
            statusSnackbar = Snackbar
                .make(rootView, messageRes, Snackbar.LENGTH_INDEFINITE)
                .also { it.show() }
        }
    }

    private fun dismissStatusSnackbar() {
        statusSnackbar?.dismiss()
        statusSnackbar = null
    }

    private fun showRestartSnackbar() {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content)

        Snackbar.make(rootView, R.string.in_app_update_downloaded, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.in_app_update_restart) {
                appUpdateManager.completeUpdate()
            }.show()
    }

}
