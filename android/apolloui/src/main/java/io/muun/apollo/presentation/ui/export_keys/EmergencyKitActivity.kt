package io.muun.apollo.presentation.ui.export_keys

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.fragments.ek_intro.EmergencyKitIntroFragment
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveFragment
import io.muun.apollo.presentation.ui.fragments.ek_success.EmergencyKitSuccessFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpFragment
import io.muun.apollo.presentation.ui.fragments.error.ErrorFragment.Companion.create
import io.muun.apollo.presentation.ui.fragments.error.ErrorViewModel
import io.muun.apollo.presentation.ui.fragments.loading.LoadingFragment.Companion.create
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation

class EmergencyKitActivity: SingleFragmentActivity<EmergencyKitPresenter?>(), EmergencyKitView {

    companion object {
        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, EmergencyKitActivity::class.java)
        }
    }

    @BindView(R.id.export_keys_header)
    lateinit var headerView: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource() =
        R.layout.export_keys_activity

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


    override fun goToStep(step: EmergencyKeysStep) {
        replaceFragment(createStepFragment(step), false)
    }

    private fun createStepFragment(step: EmergencyKeysStep): Fragment {
        return when (step) {
            EmergencyKeysStep.LOADING -> create(R.string.export_keys_preparing)
            EmergencyKeysStep.INTRO -> EmergencyKitIntroFragment()
            EmergencyKeysStep.SAVE -> EmergencyKitSaveFragment()
            EmergencyKeysStep.VERIFY -> EmergencyKitVerifyFragment()
            EmergencyKeysStep.VERIFY_HELP -> EmergencyKitVerifyHelpFragment()
            EmergencyKeysStep.CLOUD_VERIFY -> EmergencyKitCloudVerifyFragment()
            EmergencyKeysStep.SUCCESS -> EmergencyKitSuccessFragment()
            EmergencyKeysStep.ERROR -> create(ErrorViewModel.Builder()
                .loggingName(AnalyticsEvent.ERROR_TYPE.EMERGENCY_KIT_CHALLENGE_KEY_MIGRATION_ERROR)
                .title(getString(R.string.export_keys_preparing_error_title))
                .descriptionRes(R.string.export_keys_preparing_error_desc)
                .build()
            )
        }
    }
}