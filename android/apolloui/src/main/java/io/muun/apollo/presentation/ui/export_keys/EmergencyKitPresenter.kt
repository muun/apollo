package io.muun.apollo.presentation.ui.export_keys

import android.os.Bundle
import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.apis.DriveUploader
import io.muun.apollo.domain.action.ek.ReportEmergencyKitExportedAction
import io.muun.apollo.domain.action.migration.MigrateChallengeKeysAction
import io.muun.apollo.domain.errors.ChallengeKeyMigrationError
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.ek_intro.EmergencyKitIntroParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpParentPresenter
import javax.inject.Inject

@PerActivity
class EmergencyKitPresenter @Inject constructor(
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction,
    private val driveUploader: DriveUploader
):
    BasePresenter<EmergencyKitView>(),
    EmergencyKitIntroParentPresenter,
    EmergencyKitSaveParentPresenter,
    EmergencyKitVerifyParentPresenter,
    EmergencyKitVerifyHelpParentPresenter,
    EmergencyKitCloudVerifyParentPresenter {

    var step = EmergencyKeysStep.LOADING

    var uploadedFile: DriveFile? = null

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        if (step === EmergencyKeysStep.LOADING) {
            // There used to be async behavior here. Now, it's immediate. We still keep this
            // default LOADING state (instead of `null`) to navigate. This could be done better,
            // but at the time of writing we were hard-pressed for time.
            goToStep(EmergencyKeysStep.INTRO)
        }
    }

    private fun goToStep(step: EmergencyKeysStep) {
        this.step = step
        view!!.goToStep(step)
    }

    override fun refreshToolbar() {
        view.refreshToolbar()
    }

    override fun reportEmergencyKitUploaded(driveFile: DriveFile) {
        this.uploadedFile = driveFile
        view!!.goToStep(EmergencyKeysStep.CLOUD_VERIFY)
    }

    override fun reportEmergencyKitShared() {
        view!!.goToStep(EmergencyKeysStep.VERIFY)
    }

    override fun confirmEmergencyKitIntro() {
        goToStep(EmergencyKeysStep.SAVE)
    }

    override fun confirmEmergencyKitVerify() {
        reportEmergencyKitExported.run(true)
        goToStep(EmergencyKeysStep.SUCCESS)
    }

    override fun confirmEmergencyKitCloudVerify() {
        reportEmergencyKitExported.run(true)
        goToStep(EmergencyKeysStep.SUCCESS)
    }

    override fun cancelEmergencyKitCloudVerify() {
        goToStep(EmergencyKeysStep.SAVE)
    }

    override fun openEmergencyKitCloudFile() {
        driveUploader.open(context, uploadedFile!!)
    }

    override fun saveEmergencyKitAgain() {
        goToStep(EmergencyKeysStep.SAVE)
    }

    override fun showEmergencyKitVerifyHelp() {
        goToStep(EmergencyKeysStep.VERIFY_HELP)
    }

    override fun cancelEmergencyKitSave() {
        view!!.finishActivity()
    }

    override fun cancelEmergencyKitVerify() {
        goToStep(EmergencyKeysStep.SAVE)
    }

    override fun cancelEmergencyKitVerifyHelp() {
        goToStep(EmergencyKeysStep.VERIFY)
    }

    override fun handleError(error: Throwable) {
        if (error is ChallengeKeyMigrationError) {
            goToStep(EmergencyKeysStep.ERROR)
        } else {
            super.handleError(error)
        }
    }

}