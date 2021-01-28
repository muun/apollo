package io.muun.apollo.domain.model

data class UserPreferences(
        val strictMode: Boolean,
        val seenNewHome: Boolean
) {
    fun toJson(): io.muun.common.model.UserPreferences {
        return io.muun.common.model.UserPreferences(strictMode, seenNewHome)
    }

    companion object {

        @JvmStatic
        fun fromJson(prefs: io.muun.common.model.UserPreferences): UserPreferences {
            return UserPreferences(prefs.receiveStrictMode, prefs.seenNewHome)
        }
    }
}