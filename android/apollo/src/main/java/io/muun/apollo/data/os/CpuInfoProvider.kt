package io.muun.apollo.data.os

import io.muun.apollo.domain.errors.CpuInfoError
import timber.log.Timber
import java.io.File
import java.util.Scanner

private const val CPU_INFO_PATH = "/proc/cpuinfo"
private const val CPU_INFO_KEY_VALUE_DELIMITER = ": "

/**
 * Structured cpu data. Using List<Pair<String, String>> instead of Map<String, String, a couple
 * of times since apparently keys can be repeated (even for per processor info).
 */
data class CpuInfo(
    val commonInfo: List<Pair<String, String>>,
    // except processor : x pairs. index in list may be considered as an index of a processor.
    val perProcessorInfo: List<List<Pair<String, String>>>,
    val legacyData: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY: CpuInfo = CpuInfo(
            commonInfo = emptyList(),
            perProcessorInfo = emptyList(),
            legacyData = emptyMap()
        )
    }
}

object CpuInfoProvider {

    fun getCpuInfo(): CpuInfo =
        try {
            val cpuInfoContents = File(CPU_INFO_PATH).readText()
            parseCpuInfo(cpuInfoContents)
                .copy(legacyData = safeGetLegacyCpuInfo())
        } catch (e: Exception) {
            Timber.e(CpuInfoError("structured", e))
            CpuInfo.EMPTY
        }

    private fun safeGetLegacyCpuInfo(): Map<String, String> =
        try {
            getLegacyCpuInfo()
        } catch (e: java.lang.Exception) {
            Timber.e(CpuInfoError("legacy", e))
            emptyMap()
        }

    /**
     * It looks like it doesn't support having multiple processors and the data it reports should
     * be a worse formatted subset of the one reported by getCpuInfo.
     */
    private fun getLegacyCpuInfo(): Map<String, String> {
        val map: MutableMap<String, String> = HashMap()

        Scanner(File(CPU_INFO_PATH)).use { s ->
            while (s.hasNextLine()) {
                val cpuInfoValues = s.nextLine().split(CPU_INFO_KEY_VALUE_DELIMITER)
                if (cpuInfoValues.size > 1) map[cpuInfoValues[0].trim { it <= ' ' }] =
                    cpuInfoValues[1].trim { it <= ' ' }
            }
        }

        return map
    }
}