package io.muun.apollo.presentation.ui.fragments.security_center

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.FragmentSecurityCenterBinding
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterView.TaskStatus
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getDrawable
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunTaskCard

class SecurityCenterFragment : SingleFragment<SecurityCenterPresenter>(), SecurityCenterView {

    private val binding: FragmentSecurityCenterBinding
        get() = getBinding() as FragmentSecurityCenterBinding

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_security_center

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return FragmentSecurityCenterBinding::inflate
    }

    override fun initializeUi(view: View) {
        setUpHeader()
        setUpCards()
    }

    override fun setUpHeader() {
        parentActivity.header.apply {
            visibility = View.VISIBLE
            setNavigation(Navigation.NONE)
            showTitle(R.string.security_center_title)
            setElevated(true)
        }

        // Tell parent activity we want a say in how, when and if menu items are/should be displayed
        setHasOptionsMenu(true)
    }

    private fun setUpCards() {
        binding.taskEmail.setOnClickListener { presenter.goToEmailSetup() }
        binding.taskRecoveryCode.setOnClickListener { presenter.goToRecoveryCodeSetup() }
        binding.taskExportKeys.setOnClickListener { presenter.goToExportKeys() }
        binding.buttonExportKeysAgain.setOnClickListener { presenter.goToExportKeys() }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear() // Effectively state that we don't want menu items. Yeap, this is how its done.
    }

    override fun setTaskStatus(
        emailStatus: TaskStatus,
        recoveryCodeStatus: TaskStatus,
        exportKeysStatus: TaskStatus,
        securityCenter: SecurityCenter,
    ) {

        // Reset visibility, will be corrected below:
        binding.progress.visibility = View.VISIBLE
        binding.progressBarSubtitle.visibility = View.VISIBLE
        binding.scSuccessBox.visibility = View.GONE
        binding.buttonExportKeysAgain.visibility = View.GONE

        // Configure global views:
        when (securityCenter.getLevel()) {

            SecurityLevel.ANON, SecurityLevel.SKIPPED_EMAIL_ANON -> {
                binding.progressBarSubtitle.setText(R.string.sc_task_header_0)
                binding.progress.colorRes = R.color.red
                binding.progress.progress = 0.10
            }

            SecurityLevel.EMAIL -> {
                binding.progressBarSubtitle.setText(R.string.sc_task_header_1)
                binding.progress.colorRes = R.color.green
                binding.progress.progress = 0.50
            }

            SecurityLevel.SKIPPED_EMAIL_RC -> {
                binding.progressBarSubtitle.setText(R.string.sc_task_header_2)
                binding.progress.colorRes = R.color.green
                binding.progress.progress = 0.50
            }

            SecurityLevel.EMAIL_AND_RC -> {
                binding.progressBarSubtitle.setText(R.string.sc_task_header_2)
                binding.progress.colorRes = R.color.green
                binding.progress.progress = 0.80
            }

            SecurityLevel.DONE, SecurityLevel.SKIPPED_EMAIL_DONE -> {
                binding.progress.visibility = View.GONE
                binding.progressBarSubtitle.visibility = View.GONE
                binding.scSuccessBox.visibility = View.VISIBLE
                binding.buttonExportKeysAgain.visibility = View.VISIBLE
            }
        }

        val hasOldExportKeysOnly = securityCenter.hasOldExportKeysOnly()

        // Configure individual cards:
        setEmailSetupStatus(emailStatus, securityCenter)
        setRecoveryCodeSetupStatus(recoveryCodeStatus, securityCenter)
        setExportKeysStatus(exportKeysStatus, hasOldExportKeysOnly)

        if (binding.taskExportKeys.status == MuunTaskCard.Status.DONE && hasOldExportKeysOnly) {
            binding.taskExportKeys.setOnClickListener { presenter.goToRecoveryTool() }
        }
    }

    private fun setEmailSetupStatus(status: TaskStatus, sc: SecurityCenter) {

        if (sc.emailSetupSkipped()) {
            binding.taskEmail.status = MuunTaskCard.Status.SKIPPED
            binding.tagEmailSkipped.visibility = View.VISIBLE
            binding.tagEmailSkipped.bringToFront()
            (binding.tagEmailSkipped.parent as View).invalidate()

        } else {
            binding.taskEmail.status = toCardStatus(status)
            binding.tagEmailSkipped.visibility = View.GONE
        }

        binding.taskEmail.title = when (binding.taskEmail.status) {
            MuunTaskCard.Status.DONE -> getString(R.string.task_email_done_title)
            MuunTaskCard.Status.ACTIVE -> getString(R.string.task_email_pending_title)
            MuunTaskCard.Status.SKIPPED -> getString(R.string.task_email_pending_title)
            else -> throw IllegalStateException("This should never happen!")
        }

        binding.taskEmail.body = when (binding.taskEmail.status) {
            MuunTaskCard.Status.DONE -> stringWithEmail(R.string.task_email_done_body, sc.email()!!)
            MuunTaskCard.Status.ACTIVE -> getString(R.string.task_email_pending_body)
            MuunTaskCard.Status.SKIPPED -> getString(R.string.task_email_skipped_body)
            else -> throw IllegalStateException("This should never happen!")
        }

        if (sc.emailSetupSkipped()) {
            binding.taskEmail.icon = getDrawable(R.drawable.ic_step_1_skipped)

        } else {
            binding.taskEmail.icon = getDrawable(
                when (status) {
                    TaskStatus.DONE -> R.drawable.ic_check
                    TaskStatus.PENDING -> R.drawable.ic_step_1_blue
                    TaskStatus.BLOCKED -> R.drawable.ic_step_1_gray
                }
            )
        }
    }

    private fun setRecoveryCodeSetupStatus(status: TaskStatus, securityCenter: SecurityCenter) {
        binding.taskRecoveryCode.status = toCardStatus(status)

        binding.taskRecoveryCode.title = when (status) {
            TaskStatus.DONE -> getString(R.string.task_rc_title_done)
            else -> getString(R.string.task_rc_title_pending)
        }

        binding.taskRecoveryCode.body = when (securityCenter.getLevel()) {
            SecurityLevel.ANON -> getString(R.string.task_rc_body_inactive)
            SecurityLevel.SKIPPED_EMAIL_ANON -> getString(R.string.task_rc_body_email_skipped)
            SecurityLevel.EMAIL -> getString(R.string.task_rc_body_pending)
            SecurityLevel.SKIPPED_EMAIL_RC -> getString(R.string.task_rc_body_done_email_skipped)
            SecurityLevel.SKIPPED_EMAIL_DONE -> getString(R.string.task_rc_body_done_email_skipped)
            SecurityLevel.EMAIL_AND_RC, SecurityLevel.DONE ->
                stringWithEmail(R.string.task_rc_body_done, securityCenter.email()!!)
        }

        binding.taskRecoveryCode.icon = getDrawable(
            when (status) {
                TaskStatus.DONE -> R.drawable.ic_check
                TaskStatus.PENDING -> R.drawable.ic_step_2_blue
                TaskStatus.BLOCKED -> R.drawable.ic_step_2_gray
            }
        )
    }

    private fun setExportKeysStatus(status: TaskStatus, hasOldExportKeysOnly: Boolean) {
        binding.taskExportKeys.status = toCardStatus(status)

        binding.taskExportKeys.title = getString(
            when (status) {
                TaskStatus.DONE -> {
                    if (hasOldExportKeysOnly) {
                        R.string.task_export_keys_done_title_old
                    } else {
                        R.string.task_export_keys_done_title
                    }
                }

                else -> R.string.task_export_keys_pending_title
            }
        )

        binding.taskExportKeys.body = StyledStringRes(
            requireContext(), when (status) {
                TaskStatus.DONE -> {
                    if (hasOldExportKeysOnly) {
                        R.string.task_export_keys_done_body_old
                    } else {
                        R.string.task_export_keys_done_body
                    }
                }

                else -> R.string.task_export_keys_pending_body
            }
        ).toCharSequence()

        binding.taskExportKeys.icon = getDrawable(
            when (status) {
                TaskStatus.DONE -> R.drawable.ic_check
                TaskStatus.PENDING -> R.drawable.ic_step_3_blue
                TaskStatus.BLOCKED -> R.drawable.ic_step_3_gray
            }
        )
    }

    private fun toCardStatus(status: TaskStatus) =
        when (status) {
            TaskStatus.BLOCKED -> MuunTaskCard.Status.INACTIVE
            TaskStatus.PENDING -> MuunTaskCard.Status.ACTIVE
            TaskStatus.DONE -> MuunTaskCard.Status.DONE
        }

    private fun stringWithEmail(@StringRes resId: Int, email: String) =
        StyledStringRes(requireContext(), resId)
            .toCharSequence(email)
}