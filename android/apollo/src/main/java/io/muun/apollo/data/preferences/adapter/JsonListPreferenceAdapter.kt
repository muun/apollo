package io.muun.apollo.data.preferences.adapter

import android.content.SharedPreferences
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.serialization.SerializationUtils

class JsonListPreferenceAdapter<T>(private val valueClass: Class<T>)
    : Preference.Adapter<List<T>> {

    override fun get(key: String, preferences: SharedPreferences): List<T>? {
        val json = preferences.getString(key, null)
        if (json == null) {
            return null
        }

        return SerializationUtils.deserializeList(valueClass, json)
    }

    override fun set(k: String,
                     v: List<T>,
                     editor: SharedPreferences.Editor) {
        editor.putString(k, SerializationUtils.serializeList(valueClass, v))
    }
}