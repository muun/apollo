package io.muun.apollo.data.afs

import io.muun.apollo.domain.errors.CpuInfoError
import timber.log.Timber
import java.io.File
import java.util.Scanner

private const val CPU_INFO_PATH = "/proc/cpuinfo"
private const val CPU_INFO_KEY_VALUE_DELIMITER = ": "
private const val SINGLE_PROCESSOR_KEY = "processor"

class CpuInfoProvider {

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

    private fun parseCpuInfo(contents: String): CpuInfo {
        val linesStartEndBlankLine = listOf("") + contents.lines() + listOf("")

        val repeatedBlankLinesIndices = linesStartEndBlankLine
            .mapIndexed { index, s -> s to index }
            .windowed(size = 2, step = 1, partialWindows = false) {
                val (firstLine, _) = it[0]
                val (secondLine, secondIndex) = it[1]
                if (firstLine.isBlank() && secondLine.isBlank())
                    secondIndex
                else
                    null
            }
            .filterNotNull()

        // This is a list of lines that are divided to sections with single blank lines. Starts with a blank line. Ends with a
        // (potentially same) blank line.
        val linesWithSections = linesStartEndBlankLine
            .filterIndexed { index, s -> index !in repeatedBlankLinesIndices }

        val sectionBreaksIndices =
            linesWithSections.mapIndexed { index, s -> index.takeIf { s.isBlank() } }
                .filterNotNull()

        // sections (which are lists of non-blank lines). Each section is not empty.
        val sectionsOfLines =
            sectionBreaksIndices.windowed(size = 2, step = 1, partialWindows = false) {
                linesWithSections.slice(it[0] + 1 until it[1])
            }

        fun parseLine(line: String): Pair<String, String>? {
            return line
                .split(":", limit = 2)
                .takeIf { it.size == 2 }
                ?.map { it.dropWhile(Char::isWhitespace).dropLastWhile(Char::isWhitespace) }
                ?.let { it[0] to it[1] }
        }

        // sections (which are key to value pairs). Each section is not empty.
        val sectionsOfKeyValuePairs = sectionsOfLines
            .map { it.mapNotNull(::parseLine) }
            .filter { it.isNotEmpty() }

        fun isSingleProcessorPair(pair: Pair<String, String>): Boolean {
            return pair.first == SINGLE_PROCESSOR_KEY && pair.second.all(Char::isDigit)
        }

        fun extractProcessorInfo(section: List<Pair<String, String>>): List<Pair<String, String>> {
            return section.dropWhile { !isSingleProcessorPair(it) }
        }

        fun extractCommonInfo(section: List<Pair<String, String>>): List<Pair<String, String>> {
            return section.takeWhile { !isSingleProcessorPair(it) }
        }

        val processorsInfo = sectionsOfKeyValuePairs
            .map(::extractProcessorInfo)
            .filter { it.isNotEmpty() }
            .map { procInfo -> procInfo.filter { !isSingleProcessorPair(it) } }

        val commonInfo = sectionsOfKeyValuePairs
            .map(::extractCommonInfo)
            .filter { it.isNotEmpty() }
            .flatten()

        return CpuInfo(
            commonInfo = commonInfo,
            perProcessorInfo = processorsInfo,
        )
    }
}