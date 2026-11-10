package io.muun.apollo.data.preferences

import android.content.SharedPreferences

/**
 * In-memory SharedPreferences reproducing the framework semantics that matter to
 * RxSharedPreferences-backed repositories:
 * - listeners are notified synchronously (the framework calls them directly when on the main
 *   thread, which is where unit tests and the reactive consumers effectively run);
 * - writing a value equal to the stored one does not notify (SharedPreferencesImpl skips keys
 *   whose value didn't change);
 * - clear() notifies once with a null key (Android 11+), or once per removed key when
 *   [clearNotifiesEachKey] is set (Android 10 and older).
 */
class FakeSharedPreferences(
    private val clearNotifiesEachKey: Boolean = false,
) : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as String? ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as MutableSet<String>? ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        values[key] as Float? ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as Boolean? ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listeners.add(listener!!)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        listeners.remove(listener)
    }

    private fun notify(key: String?) {
        listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
    }

    private inner class Editor : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?) = put(key!!, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key!!, values)

        override fun putInt(key: String?, value: Int) = put(key!!, value)

        override fun putLong(key: String?, value: Long) = put(key!!, value)

        override fun putFloat(key: String?, value: Float) = put(key!!, value)

        override fun putBoolean(key: String?, value: Boolean) = put(key!!, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            removals.add(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            val changedKeys = mutableListOf<String>()

            if (clear) {
                val removed = values.keys.toList()
                values.clear()
                if (clearNotifiesEachKey) {
                    changedKeys.addAll(removed)
                } else {
                    notify(null)
                }
            }

            removals.forEach { key ->
                if (values.remove(key) != null || values.containsKey(key)) {
                    changedKeys.add(key)
                }
            }

            pending.forEach { (key, value) ->
                if (value == null) {
                    if (values.containsKey(key)) {
                        values.remove(key)
                        changedKeys.add(key)
                    }
                } else if (values[key] != value) {
                    values[key] = value
                    changedKeys.add(key)
                }
            }

            changedKeys.forEach(::notify)
        }

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }
    }
}
