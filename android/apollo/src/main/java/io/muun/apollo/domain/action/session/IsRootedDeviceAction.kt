package io.muun.apollo.domain.action.session

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import io.muun.apollo.data.os.TorHelper
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

class IsRootedDeviceAction @Inject constructor(
    private val context: Context,
) : BaseAsyncAction0<Boolean>() {

    companion object {
        val dangerousBinaries = arrayOf(
            TorHelper.process("ncngpu"),
            TorHelper.process("xrearyfh"),
            TorHelper.process("zntvfxvavg"),
            TorHelper.process("fhcrefh")
        )
    }

    override fun action(): Observable<Boolean> {
        return Observable.defer {

            try {
                if (RootBeer(context).isRooted) {
                    return@defer Observable.just(true)
                }

                val hasDangerousNewBinary = dangerousBinaries.any {
                    RootBeer(context).checkForBinary(it)
                }
                Observable.just(hasDangerousNewBinary)
            } catch (e: Exception) {
                // Catching exceptions to prevent potential issues with root checks
                Timber.e(e, "Root detection failed")
                Observable.just(false)
            }
        }
    }
}