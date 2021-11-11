package io.muun.apollo.presentation.ui.fragments.ek_verify_cloud

import io.muun.apollo.presentation.ui.base.ParentPresenter

interface EmergencyKitCloudVerifyParentPresenter: ParentPresenter {

    fun confirmEmergencyKitCloudVerify()

    fun cancelEmergencyKitCloudVerify()

    fun openEmergencyKitCloudFile()

}