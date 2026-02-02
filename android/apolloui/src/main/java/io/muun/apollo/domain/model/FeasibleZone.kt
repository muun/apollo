package io.muun.apollo.domain.model

data class FeasibleZone(
    val boundary: List<List<Int>>,
    val totalTimeQuantiles: Map<String, Double>
)
