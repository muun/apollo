package io.muun.apollo.presentation.ui.fragments.ek_verify

import io.muun.apollo.domain.model.GeneratedEmergencyKitInfo
import io.muun.apollo.presentation.ui.base.ParentPresenter

interface EmergencyKitVerifyParentPresenter: ParentPresenter {

    fun getGeneratedEmergencyKit(): GeneratedEmergencyKitInfo

    fun refreshToolbar()

    fun confirmEmergencyKitVerify()

    fun showEmergencyKitVerifyHelp()

    fun cancelEmergencyKitVerify()
}