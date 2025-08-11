package io.muun.apollo.data.afs

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
