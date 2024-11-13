package io.muun.apollo.data.os

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import javax.inject.Inject


class SystemCapabilitiesProvider @Inject constructor(private val context: Context) {

    val bridgeDaemonStatus: String
        get() {
            return getSysProp(TorHelper.process("vavg.fip.nqoq"))
        }

    val usbPersistConfig: String
        get() {
            return getSysProp(TorHelper.process("flf.hfo.pbasvt"))
        }

    val bridgeEnabled: Int
        get() {
            return Settings.Global.getInt(
                context.contentResolver,
                TorHelper.process("nqo_ranoyrq"), Constants.INT_UNKNOWN
            )
        }

    val developerEnabled: Int
        get() {
            return Settings.Global.getInt(
                context.contentResolver,
                TorHelper.process("qrirybczrag_frggvatf_ranoyrq"),
                Constants.INT_UNKNOWN
            )
        }

    val usbConnected: Int
        get() {
            val usbStateIntent = context.registerReceiver(
                null,
                IntentFilter(TorHelper.process("naqebvq.uneqjner.hfo.npgvba.HFO_FGNGR"))
            )
            return usbStateIntent?.extras?.let { bundle ->
                if (bundle.getBoolean(TorHelper.process("pbaarpgrq"))) 1 else 0
            } ?: Constants.INT_UNKNOWN
        }

    val securityEnhancedBuild: String
        get() {
            return getSysProp(TorHelper.process("eb.ohvyq.fryvahk"))
        }


    val bridgeRootService: String
        get() {
            return getSysProp(TorHelper.process("freivpr.nqo.ebbg"))
        }

    @SuppressLint("PrivateApi")
    fun getSysProp(name: String): String {
        return try {
            val systemPropertyClass: Class<*> =
                Class.forName(TorHelper.process("naqebvq.bf.FlfgrzCebcregvrf"))
            val getMethod = systemPropertyClass.getMethod("get", String::class.java)
            val result = getMethod.invoke(null, name)
            return result as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}