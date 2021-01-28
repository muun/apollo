package io.muun.apollo.domain.errors


class Bip72FallbackAlert(uri: String, cause: Throwable): MuunError("Fallback to BIP21", cause) {

    init {
        metadata["uri"] = uri
    }

}