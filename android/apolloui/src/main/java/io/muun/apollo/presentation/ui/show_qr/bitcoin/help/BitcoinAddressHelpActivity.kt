package io.muun.apollo.presentation.ui.show_qr.bitcoin.help

import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.*
import io.muun.apollo.presentation.ui.view.MuunHeader


class BitcoinAddressHelpActivity : SingleFragmentActivity<BasePresenter<BaseView>>() {

    @BindView(R.id.header)
    lateinit var headerView: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.activity_bitcoin_address_help

    override fun getHeader(): MuunHeader =
        headerView

    override fun getFragmentsContainer(): Int {
        return R.id.fragment_container
    }

    override fun getInitialFragment(): BaseFragment<out Presenter<*>> {
        return BitcoinAddressHelpFragment()
    }
}