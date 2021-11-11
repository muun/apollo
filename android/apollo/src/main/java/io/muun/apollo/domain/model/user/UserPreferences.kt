package io.muun.apollo.domain.model.user

data class UserPreferences(
        val strictMode: Boolean,
        val seenNewHome: Boolean,
        val seenLnurlFirstTime: Boolean,
        var defaultAddressType: String
) {
    fun toJson(): io.muun.common.model.UserPreferences {
        return io.muun.common.model.UserPreferences(
                strictMode,
                seenNewHome,
                seenLnurlFirstTime,
                defaultAddressType
        )
    }

    companion object {

        @JvmStatic
        fun fromJson(prefs: io.muun.common.model.UserPreferences): UserPreferences {
            return UserPreferences(
                    prefs.receiveStrictMode,
                    prefs.seenNewHome,
                    prefs.seenLnurlFirstTime,
                    prefs.defaultAddressType
            )
        }
    }
}