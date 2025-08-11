package io.muun.apollo.data.os

import android.content.Context
import java.io.IOException
import java.lang.Boolean.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Configuration @Inject constructor(context: Context) {

    companion object {
        private const val CONFIG_FILE_NAME = "config.properties"
    }

    private val properties: Properties = Properties()

    /**
     * Constructor.
     */
    init {
        try {
            properties.load(context.resources.assets.open(CONFIG_FILE_NAME))
        } catch (e: IOException) {
            throw RuntimeException("Config file '$CONFIG_FILE_NAME' not found.", e)
        }
    }

    fun getLong(key: String): Long =
        properties.getProperty(key).toLong()

    fun getInt(key: String): Int =
        properties.getProperty(key).toInt()

    fun getBoolean(key: String): Boolean =
        parseBoolean(properties.getProperty(key))

    fun getString(key: String): String =
        properties.getProperty(key)
}