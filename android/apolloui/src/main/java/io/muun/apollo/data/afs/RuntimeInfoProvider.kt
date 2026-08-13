package io.muun.apollo.data.afs

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaDrm
import android.os.Handler
import android.os.Process
import io.muun.apollo.data.os.OS
import io.muun.apollo.data.os.TorHelper
import java.io.File
import java.io.FileInputStream

private const val BASE_APPLICATION_ID = "io.muun.apollo"

class RuntimeInfoProvider(private val context: Context) {

    companion object {
        private val TRUSTED_APP_PATH_PREFIXES = listOf(
            BASE_APPLICATION_ID,
            "com.google.android.",
        )
        private val RUNTIME_MAP_PATH = TorHelper.process("/cebp/frys/zncf")
        private const val MAX_NATIVE_LIB_SCAN_BYTES = 4 * 1024 * 1024
    }

    val extraStackElements: List<String>
        get() {
            val additionalElements = mutableListOf<String>()
            val currentTrace = Thread.currentThread().stackTrace

            val expectedElements = listOf(
                BASE_APPLICATION_ID,
                "dalvik.",
                "java.",
                "okhttp3.",
                "retrofit2.",
                "rx.",
                "android.",
                "com.android.",
                "kotlin.",
                "androidx.",
                "com.google.android."
            )

            for (element in currentTrace) {
                val className = element.className
                val isExternalSource = expectedElements.none { className.startsWith(it) }
                if (isExternalSource && !additionalElements.contains(className)) {
                    additionalElements.add(className)
                }
            }
            return additionalElements
        }

    val uidSharedStatus: Int
        get() {
            return try {
                val uid = Process.myUid()
                val packages = context.packageManager.getPackagesForUid(uid)
                    ?: return Constants.INT_ABSENT
                if (packages.any { it != context.packageName }) {
                    Constants.INT_PRESENT
                } else {
                    Constants.INT_ABSENT
                }
            } catch (e: Exception) {
                Constants.INT_EXCEPTION
            }
        }

    val externalPackages: List<String>
        get() {
            return try {
                File(RUNTIME_MAP_PATH).useLines { lines ->
                    lines
                        .map { line -> extractPathFromMapsLine(line) }
                        .mapNotNull { path -> extractForeignAppDirectory(path) }
                        .distinct()
                        .toList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val appOpsPackageName: String by lazy {
        invokeContextMethod("getOpPackageName")
    }

    val appBasePackageName: String by lazy {
        invokeContextMethod("getBasePackageName")
    }

    @get:SuppressLint("PrivateApi")
    val restrictiveSdkStatus: Int
        get() {
            if (!OS.hasNonSdkInterfacesRestrictions()) {
                return Constants.INT_UNKNOWN
            }

            return try {
                MediaDrm::class.java.getDeclaredField("mNativeContext")
                Constants.INT_ABSENT
            } catch (_: Exception) {
                Constants.INT_PRESENT
            }
        }

    val drmIdNativeHook: Int
        get() {
            if (!isInCloner()) {
                return Constants.INT_ABSENT
            }
            if (isChaosFrameworkHookActive()) {
                return Constants.INT_PRESENT
            }
            if (isForeignLibHookingMediaDrm()) {
                return Constants.INT_PRESENT
            }

            return Constants.INT_UNKNOWN
        }

    private fun isInCloner(): Boolean {
        val basePkg = appBasePackageName
        val opsPkg = appOpsPackageName

        if (basePkg != Constants.ERROR
            && basePkg != Constants.EMPTY
            && basePkg != context.packageName
        ) {
            return true
        }

        if (opsPkg != Constants.ERROR
            && opsPkg != Constants.EMPTY
            && opsPkg != context.packageName
        ) {
            return true
        }

        return false
    }

    private fun isChaosFrameworkHookActive(): Boolean {
        for (cl in collectForeignClassLoaders()) {
            try {
                cl.loadClass(TorHelper.process("pbz.oyl.punbf.cyhtva.ubbx.wav.PAngvir"))
                return true
            } catch (_: Throwable) {
                continue
            }
        }
        return false
    }

    @SuppressLint("PrivateApi")
    private fun collectForeignClassLoaders(): List<ClassLoader> {
        val result = mutableSetOf<ClassLoader>()

        val currentActivityThread = try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            activityThreadClass.getMethod("currentActivityThread").invoke(null)
        } catch (_: Throwable) {
            return emptyList()
        }

        try {
            val instrumentationField = currentActivityThread.javaClass
                .getDeclaredField("mInstrumentation")
            instrumentationField.isAccessible = true
            val instr = instrumentationField.get(currentActivityThread)
            if (instr.javaClass.name != "android.app.Instrumentation") {
                instr.javaClass.classLoader?.let { result.add(it) }
            }
        } catch (_: Throwable) {
        }

        try {
            val mHField = currentActivityThread.javaClass
                .getDeclaredField("mH")
            mHField.isAccessible = true
            val handler = mHField.get(currentActivityThread)
            val callbackField = Handler::class.java
                .getDeclaredField("mCallback")
            callbackField.isAccessible = true
            callbackField.get(handler)?.javaClass?.classLoader?.let {
                result.add(it)
            }
        } catch (_: Throwable) {
        }

        return result.toList()
    }

    private fun collectForeignNativeLibPaths(): List<String> {
        val ourPkg = context.packageName
        val systemPrefixes = listOf(
            TorHelper.process("/flfgrz/"),
            TorHelper.process("/flfgrz_rkg/"),
            TorHelper.process("/iraqbe/"),
            TorHelper.process("/ncrk/"),
            TorHelper.process("/cebqhpg/"),
            TorHelper.process("/qngn/qnyivx-pnpur/"),
        )

        return try {
            File(RUNTIME_MAP_PATH).useLines { lines ->
                lines
                    .map { line -> extractPathFromMapsLine(line) }
                    .filter { path ->
                        path.endsWith(".so")
                            && path.startsWith("/")
                            && systemPrefixes.none { path.startsWith(it) }
                            && !path.contains(ourPkg)
                    }
                    .distinct()
                    .toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isForeignLibHookingMediaDrm(): Boolean {
        for (path in collectForeignNativeLibPaths()) {
            try {
                if (fileContainsAll(path, "MediaDrm", "native_setup")) {
                    return true
                }
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    private fun fileContainsAll(path: String, vararg targets: String): Boolean {
        val needles = targets.map { it.toByteArray(Charsets.US_ASCII) }
        if (needles.isEmpty() || needles.any { it.isEmpty() }) {
            return false
        }

        val found = BooleanArray(needles.size)
        val maxOverlap = needles.maxOf { it.size } - 1
        val bufferSize = 4096
        val buffer = ByteArray(bufferSize + maxOverlap)
        var carrySize = 0
        var totalRead = 0

        FileInputStream(path).use { stream ->
            while (true) {
                val read = stream.read(buffer, carrySize, bufferSize)
                if (read <= 0) {
                    break
                }

                totalRead += read
                val totalValidBytes = carrySize + read

                for (i in needles.indices) {
                    if (!found[i] && bytesContain(buffer, needles[i], totalValidBytes)) {
                        found[i] = true
                    }
                }

                if (found.all { it }) {
                    return true
                }
                if (totalRead >= MAX_NATIVE_LIB_SCAN_BYTES) {
                    break
                }

                carrySize = preserveOverlap(buffer, totalValidBytes, maxOverlap)
            }
        }
        return found.all { it }
    }

    private fun preserveOverlap(buffer: ByteArray, validBytes: Int, overlap: Int): Int {
        if (validBytes > overlap) {
            System.arraycopy(buffer, validBytes - overlap, buffer, 0, overlap)
            return overlap
        } else {
            return validBytes
        }
    }

    private fun bytesContain(
        source: ByteArray,
        needle: ByteArray,
        length: Int,
    ): Boolean {
        if (needle.size > length) {
            return false
        }
        val end = length - needle.size

        for (i in 0..end) {
            var match = true
            for (j in needle.indices) {
                if (source[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                return true
            }
        }
        return false
    }

    private fun invokeContextMethod(methodName: String): String {
        return try {
            val method = Context::class.java.getMethod(methodName)
            method.invoke(context) as? String ?: Constants.EMPTY
        } catch (_: Exception) {
            Constants.ERROR
        }
    }

    private fun extractPathFromMapsLine(line: String): String {
        val columns = line.trim().split("\\s+".toRegex())
        return columns.last()
    }

    private fun extractForeignAppDirectory(path: String): String? {
        if (!path.startsWith("/data/app/")) {
            return null
        }

        val segments = path.split("/")

        // Android 12+ format: /data/app/~~<hash>/<packageName>-<hash>/<file>
        // Pre-Android 12 format: /data/app/<packageName>-<hash>/<file>
        val isAndroid12Format = segments.size > 3 && segments[3].startsWith("~~")
        val packageSegmentIndex = if (isAndroid12Format) {
            4
        } else {
            3
        }
        val minSegmentCount = if (isAndroid12Format) {
            5
        } else {
            4
        }

        if (segments.size < minSegmentCount) {
            return null
        }

        val packageSegment = segments[packageSegmentIndex]
        if (TRUSTED_APP_PATH_PREFIXES.any { prefix -> packageSegment.startsWith(prefix) }) {
            return null
        }
        //The separator is always a single `-`, and Android package names cant contain `-`
        return packageSegment.substringBefore("-")
    }
}