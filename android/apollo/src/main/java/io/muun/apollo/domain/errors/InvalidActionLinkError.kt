package io.muun.apollo.domain.errors

class InvalidActionLinkError : MuunError {

    constructor() : super()

    constructor(uri: String, expected: String) : super("Invalid Deeplink clicked") {
        metadata["uri"] = uri
        metadata["expected"] = expected
    }
}
