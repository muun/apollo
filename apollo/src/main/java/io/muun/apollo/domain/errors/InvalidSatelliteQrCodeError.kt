package io.muun.apollo.domain.errors


class InvalidSatelliteQrCodeError: MuunError {

    constructor(message: String):
        super(message)

    constructor(message: String, cause: Throwable):
        super(message, cause)
}
