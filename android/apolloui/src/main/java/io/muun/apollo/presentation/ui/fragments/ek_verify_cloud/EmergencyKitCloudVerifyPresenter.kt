package io.muun.apollo.presentation.ui.fragments.ek_verify_cloud

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class EmergencyKitCloudVerifyPresenter @Inject constructor():
    SingleFragmentPresenter<EmergencyKitCloudVerifyView, EmergencyKitCloudVerifyParentPresenter>() {

    fun goBack() {
        parentPresenter.cancelEmergencyKitCloudVerify()
    }

    fun openCloudFile() {
        parentPresenter.openEmergencyKitCloudFile()
    }

    fun confirmVerify() {
        parentPresenter.confirmEmergencyKitCloudVerify()
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_EMERGENCY_KIT_CLOUD_VERIFY()

}