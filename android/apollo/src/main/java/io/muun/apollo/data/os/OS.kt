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

    fun supportsImageDecoderApi(): Boolean =
        isAndroidPOrNewer()

    /**
     * Whether this OS supports telephonyManager#getPhoneCount(), which was introduced in
     * M-6-23, and deprecated in R-11-30 in favour of telephonyManager#getActiveModemCount().
     */
    fun supportsGetPhoneCount(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports telephonyManager#getSimState(int), which was introduced in
     * 0-8-26, and deprecated in R-11-30 in favour of telephonyManager#getActiveModemCount().
     */
    fun supportsGetSimStateWithSlotIndex(): Boolean =
        isAndroidOOrNewer()

    /**
     * Whether this OS supports telephonyManager#getActiveModemCount(), which was introduced in
     * R-11-30.
     */
    fun supportsGetActiveModemCount(): Boolean =
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

    fun supportsUserCreationTime(): Boolean =
        isAndroidMOrNewer()

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
     * Whether this OS version is EXACTLY Q-10-29.
     */
    private fun isAndroidQExactly() =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

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
}