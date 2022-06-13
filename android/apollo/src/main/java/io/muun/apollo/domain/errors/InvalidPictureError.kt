package io.muun.apollo.domain.errors


import android.content.Intent
import io.muun.apollo.data.external.UserFacingErrorMessages

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
