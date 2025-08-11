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
     * Get whether the current build is a debuggable build.
     */
    abstract val isDebug: Boolean

    /**
     * Get whether the current build a production build.
     * NOTE: dogfood builds are production builds.
     */
    abstract val isProduction: Boolean

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
     * Get the flavor of the current build.
     */
    abstract val flavor: String

    /**
     * Get the git commit of the current build.
     */
    abstract val commit: String

    /**
     * Get the git branch of the current build.
     */
    abstract val branch: String

    /**
     * Get the version code of the current build (e.g 1004).
     */
    abstract val versionCode: Int

    /**
     * Get the version name of the current build (e.g 50.4).
     */
    abstract val versionName: String

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
     * Get the path of this app's "Confirm Account Deletion" deeplink.
     */
    abstract val confirmAccountDeletionPath: String

    /**
     * Get Lapp's URL.
     */
    abstract val lappUrl: String

    /**
     * Get whether the current build is a release build.
     */
    val isRelease: Boolean
        get() = buildType == "release"

    /**
     * Get whether the current build is a dogfood build.
     */
    val isDogfood: Boolean
        get() = flavor == "dogfood"

    /**
     * Get whether the current build is a CI build.
     */
    val isCI: Boolean
        get() = oldBuildType == "regtestDebug"

}