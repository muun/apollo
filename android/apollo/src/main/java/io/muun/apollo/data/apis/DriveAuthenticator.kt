package io.muun.apollo.data.apis

import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * An interface to use DriveImpl without leaking Observables to the UI layer.
 */
interface DriveAuthenticator {

    fun getSignInIntent(): Intent

    fun getSignedInAccount(resultIntent: Intent): GoogleSignInAccount

    fun signOut()

}