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

        val dangerousAppsPackages = arrayOf(
            TorHelper.process("zr.oznk.ncngpu"),
            TorHelper.process("bet.yfcbfrq.znantre"),
            TorHelper.process("zr.jrvfuh.xrearyfh"),
            TorHelper.process("zr.jrvfuh.rkc"),
            TorHelper.process("vb.in.rkcbfrq"),
            TorHelper.process("vb.in.rkcbfrq64"),
            TorHelper.process("vb.tvguho.uhfxlqt.zntvfx"),
            TorHelper.process("whavbwfi.zgx.rnfl.fh"),
            TorHelper.process("pbz.qrinqinapr.ebbgpybnx2"),
            TorHelper.process("xvatbebbg.fhcrefh"),
            TorHelper.process("bet.znfgrenkr.fhcrehfre"),
            TorHelper.process("pbz.xvatbhfre.pbz")
        )
    }

    override fun action(): Observable<Boolean> {
        return Observable.defer {

            try {
                val rootBeer = RootBeer(context)

                if (rootBeer.isRooted) {
                    return@defer Observable.just(true)
                }

                val hasDangerousNewBinary = dangerousBinaries.any {
                    rootBeer.checkForBinary(it)
                }
                if (hasDangerousNewBinary) {
                    return@defer Observable.just(true)
                }

                val hasNewManagementApps =
                    rootBeer.detectRootManagementApps(dangerousAppsPackages)
                Observable.just(hasNewManagementApps)

            } catch (e: Exception) {
                // Catching exceptions to prevent potential issues with root checks
                Timber.e(e, "Root detection failed")
                Observable.just(false)
            }
        }
    }
}