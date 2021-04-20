package io.muun.apollo.data.preferences.adapter

import android.content.SharedPreferences
import io.muun.apollo.data.preferences.rx.Preference

class DoublePreferenceAdapter private constructor() : Preference.Adapter<Double> {

    companion object {
        val INSTANCE: DoublePreferenceAdapter = DoublePreferenceAdapter()
    }

    override fun get(key: String, preferences: SharedPreferences): Double? {
        val maybeDouble = preferences.getString(key, null)

        if (maybeDouble == null) {
            return null
        }

        return maybeDouble.toDouble()
    }

    override fun set(key: String, value: Double, editor: SharedPreferences.Editor) {
        editor.putString(key, value.toString())
    }
}