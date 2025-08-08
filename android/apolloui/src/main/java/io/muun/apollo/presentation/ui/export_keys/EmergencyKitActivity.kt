package io.muun.apollo.presentation.ui.export_keys

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ExportKeysActivityBinding
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.fragments.ek_intro.EmergencyKitIntroFragment
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveFragment
import io.muun.apollo.presentation.ui.fragments.ek_success.EmergencyKitSuccessFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.apollo.presentation.ui.fragments.loading.LoadingFragment
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation

class EmergencyKitActivity : SingleFragmentActivity<EmergencyKitPresenter>(), EmergencyKitView {

    companion object {
        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, EmergencyKitActivity::class.java)
        }
    }

    private val binding: ExportKeysActivityBinding
        get() = getBinding() as ExportKeysActivityBinding

    private val headerView: MuunHeader
        get() = binding.exportKeysHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.export_keys_activity

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ExportKeysActivityBinding::inflate
    }

    override fun getFragmentsContainer() =
        R.id.fragment_container

    override fun getHeader() =
        headerView

    override fun isPresenterPersistent() =
        true

    override fun initializeUi() {
        super.initializeUi()

        headerView.attachToActivity(this)
        refreshToolbar()
    }

    override fun refreshToolbar() {
        header.showTitle(R.string.ek_title)
        header.setNavigation(Navigation.BACK)
        header.setElevated(true)
    }

    override fun goToStep(step: EmergencyKitStep) {
        replaceFragment(createStepFragment(step), false)
    }

    override fun showSaveAbortDialog() {
        MuunDialog.Builder()
            .title(R.string.ek_abort_title)
            .message(R.string.ek_abort_body)
            .positiveButton(R.string.abort) { presenter.abortEmergencyKitFlow() }
            .negativeButton(R.string.cancel)
            .build()
            .let(this::showDialog)
    }

    private fun createStepFragment(step: EmergencyKitStep): Fragment {
        return when (step) {
            EmergencyKitStep.LOADING -> LoadingFragment.create(R.string.export_keys_preparing)
            EmergencyKitStep.INTRO -> EmergencyKitIntroFragment()
            EmergencyKitStep.SAVE -> EmergencyKitSaveFragment.createForNormalExport()
            EmergencyKitStep.VERIFY -> EmergencyKitVerifyFragment()
            EmergencyKitStep.VERIFY_HELP -> EmergencyKitVerifyHelpFragment()
            EmergencyKitStep.CLOUD_VERIFY -> EmergencyKitCloudVerifyFragment()
            EmergencyKitStep.SUCCESS -> EmergencyKitSuccessFragment()
            EmergencyKitStep.ERROR -> ErrorFragment.create(
                ErrorViewModel.Builder()
                    .loggingName(AnalyticsEvent.ERROR_TYPE.EMERGENCY_KIT_CHALLENGE_KEY_MIGRATION_ERROR)
                    .title(getString(R.string.export_keys_preparing_error_title))
                    .descriptionRes(R.string.export_keys_preparing_error_desc)
                    .build()
            )
        }
    }
}