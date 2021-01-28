package io.muun.apollo.presentation.ui.fragments.recovery_tool

import android.text.TextUtils
import android.view.View
import android.widget.TextView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.utils.LinkBuilder
import javax.inject.Inject

class RecoveryToolFragment: SingleFragment<RecoveryToolPresenter>(), BaseView {

    @Inject
    lateinit var linkBuilder: LinkBuilder

    @BindView(R.id.body)
    lateinit var body: TextView

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_recovery_tool

    override fun initializeUi(view: View?) {
        super.initializeUi(view)

        body.text = TextUtils.concat(
            getString(R.string.recovery_tool_info_body),
            " ",
            linkBuilder.recoveryToolLink()
        )
    }
}