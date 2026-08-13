package io.muun.apollo.data.afs

import android.content.Context
import android.nfc.NfcAdapter
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Probes the device for hints about NFC Extended APDU support without needing a tapped tag.
 * All results are cached for the lifetime of the instance to keep the cost off the hot path
 * of BackgroundExecutionMetrics (which is serialised on every outgoing HTTP request).
 */
class NfcExtendedApduProbe(private val context: Context) {

    companion object {
        private val CONFIG_FILES = listOf(
            "/vendor/etc/libnfc-nci.conf",
            "/vendor/etc/libnfc-nxp.conf",
            "/vendor/etc/libnfc-brcm.conf",
            "/vendor/etc/libnfc-nci-vendor.conf",
            "/system/etc/libnfc-nxp.conf",
            "/system/etc/libnfc-brcm.conf",
        )

        private val CHIP_PATTERN = Regex(
            """\b(PN\d{3}|SN\d{3}[UV]?|BCM\d{4,})\b""",
            RegexOption.IGNORE_CASE,
        )

        // android.nfc.tech.TagTechnology.ISO_DEP = 3 per AOSP. Referenced by value because
        // TagTechnology is hidden from the public SDK. Best-effort — if the framework's
        // INfcTag service uses a different tech ID on some Android version, the reflection
        // tier fails safely and reports the failure via failureReason.
        private const val TECH_ID_ISO_DEP = 3

        // Real NFC HAL config files are on the order of a few KB. Cap defends against
        // unexpectedly large or hostile vendor files being loaded whole into memory.
        private const val MAX_CONFIG_FILE_BYTES = 64L * 1024L
    }

    data class NfcConfigFileScan(
        val filesPresent: List<String>,
        val chipIdentifier: String,
        val contentHash: String,
    )

    data class NfcReflectionScan(
        val extendedApduSupported: Boolean?,
        val maxTransceiveLength: Int?,
        val failureReason: String,
    )

    val configFileScan: NfcConfigFileScan by lazy { scanConfigFiles() }

    val reflectionScan: NfcReflectionScan by lazy { probeViaReflection() }

    private fun scanConfigFiles(): NfcConfigFileScan {
        val filesPresent = mutableListOf<String>()
        val readableContent = StringBuilder()

        for (path in CONFIG_FILES) {
            val file = File(path)
            val exists = try {
                file.exists()
            } catch (e: SecurityException) {
                Timber.i("SELinux blocked existence check for $path: ${e.message}")
                false
            }
            if (!exists) {
                continue
            }

            filesPresent.add(path)

            val size = file.length()
            if (size > MAX_CONFIG_FILE_BYTES) {
                Timber.i("Skipping oversized NFC config file: $path ($size bytes)")
                continue
            }

            try {
                readableContent.append(file.readText())
                readableContent.append('\n')
            } catch (e: Exception) {
                Timber.i("Could not read $path: ${e.message}")
            }
        }

        val raw = readableContent.toString()
        return NfcConfigFileScan(
            filesPresent = filesPresent.toList(),
            chipIdentifier = CHIP_PATTERN.find(raw)?.value?.uppercase() ?: Constants.EMPTY,
            contentHash = if (raw.isNotEmpty()) sha256Hex(raw) else Constants.EMPTY,
        )
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun probeViaReflection(): NfcReflectionScan {
        val adapter = try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (t: Throwable) {
            return NfcReflectionScan(
                extendedApduSupported = null,
                maxTransceiveLength = null,
                failureReason = "getDefaultAdapter:${t.javaClass.simpleName}",
            )
        }

        if (adapter == null) {
            return NfcReflectionScan(
                extendedApduSupported = null,
                maxTransceiveLength = null,
                failureReason = "noAdapter",
            )
        }

        val service = firstNonNull(
            { invokeStatic(NfcAdapter::class.java, "getService") },
            { invokeStatic(NfcAdapter::class.java, "getServiceInterface") },
            { readStaticField(NfcAdapter::class.java, "sService") },
        ) ?: return NfcReflectionScan(
            extendedApduSupported = null,
            maxTransceiveLength = null,
            failureReason = "iNfcAdapter:notReachable",
        )

        val tagService = try {
            invokeInstance(service, "getNfcTagInterface")
        } catch (t: Throwable) {
            return NfcReflectionScan(
                extendedApduSupported = null,
                maxTransceiveLength = null,
                failureReason = "getNfcTagInterface:${t.javaClass.simpleName}",
            )
        } ?: return NfcReflectionScan(
            extendedApduSupported = null,
            maxTransceiveLength = null,
            failureReason = "iNfcTag:null",
        )

        val supported = try {
            invokeInstance(tagService, "getExtendedLengthApdusSupported") as? Boolean
        } catch (t: Throwable) {
            Timber.i("Reflection getExtendedLengthApdusSupported failed: ${t.message}")
            null
        }

        val maxLen = try {
            invokeInstance(tagService, "getMaxTransceiveLength", TECH_ID_ISO_DEP) as? Int
        } catch (t: Throwable) {
            Timber.i("Reflection getMaxTransceiveLength failed: ${t.message}")
            null
        }

        val failureReason = when {
            supported == null && maxLen == null -> {
                "iNfcTag:allMethodsFailed"
            }

            supported == null -> {
                "getExtendedLengthApdusSupported:failed"
            }

            maxLen == null -> {
                "getMaxTransceiveLength:failed"
            }

            else -> {
                Constants.EMPTY
            }
        }

        return NfcReflectionScan(
            extendedApduSupported = supported,
            maxTransceiveLength = maxLen,
            failureReason = failureReason,
        )
    }

    private fun invokeStatic(cls: Class<*>, methodName: String): Any? {
        val method = cls.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(null)
    }

    @Suppress("SameParameterValue")
    private fun readStaticField(cls: Class<*>, fieldName: String): Any? {
        val field = cls.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(null)
    }

    private fun invokeInstance(target: Any, methodName: String): Any? {
        val method = target.javaClass.getMethod(methodName)
        return method.invoke(target)
    }

    @Suppress("SameParameterValue")
    private fun invokeInstance(target: Any, methodName: String, intArg: Int): Any? {
        val method = target.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
        return method.invoke(target, intArg)
    }

    private fun firstNonNull(vararg blocks: () -> Any?): Any? {
        for (block in blocks) {
            try {
                val result = block()
                if (result != null) return result
            } catch (t: Throwable) {
                Timber.i("Reflection attempt failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        return null
    }
}
