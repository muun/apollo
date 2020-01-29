package io.muun.apollo.domain.libwallet.errors

import io.muun.apollo.domain.errors.MuunError

open class LibwalletMismatchError(entity: String, field: String, javaValue: Any?, goValue: Any?) :
    MuunError("Libwallet produced a strange $entity value") {

    init {
        metadata["field"] = field
        metadata["javaValue"] = javaValue.toString()
        metadata["goLangValue"] = goValue.toString()
    }
}