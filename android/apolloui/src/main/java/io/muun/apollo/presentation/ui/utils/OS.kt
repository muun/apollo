package io.muun.apollo.presentation.ui.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Utility object to group OS related queries or operations like checking supported features
 * based on Android version.
 *
 * This is a "sibling" object to the its namesake in our data module. We decided to have a separate
 * object in our presentation layer to avoid breaking CLEAN architecture (presentation accessing
 * data directly) or tightly coupling domain layer to data (domain knowing android version
 * specifics).
 *
 * Note: this file docs use the following convention to refer to android OS versions:
 * <versionLetter>-<versionMajorNumber>-<apiLevel>. Some examples:
 * - M-6-23 -> Android Marshmallow, Android 6, Api Level 23
 * - N-7-24 -> Android Nougat, Android 7, Api level 24
 * - P-9-28 -> Android Pie, Android 9, Api level 28
 */
object OS {

    /**
     * Whether this OS supports Clipboard Access notification, which was added in S-12-31.
     */
    @JvmStatic
    fun supportsClipboardAccessNotification(): Boolean =
        isAndroidSOrNewer()

    /**
     * Whether this OS supports espresso Toast detection, which stopped working in S-12-31.
     * See: https://github.com/android/android-test/issues/803.
     */
    @JvmStatic
    fun supportsEspressoToastDetection(): Boolean =
        !isAndroidSOrNewer()

    /**
     * Whether this OS supports Notification runtime permission, which was added in Tiramisu-13-33.
     */
    @JvmStatic
    fun supportsNotificationRuntimePermission(): Boolean =
        isAndroidTiramisuOrNewer()

    /**
     * Whether this OS supports querying for App Standby Buckets, support for which was added in
     * P-9-28.
     */
    fun supportsStandByBuckets(): Boolean =
        isAndroidPOrNewer()

    /**
     * Whether this OS REQUIRES Pending Intent mutability flags, which where introduced in M-6-23
     * and are required starting in S-12-31.
     *
     * See:
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://stackoverflow.com/questions/70894168/targeting-s-version-31-and-above-requires-that-one-of-flag-immutable-or-flag
     */
    fun requiresPendingIntentMutabilityFlags(): Boolean =
        isAndroidSOrNewer()

    /**
     * Whether this OS supports Pending Intent mutability flags, which where introduced in M-6-23
     * and are required starting in S-12-31.
     *
     * NOTE: this method has a sibling method in the sibling object in data layer with the same
     * exact check.
     *
     * See:
     * - https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability
     * - https://developer.android.com/guide/components/intents-filters#DeclareMutabilityPendingIntent
     * - https://stackoverflow.com/questions/70894168/targeting-s-version-31-and-above-requires-that-one-of-flag-immutable-or-flag
     */
    fun supportsPendingIntentMutabilityFlags(): Boolean =
        isAndroidMOrNewer()

    /**
     * Whether this OS supports NotificationChannel, which is new class and is not present in the
     * support library. Support started in O-8-26.
     */
    fun supportsNotificationChannel(): Boolean =
        isAndroidOOrNewer()

    /**
     * We'll define that an Android OS version supports Dark Mode if it does so in a publicly
     * accessible way (aka there's a system config accessible for all users). Android P aka 9
     * (api 28) does support Dark Mode though hidden, via Developer Options. We'll take that as
     * a system that does NOT support Dark Mode as most users won't ever find how to display the
     * developer options and/or find that Dark Mode config in there.
     */
    fun supportsDarkMode(): Boolean {
        return supportsDarkModePublicly()
    }

    /**
     * Whether we should apply a special treatment for string resources in our UI tests. This is
     * apparently needed starting from N-7-24.
     */
    fun shouldNormalizeTextForUiTests(): Boolean =
        isAndroidNOrNewer()

    /**
     * Whether we should use `resources.configuration.locales` instead of deprecated
     * `resources.configuration.locale` N-7-24.
     */
    fun supportsLocales(): Boolean =
        isAndroidNOrNewer()

    /**
     * Whether this OS has edge-to-edge support (insets), which was added in R-11-30.
     */
    @JvmStatic
    fun supportsEdgeToEdge(): Boolean =
        isAndroidROrNewer()

    /**
     * Whether this OS supports Process's elapsed realtime
     */
    fun supportsProcessStartTimestamps(): Boolean =
        isAndroidNOrNewer()

    /**
     * Whether this OS is affected by a bug in floating ViewTreeObserver not being merged into
     * real ViewTreeObserver.
     *
     * @see https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3
     * @see https://github.com/firebase/firebase-android-sdk/blob/main/firebase-perf/src/main/java/com/google/firebase/perf/util/FirstDrawDoneListener.java#L42
     */
    fun affectedByFloatingViewTreeObserverNotMergedBug(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O

    // PRIVATE STUFF:

    /**
     * We'll define that an Android OS version supports Dark Mode if it does so in a publicly
     * accessible way (aka there's a system config accessible for all users). Android P aka 9
     * (api 28) does support Dark Mode though hidden, via Developer Options. We'll take that as
     * a system that does NOT support Dark Mode as most users won't ever find how to display the
     * developer options and/or find that Dark Mode config in there.
     */
    private fun supportsDarkModePublicly(): Boolean {
        return isAndroidQOrNewer()
    }

    // PRIVATE STUFF:

    /**
     * Whether this OS version is M-6-23 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    private fun isAndroidMOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /**
     * Whether this OS version is N-7-24 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    private fun isAndroidNOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Whether this OS version is O-8-26 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private fun isAndroidOOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * Whether this OS version is P-9-28 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    private fun isAndroidPOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P


    /**
     * Whether this OS version is Q-10-29 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun isAndroidQOrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Whether this OS version is R-11-30 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun isAndroidROrNewer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Whether this OS version is S-12-31 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private fun isAndroidSOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Whether this OS version is Tiramisu-13-33 or newer.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isAndroidTiramisuOrNewer() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

}