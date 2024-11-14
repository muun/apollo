package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.FeeWindow
import org.threeten.bp.ZonedDateTime
import rx.Observable
import java.util.SortedMap
import javax.inject.Inject

// Open for mockito to mock/spy
open class FeeWindowRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_FEE_WINDOW = "fee_window"
    }

    /**
     * Like FeeWindow, but JSON-serializable.
     */
    private class StoredFeeWindow {
        // WAIT
        // WARNING
        // CAREFUL
        // READ THIS, I MEAN IT:
        // We forgot to exclude this class from Proguard rules. This means that the order of
        // declaration of this attributes is important -- until we remove this class from proguard
        // and migrate the preference to a non-minified JSON this class is APPEND-ONLY.
        @JvmField
        var houstonId: Long? = null

        @JvmField
        var fetchDate: ZonedDateTime? = null

        @JvmField
        var targetedFees: SortedMap<Int, Double>? = null

        @JvmField
        var fastConfTarget = 0

        @JvmField
        var mediumConfTarget = 0

        @JvmField
        var slowConfTarget = 0

        /**
         * JSON constructor.
         */
        @Suppress("unused")
        constructor()

        constructor(feeWindow: FeeWindow) {
            houstonId = feeWindow.houstonId
            fetchDate = feeWindow.fetchDate
            targetedFees = feeWindow.targetedFees
            fastConfTarget = feeWindow.fastConfTarget
            mediumConfTarget = feeWindow.mediumConfTarget
            slowConfTarget = feeWindow.slowConfTarget
        }

        fun toFeeWindow(): FeeWindow {
            return FeeWindow(
                houstonId,
                fetchDate,
                targetedFees,
                fastConfTarget,
                mediumConfTarget,
                slowConfTarget
            )
        }
    }

    private val feeWindowPreference: Preference<StoredFeeWindow>
        get() = rxSharedPreferences.getObject(
            KEY_FEE_WINDOW,
            JsonPreferenceAdapter(StoredFeeWindow::class.java)
        )

    override val fileName get() = "fee_window"

    /**
     * Return true if `fetch()` is safe to call.
     */
    private val isSet: Boolean
        get() = feeWindowPreference.isSet

    /**
     * Fetch an observable instance of the latest expected fee.
     * Only to be used after feeWindowPreference.isSet == true.
     */
    fun fetchNonNull(): Observable<FeeWindow> {
        return fetch().map { feeWindow -> feeWindow!! }
    }

    /**
     * Fetch an observable instance of the latest expected fee.
     */
    fun fetch(): Observable<FeeWindow?> {
        return feeWindowPreference.asObservable().map { storedFeeWindow: StoredFeeWindow? ->
            if (storedFeeWindow != null) {
                return@map storedFeeWindow.toFeeWindow()
            } else {
                return@map null
            }
        }
    }

    private fun fetchOne(): FeeWindow? {
        return fetch().toBlocking().first()
    }

    /**
     * Store the current expected fee.
     */
    fun store(feeWindow: FeeWindow) {
        feeWindowPreference.set(StoredFeeWindow(feeWindow))
    }

    /**
     * Migration to init dynamic fee targets.
     */
    fun initDynamicFeeTargets() {
        val feeWindow = fetchOne()
        if (feeWindow != null) {
            val migratedFeeWindow = feeWindow.initDynamicFeeTargets()
            store(migratedFeeWindow)
        }
    }

    fun isFeeRecent() =
        if (isSet)
            fetchOne()!!.isRecent
        else
            false
}