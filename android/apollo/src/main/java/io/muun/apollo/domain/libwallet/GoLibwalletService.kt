package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.libwallet.errors.FeeBumpFunctionsStoreError
import io.muun.apollo.domain.utils.toLibwalletModel
import newop.Newop
import timber.log.Timber

class GoLibwalletService: LibwalletService {
    override fun persistFeeBumpFunctions(encodedFeeBumpFunctions: List<String>) {
        val feeBumpFunctionsStringList = encodedFeeBumpFunctions.toLibwalletModel()

        try {
            Newop.persistFeeBumpFunctions(feeBumpFunctionsStringList)
        } catch (e: Exception) {
            Timber.e(e, "Error storing fee bump functions")
            throw FeeBumpFunctionsStoreError(encodedFeeBumpFunctions.toString(), e)
        }
    }

    override fun areFeeBumpFunctionsInvalidated(): Boolean {
        return Newop.areFeeBumpFunctionsInvalidated()
    }
}