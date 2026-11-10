package io.muun.apollo.domain.libwallet.errors

data class ErrorDetail(
    val type: ErrorDetailType,
    val code: Long,
    val message: String,
    val developerMessage: String,
    val context: Map<String, String>,
) {
    companion object {
        // Context key under which libwallet names the firmware version of the security card an
        // error happened with.
        const val CARD_FIRMWARE_VERSION_KEY = "card_firmware_version"
    }
}
