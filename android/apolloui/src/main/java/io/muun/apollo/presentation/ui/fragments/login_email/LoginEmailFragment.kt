package io.muun.apollo.presentation.ui.fragments.login_email

import android.view.View
import butterknife.BindView
import butterknife.OnClick
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunTextInput
import io.muun.common.bitcoinj.ValidationHelpers

class LoginEmailFragment: SingleFragment<LoginEmailPresenter>(), LoginEmailView {

    @BindView(R.id.enter_email_input)
    lateinit var emailInput: MuunTextInput

    @BindView(R.id.enter_email_action)
    lateinit var confirm: MuunButton

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.login_email


    override fun initializeUi(view: View) {
        super.initializeUi(view)

        parentActivity.header.apply {
            setNavigation(Navigation.BACK)
            showTitle(R.string.login_title)
            setElevated(true)
        }

        emailInput.setOnChangeListener { validateEmailInput() }
        validateEmailInput()

    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun setLoading(isLoading: Boolean) {
        confirm.setLoading(isLoading)
    }

    override fun autoFillEmail(email: String) {
        emailInput.setText(email)
    }

    override fun setEmailError(error: UserFacingError?) {
        emailInput.setError(error)
    }

    @OnClick(R.id.enter_email_action)
    fun onConfirmClick() {
        presenter.submitEmail(emailInput.text.toString())
    }

    @OnClick(R.id.enter_email_use_rc_only)
    fun onRecoverWithRecoveryCodeClick(v: View) {
        presenter.useRecoveryCodeOnlyLogin()
        confirm.isEnabled
    }

    private fun validateEmailInput() {
        setEmailError(null)
        confirm.isEnabled = ValidationHelpers.isValidEmail(emailInput.text.toString())
    }
}