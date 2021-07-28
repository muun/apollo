package io.muun.apollo.presentation.ui.fragments.create_password

import android.view.View
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunTextInput

class CreatePasswordFragment : SingleFragment<CreatePasswordPresenter>(), CreatePasswordView {

    @BindView(R.id.create_password_input)
    lateinit var passwordInput: MuunTextInput

    @BindView(R.id.create_password_confirm_input)
    lateinit var passwordConfirmInput: MuunTextInput

    @BindView(R.id.create_password_confirm)
    lateinit var confirmButton: MuunButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.fragment_create_password

    override fun initializeUi(view: View) {
        super.initializeUi(view)

        parentActivity.header.setNavigation(MuunHeader.Navigation.EXIT)

        passwordInput.setPasswordRevealEnabled(true)
        passwordInput.setOnChangeListener {
            // Ugly check needed for some convoluted scenario where we receive input and fragment
            // is being re-created or something
            if (::passwordInput.isInitialized) {
                validatePassword()
            }
        }

        passwordConfirmInput.setPasswordRevealEnabled(true)
        passwordConfirmInput.setOnChangeListener {
            // Ugly check needed for some convoluted scenario where we receive input and fragment
            // is being re-created or something
            if (::passwordConfirmInput.isInitialized) {
                validatePassword()
            }
        }

        confirmButton.isEnabled = false
        confirmButton.setOnClickListener {
            presenter.submitPassword(
                passwordInput.text.toString(),
                passwordConfirmInput.text.toString()
            )
        }
    }

    override fun onBackPressed(): Boolean {
        presenter.goBack()
        return true
    }

    override fun onResume() {
        super.onResume()
        passwordInput.requestFocusInput()
    }

    override fun setPasswordError(error: UserFacingError?) {
        passwordInput.clearError()

        if (error != null) {
            passwordInput.setError(error)
            passwordInput.requestFocusInput()
        }
    }

    override fun setConfirmPasswordError(error: UserFacingError) {
        passwordConfirmInput.clearError()

        passwordConfirmInput.setError(error)
        passwordConfirmInput.requestFocusInput()
        confirmButton.isEnabled = false
    }

    override fun setLoading(isLoading: Boolean) {
        passwordInput.isEnabled = !isLoading
        confirmButton.isLoading = isLoading
    }

    private fun validatePassword() {
        val validPassword = presenter.isValidPassword(passwordInput.text.toString())
        val validPasswordConfirm = presenter.isValidPassword(passwordConfirmInput.text.toString())

        confirmButton.isEnabled = validPassword && validPasswordConfirm
    }
}