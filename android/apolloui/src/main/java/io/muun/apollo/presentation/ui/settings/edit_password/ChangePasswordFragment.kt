package io.muun.apollo.presentation.ui.settings.edit_password

import android.text.TextUtils
import android.view.View
import android.widget.CheckBox
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunTextInput
import io.muun.common.Rules

class ChangePasswordFragment : SingleFragment<ChangePasswordPresenter>(), ChangePasswordView {

    @BindView(R.id.change_password_input)
    lateinit var passwordInput: MuunTextInput

    @BindView(R.id.change_password_confirm_input)
    lateinit var passwordConfirmInput: MuunTextInput

    @BindView(R.id.change_password_condition)
    lateinit var condition: CheckBox

    @BindView(R.id.change_password_continue)
    lateinit var continueButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.change_password_fragment
    }

    override fun initializeUi(view: View) {
        continueButton.isEnabled = false

        passwordInput.setPasswordRevealEnabled(true)
        passwordInput.setOnChangeListener(this) {
            // Ugly check needed for some convoluted scenario where we receive input and fragment
            // is being re-created or something
            if (::passwordInput.isInitialized) {
                validateInputs()
            }
        }
        passwordConfirmInput.setPasswordRevealEnabled(true)
        passwordConfirmInput.setOnChangeListener(this) {
            // Ugly check needed for some convoluted scenario where we receive input and fragment
            // is being re-created or something
            if (::passwordConfirmInput.isInitialized) {
                validateInputs()
            }
        }

        condition.setOnCheckedChangeListener { _, _ ->
            validateInputs()
        }
        continueButton.setOnClickListener { onContinueButtonClick() }
    }

    override fun onBackPressed(): Boolean {
        val muunDialog = MuunDialog.Builder()
            .title(R.string.change_password_abort_dialog_title)
            .message(R.string.change_password_abort_dialog_message)
            .positiveButton(R.string.change_password_abort_dialog_yes) { abort() }
            .negativeButton(R.string.change_password_abort_dialog_no, null)
            .build()

        parentActivity.showDialog(muunDialog)
        return true
    }

    private fun abort() {
        safeGetParentActivity().ifPresent(BaseActivity<*>::finishActivity)
    }

    override fun setLoading(loading: Boolean) {
        continueButton.setLoading(loading)
        condition.isEnabled = !loading
    }

    override fun setPasswordError(error: UserFacingError?) {
        passwordInput.clearError()

        if (error != null) {
            passwordInput.setError(error)
            passwordInput.requestFocusInput()
        }
    }

    override fun setConfirmPasswordError(error: UserFacingError?) {
        passwordConfirmInput.clearError()

        if (error != null) {
            passwordConfirmInput.setError(error)
            passwordConfirmInput.requestFocusInput()
        }
    }

    override fun onResume() {
        super.onResume()
        passwordInput.requestFocusInput()
    }

    override fun blockScreenshots(): Boolean {
        return true
    }

    private fun validateInputs() {
        val validPassword = isValidPassword(passwordInput.text.toString())
        val validPasswordConfirm = isValidPassword(passwordConfirmInput.text.toString())

        continueButton.isEnabled = validPassword && validPasswordConfirm && condition.isChecked
    }

    private fun onContinueButtonClick() {
        presenter.submitPassword(passwordInput.text.toString(), passwordConfirmInput.text.toString())
    }

    private fun isValidPassword(password: String) =
        !TextUtils.isEmpty(password) && password.length >= Rules.PASSWORD_MIN_LENGTH

}