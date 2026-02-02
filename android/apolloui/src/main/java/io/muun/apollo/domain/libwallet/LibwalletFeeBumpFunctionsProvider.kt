package io.muun.apollo.domain.libwallet

import io.muun.apollo.domain.libwallet.errors.FeeBumpFunctionsStoreError
import io.muun.apollo.domain.model.feebump.FeeBumpFunctions
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy
import io.muun.apollo.domain.utils.toLibwalletModel
import newop.Newop
import timber.log.Timber

class LibwalletFeeBumpFunctionsProvider : FeeBumpFunctionsProvider {

    override fun persistFeeBumpFunctions(
        feeBumpFunctions: FeeBumpFunctions,
        refreshPolicy: FeeBumpRefreshPolicy,
    ) {
        val feeBumpFunctionsStringList = feeBumpFunctions.functions.toLibwalletModel()

        try {
            Newop.persistFeeBumpFunctions(
                feeBumpFunctionsStringList,
                feeBumpFunctions.uuid,
                refreshPolicy.value
            )
        } catch (e: Exception) {
            Timber.e(e, "Error storing fee bump functions")
            throw FeeBumpFunctionsStoreError(feeBumpFunctions.functions.toString(), e)
        }
    }

    override fun areFeeBumpFunctionsInvalidated(): Boolean {
        return Newop.areFeeBumpFunctionsInvalidated()
    }
}