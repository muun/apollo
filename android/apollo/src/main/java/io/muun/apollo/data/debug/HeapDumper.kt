package io.muun.apollo.data.debug

import android.app.Application
import android.os.Debug
import io.muun.apollo.data.external.Globals
import timber.log.Timber
import java.io.IOException

/**
 * Utility class to help the debug and analysis of OutOfMemoryError (OOM) and memory leaks. Meant
 * to be used ONLY for DEBUG builds.
 */
object HeapDumper {

    private lateinit var application: Application

    //uncaught exceptions
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @JvmStatic
    fun init(application: Application) {
        if (Globals.INSTANCE.isDebugBuild) {
            this.application = application
            defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(customUncaughtExceptionHandler)
        }
    }

    // OOM handler listener
    private val customUncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, ex ->
        if (ex is OutOfMemoryError) {
            try {
                Timber.i("DumpHprofData: Starting...")
                Debug.dumpHprofData("${application.filesDir.absolutePath}/apollo-oom-dump.hprof")
            } catch (e: IOException) {
                Timber.i("DumpHprofData: Error: $e. Cause: $ex")
            }
            Timber.i("DumpHprofData: Success")
        }

        //call the default exception handler
        defaultUncaughtExceptionHandler?.uncaughtException(thread, ex)
    }
}