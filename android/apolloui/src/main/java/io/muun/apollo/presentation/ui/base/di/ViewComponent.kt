package io.muun.apollo.presentation.ui.base.di

import dagger.Subcomponent
import io.muun.apollo.presentation.ui.view.FeeManualInput
import io.muun.apollo.presentation.ui.view.MuunAmountInput
import io.muun.apollo.presentation.ui.view.MuunLockOverlay
import io.muun.apollo.presentation.ui.view.MuunNumericKeyboard
import io.muun.apollo.presentation.ui.view.MuunPictureInput
import io.muun.apollo.presentation.ui.view.MuunTextInput

@PerView
@Subcomponent
interface ViewComponent {

    fun inject(muunTextInput: MuunTextInput)

    fun inject(feeManualInput: FeeManualInput)

    fun inject(muunAmountInput: MuunAmountInput)

    fun inject(muunPictureInput: MuunPictureInput)

    fun inject(muunLockOverlay: MuunLockOverlay)

    fun inject(muunNumericKeyboard: MuunNumericKeyboard)
}