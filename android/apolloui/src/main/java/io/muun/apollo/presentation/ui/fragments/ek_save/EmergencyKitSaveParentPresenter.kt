package io.muun.apollo.presentation.ui.fragments.ek_save

import io.muun.apollo.data.apis.DriveFile
import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.ui.base.ParentPresenter

interface EmergencyKitSaveParentPresenter : ParentPresenter {

    fun setGeneratedEmergencyKit(kitGen: GeneratedEmergencyKit)

    fun getGeneratedEmergencyKit(): GeneratedEmergencyKit

    fun confirmEmergencyKitUploaded(driveFile: DriveFile)

    /**
     * There's a limitation here. Android OS doesn't allow us to know with 100% certainty that
     * the EK was successfully shared or saved locally. But this is our best effort, we accept
     * there will be false-positives. We signal that this step of the flow is completed and we can
     * move forward. Worst case, users can always press back and return.
     */
    fun confirmManualShareCompleted()

    fun cancelEmergencyKitSave()
}