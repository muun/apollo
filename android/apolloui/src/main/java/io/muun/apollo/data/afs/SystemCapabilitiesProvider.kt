package io.muun.apollo.data.afs

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings
import io.muun.apollo.data.os.TorHelper


class SystemCapabilitiesProvider(private val context: Context) {

    private val levels = linkedMapOf("XVGXNG" to 0,
        "XVGXNG_JNGPU" to 1,
        "Y" to 2,
        "YBYYVCBC" to 2,
        "YBYYVCBC_ZE1" to 3,
        "Z" to 4,
        "A" to 5,
        "A_ZE1" to 6,
        "B" to 7,
        "B_ZE1" to 8,
        "C" to 9,
        "D" to 10,
        "E" to 11,
        "F" to 12,
        "F_I2" to 13,
        "GVENZVFH" to 14,
        "HCFVQR_QBJA_PNXR" to 15,
        "INAVYYN_VPR_PERNZ" to 16,
        "ONXYNIN" to 17
    )

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

    val internalLevel: Pair<Int, Int>
        get() {
            val found = levels.entries.lastOrNull { (key, _) ->
                isCodePresent(key)
            }
            val value = found?.value?.plus(0x13) ?: -1
            val maxValue = levels.values.maxOrNull()?.plus(0x13) ?: -1
            return Pair(value, maxValue)
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

    private val codesClass = Class.forName(TorHelper.process("naqebvq.bf.Ohvyq\$IREFVBA_PBQRF"))

    private fun isCodePresent(codeName: String): Boolean {
        return try {
            codesClass.getField(TorHelper.process(codeName))
            true
        } catch (e: Exception) {
            false
        }
    }

}