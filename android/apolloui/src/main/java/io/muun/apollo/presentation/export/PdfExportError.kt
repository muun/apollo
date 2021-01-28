package io.muun.apollo.presentation.export

class PdfExportError: RuntimeException {
    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
}