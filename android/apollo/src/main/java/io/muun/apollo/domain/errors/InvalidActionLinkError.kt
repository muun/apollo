package io.muun.apollo.domain.errors

class InvalidActionLinkError(): MuunError() {

    constructor(uri: String): this() {
        metadata["uri"] = uri
    }
}
