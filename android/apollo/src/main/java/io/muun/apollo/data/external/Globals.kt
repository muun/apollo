package io.muun.apollo.data.external

import org.bitcoinj.core.NetworkParameters

abstract class Globals {

    companion object {

        /**
         * This will be initialized in the UI application code, since it depends on Android build
         * configurations.
         */
        lateinit var INSTANCE: Globals
    }

    /**
     * Get the Application Id (previously package name) of the app. Identifies the app on the
     * device, its unique in the Google Play store.
     */
    abstract val applicationId: String

    /**
     * Get whether the current build a debuggable build.
     */
    abstract val isDebugBuild: Boolean

    /**
     * Get the build type of the current build.
     */
    abstract val buildType: String

    /**
     * Get the legacy build type of the current build. It is now deprecated in favour of
     * [Globals.buildType].
     */
    abstract val oldBuildType: String

    /**
     * Get the version code of the current build (e.g 1004).
     */
    abstract val versionCode: Int

    /**
     * Get the version name of the current build (e.g 50.4).
     */
    abstract val versionName: String

    /**
     * Get the version name of the current build (e.g 50.4).
     */
    abstract val deviceName: String

    /**
     * Get the model name of the device where app is running.
     */
    abstract val deviceModel: String

    /**
     * Get the manufacturer name of the device where app is running.
     */
    abstract val deviceManufacturer: String

    /**
     * Get the fingerprint of the device where app is running.
     */
    abstract val fingerprint: String

    /**
     * Get the hardware name of the device where app is running.
     */
    abstract val hardware: String

    /**
     * Get the bootloader name of the device where app is running.
     */
    abstract val bootloader: String

    /**
     * Get the bitcoin network specs/parameters of the network this build is using.
     */
    abstract val network: NetworkParameters

    /**
     * Get the hostname of this app's deeplink.
     */
    abstract val muunLinkHost: String

    /**
     * Get the path of this app's "Verify" deeplink.
     */
    abstract val verifyLinkPath: String

    /**
     * Get the path of this app's "Authorize" deeplink.
     */
    abstract val authorizeLinkPath: String

    /**
     * Get the path of this app's "Confirm" deeplink.
     */
    abstract val confirmLinkPath: String

    /**
     * Get the path of this app's "Authorize RC Login" deeplink.
     */
    abstract val rcLoginAuthorizePath: String

    /**
     * Get Lapp's URL.
     */
    abstract val lappUrl: String

    /**
     * Get whether the current build is a release build.
     */
    val isReleaseBuild: Boolean
        get() = buildType == "release"

}