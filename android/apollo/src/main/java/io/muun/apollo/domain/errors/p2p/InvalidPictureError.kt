package io.muun.apollo.domain.errors.p2p


import android.content.Intent
import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class InvalidPictureError : UserFacingError {

    constructor() : super(UserFacingErrorMessages.INSTANCE.invalidPicture())

    constructor(resultIntent: Intent) : super(UserFacingErrorMessages.INSTANCE.invalidPicture()) {
        metadata["intent"] = resultIntent.toString()
        metadata["action"] = resultIntent.action ?: "null"
        metadata["data"] = resultIntent.dataString ?: "null"
        metadata["extrasSize"] = resultIntent.extras?.size() ?: 0

        if (resultIntent.extras != null) {
            var extras = ""
            for (key in resultIntent.extras!!.keySet()) {
                extras += "$key=${resultIntent.extras!![key]} "
            }

            metadata["extras"] = extras
        }
    }
}
