package io.muun.apollo.domain.errors

class LibwalletMismatchAddressError(javaAddress: String, goAddress: String)
    : MuunError("Libwallet produced a strange address") {

    init {
        metadata["javaAddress"] = javaAddress
        metadata["goLangAddress"] = goAddress
    }
}