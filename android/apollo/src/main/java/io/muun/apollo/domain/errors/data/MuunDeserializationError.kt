package io.muun.apollo.domain.errors.data

import io.muun.apollo.domain.errors.MuunError
import io.muun.common.exception.PotentialBug

class MuunDeserializationError(cause: Exception, json: String?) : MuunError(cause), PotentialBug {

    constructor(json: String) : this(IllegalArgumentException(), json)

    init {
        metadata["json"] = json.toString()
    }
}