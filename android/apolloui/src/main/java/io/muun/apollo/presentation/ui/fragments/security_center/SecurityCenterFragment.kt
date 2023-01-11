package io.muun.apollo.presentation.ui.fragments.security_center

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.SecurityCenter
import io.muun.apollo.domain.model.SecurityLevel
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterView.TaskStatus
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.getDrawable
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation
import io.muun.apollo.presentation.ui.view.MuunProgressBar
import io.muun.apollo.presentation.ui.view.MuunTaskCard

class SecurityCenterFragment : SingleFragment<SecurityCenterPresenter>(), SecurityCenterView {

    @BindView(R.id.tag_email_skipped)
    lateinit var emailSkippedTag: View

    @BindView(R.id.task_email)
    lateinit var emailTaskCard: MuunTaskCard

    @BindView(R.id.task_recovery_code)
    lateinit var recoveryCodeTaskCard: MuunTaskCard

    @BindView(R.id.task_export_keys)
    lateinit var exportKeysTaskCard: MuunTaskCard

    @BindView(R.id.progress)
    lateinit var progressBar: MuunProgressBar

    @BindView(R.id.progress_bar_subtitle)
    lateinit var progressBarSubtitle: TextView

    @BindView(R.id.sc_success_box)
    lateinit var successBox: ViewGroup

    @BindView(R.id.button_export_keys_again)
    lateinit var exportAgainButton: View

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.fragment_security_center


    override fun initializeUi(view: View) {
        super.initializeUi(view)

        setUpHeader()
        setUpCards()
    }

    private fun setUpHeader() {
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
        emailTaskCard.setOnClickListener { presenter.goToEmailSetup() }
        recoveryCodeTaskCard.setOnClickListener { presenter.goToRecoveryCodeSetup() }
        exportKeysTaskCard.setOnClickListener { presenter.goToExportKeys() }
        exportAgainButton.setOnClickListener { presenter.goToExportKeys() }
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
        progressBar.visibility = View.VISIBLE
        progressBarSubtitle.visibility = View.VISIBLE
        successBox.visibility = View.GONE
        exportAgainButton.visibility = View.GONE

        // Configure global views:
        when (securityCenter.getLevel()) {

            SecurityLevel.ANON, SecurityLevel.SKIPPED_EMAIL_ANON -> {
                progressBarSubtitle.setText(R.string.sc_task_header_0)
                progressBar.colorRes = R.color.red
                progressBar.progress = 0.10
            }

            SecurityLevel.EMAIL -> {
                progressBarSubtitle.setText(R.string.sc_task_header_1)
                progressBar.colorRes = R.color.green
                progressBar.progress = 0.50
            }

            SecurityLevel.SKIPPED_EMAIL_RC -> {
                progressBarSubtitle.setText(R.string.sc_task_header_2)
                progressBar.colorRes = R.color.green
                progressBar.progress = 0.50
            }

            SecurityLevel.EMAIL_AND_RC -> {
                progressBarSubtitle.setText(R.string.sc_task_header_2)
                progressBar.colorRes = R.color.green
                progressBar.progress = 0.80
            }

            SecurityLevel.DONE, SecurityLevel.SKIPPED_EMAIL_DONE -> {
                progressBar.visibility = View.GONE
                progressBarSubtitle.visibility = View.GONE
                successBox.visibility = View.VISIBLE
                exportAgainButton.visibility = View.VISIBLE
            }
        }

        val hasOldExportKeysOnly = securityCenter.hasOldExportKeysOnly()

        // Configure individual cards:
        setEmailSetupStatus(emailStatus, securityCenter)
        setRecoveryCodeSetupStatus(recoveryCodeStatus, securityCenter)
        setExportKeysStatus(exportKeysStatus, hasOldExportKeysOnly)

        if (exportKeysTaskCard.status == MuunTaskCard.Status.DONE && hasOldExportKeysOnly) {
            exportKeysTaskCard.setOnClickListener { presenter.goToRecoveryTool() }
        }
    }

    private fun setEmailSetupStatus(status: TaskStatus, sc: SecurityCenter) {

        if (sc.emailSetupSkipped()) {
            emailTaskCard.status = MuunTaskCard.Status.SKIPPED
            emailSkippedTag.visibility = View.VISIBLE
            if (!OS.supportsTranslateZ()) { // we can't use translateZ in api levels below 21 :(
                emailSkippedTag.bringToFront()
                (emailSkippedTag.parent as View).invalidate()
            }

        } else {
            emailTaskCard.status = toCardStatus(status)
            emailSkippedTag.visibility = View.GONE
        }

        emailTaskCard.title = when (emailTaskCard.status) {
            MuunTaskCard.Status.DONE -> getString(R.string.task_email_done_title)
            MuunTaskCard.Status.ACTIVE -> getString(R.string.task_email_pending_title)
            MuunTaskCard.Status.SKIPPED -> getString(R.string.task_email_pending_title)
            else -> throw IllegalStateException("This should never happen!")
        }

        emailTaskCard.body = when (emailTaskCard.status) {
            MuunTaskCard.Status.DONE -> stringWithEmail(R.string.task_email_done_body, sc.email()!!)
            MuunTaskCard.Status.ACTIVE -> getString(R.string.task_email_pending_body)
            MuunTaskCard.Status.SKIPPED -> getString(R.string.task_email_skipped_body)
            else -> throw IllegalStateException("This should never happen!")
        }

        if (sc.emailSetupSkipped()) {
            emailTaskCard.icon = getDrawable(R.drawable.ic_step_1_skipped)

        } else {
            emailTaskCard.icon = getDrawable(when (status) {
                TaskStatus.DONE -> R.drawable.ic_check
                TaskStatus.PENDING -> R.drawable.ic_step_1_blue
                TaskStatus.BLOCKED -> R.drawable.ic_step_1_gray
            })
        }
    }

    private fun setRecoveryCodeSetupStatus(status: TaskStatus, securityCenter: SecurityCenter) {
        recoveryCodeTaskCard.status = toCardStatus(status)

        recoveryCodeTaskCard.title = when (status) {
            TaskStatus.DONE -> getString(R.string.task_rc_title_done)
            else -> getString(R.string.task_rc_title_pending)
        }

        recoveryCodeTaskCard.body = when (securityCenter.getLevel()) {
            SecurityLevel.ANON -> getString(R.string.task_rc_body_inactive)
            SecurityLevel.SKIPPED_EMAIL_ANON -> getString(R.string.task_rc_body_email_skipped)
            SecurityLevel.EMAIL -> getString(R.string.task_rc_body_pending)
            SecurityLevel.SKIPPED_EMAIL_RC -> getString(R.string.task_rc_body_done_email_skipped)
            SecurityLevel.SKIPPED_EMAIL_DONE -> getString(R.string.task_rc_body_done_email_skipped)
            SecurityLevel.EMAIL_AND_RC, SecurityLevel.DONE ->
                stringWithEmail(R.string.task_rc_body_done, securityCenter.email()!!)
        }

        recoveryCodeTaskCard.icon = getDrawable(when (status) {
            TaskStatus.DONE -> R.drawable.ic_check
            TaskStatus.PENDING -> R.drawable.ic_step_2_blue
            TaskStatus.BLOCKED -> R.drawable.ic_step_2_gray
        })
    }

    private fun setExportKeysStatus(status: TaskStatus, hasOldExportKeysOnly: Boolean) {
        exportKeysTaskCard.status = toCardStatus(status)

        exportKeysTaskCard.title = getString(when (status) {
            TaskStatus.DONE -> {
                if (hasOldExportKeysOnly) {
                    R.string.task_export_keys_done_title_old
                } else {
                    R.string.task_export_keys_done_title
                }
            }
            else -> R.string.task_export_keys_pending_title
        })

        exportKeysTaskCard.body = StyledStringRes(requireContext(), when (status) {
            TaskStatus.DONE -> {
                if (hasOldExportKeysOnly) {
                    R.string.task_export_keys_done_body_old
                } else {
                    R.string.task_export_keys_done_body
                }
            }
            else -> R.string.task_export_keys_pending_body
        }).toCharSequence()

        exportKeysTaskCard.icon = getDrawable(when (status) {
            TaskStatus.DONE -> R.drawable.ic_check
            TaskStatus.PENDING -> R.drawable.ic_step_3_blue
            TaskStatus.BLOCKED -> R.drawable.ic_step_3_gray
        })
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