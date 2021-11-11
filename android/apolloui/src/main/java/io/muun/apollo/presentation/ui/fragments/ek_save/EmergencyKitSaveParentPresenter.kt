package io.muun.apollo.presentation.ui.fragments.ek_save

import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.ui.base.ParentPresenter

interface EmergencyKitSaveParentPresenter: ParentPresenter {

    fun setGeneratedEmergencyKit(kitGen: GeneratedEmergencyKit)

    fun getGeneratedEmergencyKit(): GeneratedEmergencyKit

    fun reportEmergencyKitUploaded(driveFile: DriveFile)

    fun reportEmergencyKitShared()

    fun cancelEmergencyKitSave()
}