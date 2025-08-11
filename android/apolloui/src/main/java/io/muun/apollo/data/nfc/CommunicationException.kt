package io.muun.apollo.data.nfc

import java.io.IOException


/**
 * Exception during communication with smart card
 */
class CommunicationException(message: String?, cause: Throwable) : IOException(message, cause)