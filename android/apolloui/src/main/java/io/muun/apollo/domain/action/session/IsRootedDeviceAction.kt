package io.muun.apollo.domain.action.session

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import io.muun.apollo.data.os.TorHelper
import timber.log.Timber

class IsRootedDeviceAction(val context: Context) {

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

    fun isRooted(): Boolean {
        return try {
            val rootBeer = RootBeer(context)
            if (rootBeer.isRooted) {
                return true
            }
            if (dangerousBinaries.any { rootBeer.checkForBinary(it) }) {
                return true
            }
            rootBeer.detectRootManagementApps(dangerousAppsPackages)
        } catch (e: Exception) {
            // Catching exceptions to prevent potential issues with root checks
            Timber.e(e, "Root detection failed")
            false
        }
    }
}
