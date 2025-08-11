package io.muun.apollo.data.os

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting

/**
 * Utility object to group OS related queries or operations like checking supported features
 * based on Android version.
 *
 * Note: this file docs use the following convention to refer to android OS versions:
 * <versionLetter>-<versionMajorNumber>-<apiLevel>. Some examples:
 * - M-6-23 -> Android Marshmallow, Android 6, Api Level 23
 * - N-7-24 -> Android Nougat, Android 7, Api level 24
 * - P-9-28 -> Android Pie, Android 9, Api level 28
 */
object OS {

    /**
     * Whether this OS supports Hardware-backed Keystore
     * (see: https://source.android.com/docs/security/features/keystore), which was introduced in
     * M-6-23.
     */
    @JvmStatic
    fun supportsHardwareBackedKeystore(): Boolean =
        isAndroidMOrNewer()

    fun supportsInstallSourceInfo(): Boolean =
        isAndroidROrNewer()

    fun supportsImageDecoderApi(): Boolean =
        isAndroidPOrNewer()

    /**
     * Whether this OS supports MediaDrm#close(), which was introduced in
     * P-9-28, deprecating MediaDrm#close().
     */
    fun supportsMediaDrmClose(): Boolean =
        isAndroidPOrNewer()

    /**
     * Whether this OS supports telephonyManager#getPhoneCount(), which was introduced in
     * M-6-23, and deprecated in R-11-30 in favour of telephonyManager#getActiveModemCount().
     */
    fun supportsGetPhoneCount(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports telephonyManager#getSimState(int), which was introduced in
     * O-8-26, and deprecated in R-11-30 in favour of telephonyManager#getActiveModemCount().
     */
    fun supportsGetSimStateWithSlotIndex(): Boolean =
        isAndroidOOrNewer()

    /**
     * Whether this OS supports telephonyManager#isDataEnabled(), which was introduced in O-8-26
     */
    fun supportsIsDataEnabled(): Boolean =
        isAndroidOOrNewer()

    /**
     * Whether this OS supports telephonyManager#getActiveModemCount(), which was introduced in
     * R-11-30.
     */
    fun supportsGetActiveModemCount(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports telephonyManager#getNetworkCountryIso(int slotIndex), which was introduced in
     * R-11-30.
     */
    fun supportsGetNetworkCountryIsoWithSlotIndex(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports Pending Intent mutability flags, which where introduced in M-6-23
     * and are required starting in S-12-31.
     *
     * NOTE: this method has a sibling method in the sibling object in presentation layer with the
     * same exact check.
     *
     * See:
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://stackoverflow.com/questions/70894168/targeting-s-version-31-and-above-requires-that-one-of-flag-immutable-or-flag
     */
    fun supportsPendingIntentMutabilityFlags(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether we should apply our "small delay" Keystore workaround. Apparently in some Android
     * versions when you encounter some Keystore errors you can solve them by waiting a bit and just
     * retrying. We currently apply this workaround for EXACTLY Q-10-29.
     *
     * See:
     * - CryptographyWrapper class
     * - https://issuetracker.google.com/issues/147384380.
     */
    @JvmStatic
    fun shouldApplyKeystoreSmallDelayWorkaround(): Boolean =
        isAndroidQExactly()

    /**
     * Whether this OS supports obtaining the component that handled a share Intent. This is
     * translated to the support of the EXTRA_CHOSEN_COMPONENT Intent Extra, which was added in
     * LMR1-5.1-22.
     */
    fun supportsExtraChosenComponentIntentExtra(): Boolean =
        isAndroidLMr1OrNewer()

    /**
     * Whether this OS supports new Webview's PrintDocumentAdapter factory method, which was added
     * in M-6-23.
     */
    fun supportsNewPrintDocumentAdapter(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports KeyPermanentlyInvalidatedException a special Android Keystore
     * exception, subclass of InvalidKeyException, which was added in M-6-23. See:
     * https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException
     */
    @VisibleForTesting
    fun supportsKeyPermanentlyInvalidatedException(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports PowerManager#getBatteryDischargePrediction(), which was added
     * in S-12-31.
     */
    fun supportsBatteryDischargePrediction(): Boolean =
        isAndroidSOrNewer()

    /**
     * Whether this OS supports UserManager#getUserCreationTime(UserHandle), which was added
     * in M-6-23.
     */
    fun supportsUserCreationTime(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports MediaDrm#getSupportedCryptoSchemes(), which was introduced in
     * R-11-30.
     */
    fun supportsGetSupportedCryptoSchemes(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports Build.VERSION.SECURITY_PATCH, which was introduced in M-6-23.
     */
    fun supportsBuildVersionSecurityPatch(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports Build.VERSION.BASE_OS, which was introduced in M-6-23.
     */
    fun supportsBuildVersionBaseOs(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports Build.SUPPORTED_ABIS, which was introduced in L-5.0-21.
     */
    fun supportsBuildSupportedAbis(): Boolean =
        isAndroidLOrNewer()

    /**
     * Whether this OS supports Feature Picture and Picture, which was introduced in N-7-24.
     */
    fun supportsPIP(): Boolean =
        isAndroidNOrNewer()

    /**
     * Whether this OS device has biometric hardware to detect a fingerprint, which was introduced
     * in M-6-23.
     */
    fun supportsDactylogram(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports Feature PC, which was introduced in O-8.1-27.
     */
    fun supportsFeaturePC(): Boolean =
        isAndroidOMr1OrNewer()

    /**
     * Whether this OS supports PackageManager.GET_SIGNING_CERTIFICATES, which was introduced
     * in P-9-28.
     */
    fun supportsGetSigningCerts(): Boolean =
        isAndroidPOrNewer()


    /**
     * Whether this OS supports Settings.Global.BOOT_COUNT, which was introduced in N-7-24.
     */
    fun supportsBootCountSetting(): Boolean =
        isAndroidNOrNewer()

    /**
     * Whether this OS supports ConnectivityManager#getActiveNetwork(), which was introduced
     * in M-6-23.
     */
    fun supportsActiveNetwork(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports RouteInfo#hasGateway(), which was introduced
     * in Q-10-29.
     */
    fun supportsRouteHasGateway(): Boolean =
        isAndroidQOrNewer()

    /**
     * Whether this OS supports ConnectivityManager#getNetworkCapabilities, which was introduced
     * in L-5.0-21.
     */
    fun supportsNetworkCapabilities(): Boolean =
        isAndroidLOrNewer()

    /**
     * Whether this OS supports Build.SUPPORTED_ABIS, which was introduced in L-5-21.
     */
    fun supportsSupportedAbis(): Boolean =
        isAndroidLOrNewer()

    /**
     * Whether this OS supports Calendar#calendarType, which was introduced in O-8-26.
     */
    fun supportsCalendarType(): Boolean =
        isAndroidOOrNewer()

    /**
     * Whether this OS supports {@link android.security.KeyStoreException} public methods, which
     * were introduced in T-13-33.
     */
    fun supportsKeystoreExceptionPublicMethods(): Boolean =
        isAndroidTiramisuOrNewer()

    /**
     * Whether this OS supports {@link android.nfc.NfcAntennaInfo.getAvailableNfcAntennas}, which
     * was introduced in U-14-34.
     */
    fun supportsAvailableNfcAntennas(): Boolean =
        isAndroidUpsideDownCakeOrNewer()

    /**
     * Whether this OS supports {@link android.app.ActivityManager.isBackgroundRestricted}, which
     * was introduced in P-9-28.
     */
    fun supportsIsBackgroundRestricted(): Boolean =
        isAndroidPOrNewer()

    /**
     * Whether this OS supports {@link android.app.ActivityManager.isRunningInUserTestHarness},
     * which was introduced in Q-10-29.
     */
    fun supportsIsRunningInUserTestHarness(): Boolean =
        isAndroidQOrNewer()

    /**
     * Whether this OS supports {@link android.app.ActivityManager.isLowMemoryKillReportSupported},
     * which was introduced in R-11-30.
     */
    fun supportsLowMemoryKillReport(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports {@link android.app.ActivityManager.getHistoricalProcessExitReasons},
     * which was introduced in R-11-30.
     */
    fun supportsgetHistoricalProcessExitReasons(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports File#readAttributes, which was added in O-8-26.
     */
    fun supportsReadFileAttributes(): Boolean =
        isAndroidOOrNewer()


    fun supportsHardwareAddresses(): Boolean =
        isAndroidQOrOlder()


    // PRIVATE STUFF:

    /**
     * Whether this OS version is P-9-28 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    private fun isAndroidPOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Whether this OS version is M-6-23 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    private fun isAndroidMOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /**
     * Whether this OS version is S-12-31 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private fun isAndroidSOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Whether this OS version is Q-10-29 or older.
     */
    private fun isAndroidQOrOlder() =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    /**
     * Whether this OS version is EXACTLY Q-10-29.
     */
    private fun isAndroidQExactly() =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

    /**
     * Whether this OS version is Q-10-29 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun isAndroidQOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Whether this OS version is L-5.0-21 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP)
    private fun isAndroidLOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    /**
     * Whether this OS version is LMR1-5.1-22 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun isAndroidLMr1OrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

    /**
     * Whether this OS version is O-8-26 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private fun isAndroidOOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Whether this OS version is R-11-30 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun isAndroidROrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether this OS version is N-7-24 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    private fun isAndroidNOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Whether this OS version is O-8.1-27 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    private fun isAndroidOMr1OrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    /**
     * Whether this OS version is T-13-33 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isAndroidTiramisuOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether this OS version is U-14-34 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun isAndroidUpsideDownCakeOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}