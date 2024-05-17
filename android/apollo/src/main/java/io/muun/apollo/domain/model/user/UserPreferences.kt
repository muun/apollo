package io.muun.apollo.domain.model.user

import io.muun.common.model.ReceiveFormatPreference

data class UserPreferences(
    val strictMode: Boolean,
    val seenNewHome: Boolean,
    val seenLnurlFirstTime: Boolean,
    val defaultAddressType: String,
    val skippedEmailSetup: Boolean,
    val receivePreference: ReceiveFormatPreference,
    val allowMultiSession: Boolean,
) {
    fun toJson(): io.muun.common.model.UserPreferences {
        return io.muun.common.model.UserPreferences(
            strictMode,
            seenNewHome,
            seenLnurlFirstTime,
            defaultAddressType,
            false,
            skippedEmailSetup,
            receivePreference,
            allowMultiSession
        )
    }

    companion object {

        @JvmStatic
        fun fromJson(prefs: io.muun.common.model.UserPreferences): UserPreferences {
            return UserPreferences(
                prefs.receiveStrictMode,
                prefs.seenNewHome,
                prefs.seenLnurlFirstTime,
                prefs.defaultAddressType,
                prefs.skippedEmailSetup,
                prefs.receiveFormatPreference,
                prefs.allowMultiSession
            )
        }
    }
}