package io.muun.apollo.domain.errors

class SignupDraftFormatError(draftString: String?) : MuunError() {

    init {
        metadata["draftString"] = draftString ?: "<unknown>"
    }

}