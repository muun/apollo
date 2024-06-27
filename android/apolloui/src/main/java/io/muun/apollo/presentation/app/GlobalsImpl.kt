package io.muun.apollo.presentation.app

import android.os.Build
import io.muun.apollo.BuildConfig
import io.muun.apollo.data.external.Globals
import io.muun.common.bitcoinj.NetworkParametersHelper
import org.bitcoinj.core.NetworkParameters

class GlobalsImpl : Globals() {

    override val network: NetworkParameters = NetworkParametersHelper
        .getNetworkParametersFromName(BuildConfig.NETWORK_NAME)

    override val applicationId: String
        get() = BuildConfig.APPLICATION_ID

    override val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    override val buildType: String
        get() = BuildConfig.BUILD_TYPE

    override val oldBuildType: String
        get() = BuildConfig.OLD_BUILD_TYPE

    override val versionCode: Int
        get() = BuildConfig.VERSION_CODE

    override val versionName: String
        get() = BuildConfig.VERSION_NAME

    override val deviceName: String
        get() = Build.DEVICE

    override val deviceModel: String
        get() = Build.MODEL

    override val deviceManufacturer: String
        get() = Build.MANUFACTURER

    override val fingerprint: String
        get() = Build.FINGERPRINT

    override val hardware: String
        get() = Build.HARDWARE

    override val bootloader: String
        get() = Build.BOOTLOADER

    override val muunLinkHost: String
        get() = BuildConfig.MUUN_LINK_HOST

    override val verifyLinkPath: String
        get() = BuildConfig.VERIFY_LINK_PATH

    override val authorizeLinkPath: String
        get() = BuildConfig.AUTHORIZE_LINK_PATH

    override val confirmLinkPath: String
        get() = BuildConfig.CONFIRM_LINK_PATH

    override val rcLoginAuthorizePath: String
        get() = BuildConfig.RC_LOGIN_AUTHORIZE_LINK_PATH

    override val confirmAccountDeletionPath: String
        get() = BuildConfig.CONFIRM_ACCOUNT_DELETION_PATH

    override val lappUrl: String
        get() = BuildConfig.LAPP_URL
}