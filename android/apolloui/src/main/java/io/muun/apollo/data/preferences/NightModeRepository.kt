package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.domain.libwallet.WalletClient
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.domain.utils.isEmpty
import rx.subjects.BehaviorSubject
import javax.inject.Inject

class NightModeRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val walletClient: WalletClient,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY = "nightMode"
    }

    private var nightModeSubject = BehaviorSubject.create<NightMode>()

    override val fileName get() = "current_night_mode"

    fun storeNightMode(mode: NightMode) {
        walletClient.saveString(KEY, mode.toString())
        nightModeSubject.onNext(mode)
    }

    fun getNightMode() : NightMode {
        val stringValue = walletClient.getString(KEY)
        if (stringValue.isEmpty()) {
            return NightMode.FOLLOW_SYSTEM
        }
        return NightMode.valueOf(stringValue!!)
    }

    fun watchNightMode() =
        nightModeSubject.asObservable()!!
}