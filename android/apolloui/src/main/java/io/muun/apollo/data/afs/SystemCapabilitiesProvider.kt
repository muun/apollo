package io.muun.apollo.data.afs

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import io.muun.apollo.data.os.TorHelper


class SystemCapabilitiesProvider(private val context: Context) {

    val bridgeDaemonStatus: String
        get() {
            return getSysPropSecure("vavg.fip.nqoq")
        }

    val usbPersistConfig: String
        get() {
            return getSysPropSecure("flf.hfo.pbasvt")
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
            return getSysPropSecure("eb.ohvyq.fryvahk")
        }


    val bridgeRootService: String
        get() {
            return getSysPropSecure("freivpr.nqo.ebbg")
        }

    val vbMeta: String
        get() {
            return getSysPropSecure("eb.obbg.iozrgn.qvtrfg")
        }

    val deviceRegion: Map<String, String>? by lazy {
        sequenceOf(
            "csc" to { getSysPropSecure("eb.pfp.fnyrf_pbqr") },
            "miui" to { getSysPropSecure("eb.zvhv.ertvba") },
            "country" to { getSysPropSecure("eb.obbg.pbhagel_ertvba") },
            "hw" to { getSysPropSecure("eb.pbasvt.uj.ertvba") },
            "hw_radio" to { getSysPropSecure("eb.iraqbe.uj.enqvb") },
            "regionmark" to { getSysPropSecure("eb.iraqbe.bccb.ertvbaznex") },
            "regionmark_fallback" to { getSysPropSecure("eb.bccb.ertvbaznex") },
            "product" to { getSysPropSecure("eb.cebqhpg.pbhagel.ertvba") },
            "locale" to { getSysPropSecure("eb.cebqhpg.ybpnyr.ertvba") },
        ).firstNotNullOfOrNull { (source, function) ->
            function().takeIf { it.isNotEmpty() }?.let {
                mapOf("source" to source, "value" to it.take(100))
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun getSysPropSecure(name: String): String {
        return try {
            val systemPropertyClass: Class<*> =
                Class.forName(TorHelper.process("naqebvq.bf.FlfgrzCebcregvrf"))
            val getMethod = systemPropertyClass.getMethod("get", String::class.java)
            val result = getMethod.invoke(null, TorHelper.process(name))
            return result as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}