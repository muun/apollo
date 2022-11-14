package io.muun.apollo.presentation.ui.taproot_setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.BaseFragment
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.fragments.ek_save.EmergencyKitSaveFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify.EmergencyKitVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_cloud.EmergencyKitCloudVerifyFragment
import io.muun.apollo.presentation.ui.fragments.ek_verify_help.EmergencyKitVerifyHelpFragment
import io.muun.apollo.presentation.ui.fragments.tr_intro.TaprootIntroFragment
import io.muun.apollo.presentation.ui.fragments.tr_success.TaprootSuccessFragment
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation

class TaprootSetupActivity: SingleFragmentActivity<TaprootSetupPresenter>(), TaprootSetupView {

    companion object {
        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, TaprootSetupActivity::class.java)
        }
    }

    @BindView(R.id.header)
    lateinit var headerView: MuunHeader

    override fun inject() {
        component.inject(this)
    }

    override fun isPresenterPersistent() =
        true

    override fun getLayoutResource() =
        R.layout.taproot_setup_activity

    override fun getFragmentsContainer() =
        R.id.fragment_container

    override fun getInitialFragment() =
        createStepFragment(TaprootSetupStep.INTRO)

    override fun getHeader() =
        headerView

    override fun initializeUi() {
        super.initializeUi()

        headerView.attachToActivity(this)
        header.setNavigation(Navigation.EXIT)
        header.hideTitle()
        header.setElevated(false)
    }

    override fun goToStep(step: TaprootSetupStep, args: Bundle) {
        if (step == TaprootSetupStep.INTRO) {
            header.setNavigation(Navigation.EXIT)
        } else {
            header.setNavigation(Navigation.BACK)
        }

        val fragment = createStepFragment(step)
        fragment.argumentsBundle.putAll(args)

        replaceFragment(fragment, false)
    }

    private fun createStepFragment(step: TaprootSetupStep): BaseFragment<*> {
        return when (step) {
            TaprootSetupStep.INTRO -> TaprootIntroFragment()
            TaprootSetupStep.SAVE -> EmergencyKitSaveFragment.createForUpdate()
            TaprootSetupStep.VERIFY -> EmergencyKitVerifyFragment()
            TaprootSetupStep.VERIFY_HELP -> EmergencyKitVerifyHelpFragment()
            TaprootSetupStep.CLOUD_VERIFY -> EmergencyKitCloudVerifyFragment()
            TaprootSetupStep.SUCCESS -> TaprootSuccessFragment()
        }
    }
}