package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.domain.libwallet.LibwalletClient
import javax.inject.Inject

class BiometricsRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val libwalletClient: LibwalletClient,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_BIOMETRICS_OPT_IN = "biometricsOptIn"
    }

    override val fileName get() = "biometrics"

    fun userOptInBiometrics(): Boolean {
        return libwalletClient.getBoolean(KEY_BIOMETRICS_OPT_IN, false)
    }

    fun setUserOptInBiometrics(optIn: Boolean) {
        libwalletClient.saveBoolean(KEY_BIOMETRICS_OPT_IN, optIn)
    }

    fun deleteUserOptInBiometrics() {
        libwalletClient.delete(KEY_BIOMETRICS_OPT_IN)
    }
}