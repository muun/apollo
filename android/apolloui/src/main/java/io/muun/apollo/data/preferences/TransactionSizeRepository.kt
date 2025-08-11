package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.common.utils.Preconditions
import rx.Observable
import javax.inject.Inject

class TransactionSizeRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_TRANSACTION_SIZE = "transaction_size"
    }

    private val transactionSizePreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_TRANSACTION_SIZE, null)

    override val fileName get() = "transaction_size"

    /**
     * Get the stored NextTransactionSize.
     */
    val nextTransactionSize: NextTransactionSize?
        get() = watchNextTransactionSize().toBlocking().first()

    /**
     * Watch the stored NextTransactionSize.
     * Only to be used after transactionSizePreference.isSet == true.
     */
    fun watchNonNullNts(): Observable<NextTransactionSize> {
        return watchNextTransactionSize().map { nts -> nts!! }
    }

    /**
     * Watch the stored NextTransactionSize.
     */
    fun watchNextTransactionSize(): Observable<NextTransactionSize?> {
        return transactionSizePreference.asObservable()
            .map { json: String? ->
                if (json != null) {
                    return@map SerializationUtils.deserializeJson(
                        NextTransactionSize::class.java, json
                    )
                } else {
                    return@map null
                }
            }
    }

    /**
     * Replace the stored NextTransactionSize.
     */
    fun setTransactionSize(transactionSize: NextTransactionSize) {
        transactionSizePreference.setNow(
            SerializationUtils.serializeJson(NextTransactionSize::class.java, transactionSize)
        )
    }

    /**
     * Migration to init expected debt for pre-existing NTSs.
     */
    fun initExpectedDebt() {
        val hasNts = sharedPreferences.contains(KEY_TRANSACTION_SIZE)
        if (!hasNts) {
            return
        }
        val nts = nextTransactionSize
        Preconditions.checkNotNull(nts)
        setTransactionSize(nts!!.initExpectedDebt())
    }

    /**
     * Migration to init outpoints for pre-existing NTSs.
     */
    fun initNtsOutpoints() {
        val hasNts = sharedPreferences.contains(KEY_TRANSACTION_SIZE)
        if (!hasNts) {
            return
        }
        val nts = nextTransactionSize
        Preconditions.checkNotNull(nts)
        setTransactionSize(nts!!.initOutpoints())
    }

    /**
     * Migration to init utxo status for pre-existing NTSs.
     */
    fun initNtsUtxoStatus() {
        val hasNts = sharedPreferences.contains(KEY_TRANSACTION_SIZE)
        if (!hasNts) {
            return
        }
        val nts = nextTransactionSize
        Preconditions.checkNotNull(nts)
        setTransactionSize(nts!!.initUtxoStatus())
    }
}