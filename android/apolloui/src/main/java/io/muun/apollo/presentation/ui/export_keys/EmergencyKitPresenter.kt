package io.muun.apollo.presentation.ui.export_keys

import android.os.Bundle
import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.data.apis.DriveUploader
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.errors.ChallengeKeyMigrationError
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyParentPresenter
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpParentPresenter
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroParentPresenter
import javax.inject.Inject

@PerActivity
class EmergencyKitPresenter @Inject constructor(
    private val driveUploader: DriveUploader,
) : BasePresenter<EmergencyKitView>(),
    FlowIntroParentPresenter,
    EmergencyKitSaveParentPresenter,
    EmergencyKitVerifyParentPresenter,
    EmergencyKitVerifyHelpParentPresenter,
    EmergencyKitCloudVerifyParentPresenter {

    var step = EmergencyKitStep.LOADING

    var uploadedFile: DriveFile? = null

    private var generatedEK: GeneratedEmergencyKit? = null

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        if (step === EmergencyKitStep.LOADING) {
            // There used to be async behavior here. Now, it's immediate. We still keep this
            // default LOADING state (instead of `null`) to navigate. This could be done better,
            // but at the time of writing we were hard-pressed for time.
            goToStep(EmergencyKitStep.INTRO)
        }
    }

    private fun goToStep(step: EmergencyKitStep) {
        this.step = step
        view!!.goToStep(step)
    }

    override fun refreshToolbar() {
        view.refreshToolbar()
    }

    override fun setGeneratedEmergencyKit(kitGen: GeneratedEmergencyKit) {
        generatedEK = kitGen
    }

    override fun getGeneratedEmergencyKit(): GeneratedEmergencyKit =
        generatedEK!!

    override fun confirmEmergencyKitUploaded(driveFile: DriveFile) {
        this.uploadedFile = driveFile
        view!!.goToStep(EmergencyKitStep.CLOUD_VERIFY)
    }

    override fun confirmManualShareCompleted() {
        view!!.goToStep(EmergencyKitStep.VERIFY)
    }

    override fun confirmIntroduction() {
        goToStep(EmergencyKitStep.SAVE)
    }

    override fun confirmEmergencyKitVerify() {
        goToStep(EmergencyKitStep.SUCCESS)
    }

    override fun confirmEmergencyKitCloudVerify() {
        goToStep(EmergencyKitStep.SUCCESS)
    }

    override fun cancelEmergencyKitCloudVerify() {
        goToStep(EmergencyKitStep.SAVE)
    }

    override fun openEmergencyKitCloudFile() {
        driveUploader.open(context, uploadedFile!!)
    }

    override fun saveEmergencyKitAgain() {
        goToStep(EmergencyKitStep.SAVE)
    }

    override fun showEmergencyKitVerifyHelp() {
        goToStep(EmergencyKitStep.VERIFY_HELP)
    }

    override fun cancelEmergencyKitSave() {
        view.showSaveAbortDialog()
    }

    override fun cancelEmergencyKitVerify() {
        goToStep(EmergencyKitStep.SAVE)
    }

    override fun cancelEmergencyKitVerifyHelp() {
        goToStep(EmergencyKitStep.VERIFY)
    }

    override fun handleError(error: Throwable) {
        if (error is ChallengeKeyMigrationError) {
            goToStep(EmergencyKitStep.ERROR)
        } else {
            super.handleError(error)
        }
    }

    fun abortEmergencyKitFlow() {
        analytics.report(AnalyticsEvent.E_EMERGENCY_KIT_ABORTED())
        view.finishActivity()
    }
}