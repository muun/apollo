package io.muun.apollo.presentation.ui.show_qr.bitcoin.help

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.base.di.PerFragment
import javax.inject.Inject

@PerFragment
class BitcoinAddressHelpPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, ParentPresenter>()
