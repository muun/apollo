package io.muun.apollo.presentation.ui.fragments.ek_verify

import io.muun.apollo.domain.model.GeneratedEmergencyKit
import io.muun.apollo.presentation.ui.base.ParentPresenter

interface EmergencyKitVerifyParentPresenter: ParentPresenter {

    fun getGeneratedEmergencyKit(): GeneratedEmergencyKit

    fun refreshToolbar()

    fun confirmEmergencyKitVerify()

    fun showEmergencyKitVerifyHelp()

    fun cancelEmergencyKitVerify()
}