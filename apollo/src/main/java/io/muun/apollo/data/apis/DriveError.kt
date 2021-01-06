package io.muun.apollo.data.apis

import java.lang.RuntimeException

class DriveError(cause: Throwable): RuntimeException(cause)