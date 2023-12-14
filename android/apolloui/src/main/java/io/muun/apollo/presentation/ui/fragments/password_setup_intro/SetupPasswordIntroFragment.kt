package io.muun.apollo.presentation.ui.fragments.password_setup_intro

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunInfoBox

class SetupPasswordIntroFragment : SingleFragment<SetupPasswordIntroPresenter>(),
    SetupPasswordIntroView {

    @BindView(R.id.setup_password_intro_info_box)
    lateinit var infoBox: MuunInfoBox

    @BindView(R.id.create_email_skip)
    lateinit var skipButton: MuunButton

    @BindView(R.id.setup_password_intro_action)
    lateinit var actionButton: MuunButton

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_setup_password_intro

    override fun initializeUi(view: View) {
        skipButton.setOnClickListener { presenter.skipEmailSetup() }
        actionButton.setOnClickListener { presenter.startSetup() }
    }

    override fun setUpHeader() {
        parentActivity.header.let {
            it.setNavigation(MuunHeader.Navigation.BACK)
            it.hideTitle()
            it.setIndicatorText(null)
            it.setElevated(false)
        }
    }

    override fun setSecurityLevel(securityLevel: SecurityLevel) {
        if (securityLevel == SecurityLevel.ANON) {
            skipButton.visibility = View.VISIBLE
        }

        val hasRecoveryCode = securityLevel == SecurityLevel.SKIPPED_EMAIL_RC
            || securityLevel == SecurityLevel.SKIPPED_EMAIL_DONE

        if (hasRecoveryCode) {
            infoBox.setTitle(R.string.setup_password_intro_title_has_rc)
            infoBox.setDescription(R.string.setup_password_intro_body_has_rc)
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }
}