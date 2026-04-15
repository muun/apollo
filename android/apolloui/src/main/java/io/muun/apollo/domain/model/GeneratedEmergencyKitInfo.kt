package io.muun.apollo.domain.model

/**
 * Information about a generated Emergency Kit.
 * This class contains metadata returned from libwallet after generating an Emergency Kit PDF.
 */
data class GeneratedEmergencyKitInfo(
    val verificationCode: String,
    val version: Int
)