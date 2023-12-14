package io.muun.apollo.presentation.ui.fragments.ek_verify_cloud

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton


class EmergencyKitCloudVerifyFragment : SingleFragment<EmergencyKitCloudVerifyPresenter>(),
    EmergencyKitCloudVerifyView {

    @BindView(R.id.open_cloud_file)
    lateinit var openCloudFileButton: MuunButton

    @BindView(R.id.confirm)
    lateinit var confirmButton: MuunButton

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_ek_cloud_verify

    override fun initializeUi(view: View) {
        openCloudFileButton.setOnClickListener {
            presenter.openCloudFile()
        }

        confirmButton.setOnClickListener {
            presenter.confirmVerify()
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }
}