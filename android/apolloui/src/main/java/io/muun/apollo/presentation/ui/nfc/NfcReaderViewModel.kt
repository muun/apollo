package io.muun.apollo.presentation.ui.nfc

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.muun.apollo.data.afs.MetricsProvider
import io.muun.apollo.data.nfc.NfcBridgerFactory
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.apollo.domain.action.sensor.StoreSensorsDataAction
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent.ERROR_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_ERROR
import io.muun.apollo.domain.libwallet.WalletClient
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.presentation.ui.nfc.events.GestureEvent
import io.muun.apollo.presentation.ui.nfc.events.ISensorEvent
import io.muun.common.utils.Encodings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class NfcReaderViewModel @Inject constructor(
    private val featureSelector: FeatureSelector,
    private val libwalletClient: WalletClient,
    private val nfcBridgerFactory: NfcBridgerFactory,
    private val analytics: Analytics,
    private val metricsProvider: MetricsProvider,
    private val storeSensorsDataAction: StoreSensorsDataAction,
) : ViewModel() {

    sealed class NfcReadState {
        object Success : NfcReadState()
        data class Error(val cause: Exception) : NfcReadState()
    }

    private var sensorJob: Job? = null

    private val _nfcReadState = MutableSharedFlow<NfcReadState>(replay = 0)
    val nfcReadState = _nfcReadState.asSharedFlow()

    private val _gestureEvents = MutableSharedFlow<ISensorEvent>(extraBufferCapacity = 64)
    private val gestureEvents: Flow<ISensorEvent> = _gestureEvents

    /**
     * Handles the discovery of an NFC [Tag] and attempts to perform a signing operation.
     *
     * Launches a coroutine in the [viewModelScope] to process the tag asynchronously.
     * Uses [IsoDep] to communicate with the tag and delegates signing logic to [handleSignMessage].
     * Emits a [NfcReadState] based on whether the signing operation was successful.
     *
     * @param nfcSession The NFC [NfcSession] built from the [Tag] discovered by the system.
     */
    internal fun onNewNfcSession(nfcSession: NfcSession) {
        viewModelScope.launch {
            val pair = withContext(Dispatchers.IO) {
                try {
                    Pair<ByteArray, Exception?>(handleSignMessage(nfcSession), null)
                } catch (e: Exception) {
                    Timber.e(e)
                    Pair(byteArrayOf(), e)
                }
            }

            val signedMessage = pair.first
            val error = pair.second

            Timber.d("NfcReaderViewModel: ${Encodings.bytesToHex(signedMessage)}")

            _nfcReadState.emit(
                if (signedMessage.isNotEmpty()) {
                    Timber.i("Security card verification SUCCESS")
                    NfcReadState.Success
                } else {
                    Timber.i("Security card verification ERROR")
                    NfcReadState.Error(error ?: Exception("Fallback msg: empty signedMessage"))
                }
            )
        }
    }

    private fun handleSignMessage(nfcSession: NfcSession): ByteArray {
        nfcSession.connect()
        val nfcBridger = nfcBridgerFactory.forSession(nfcSession)
        val challengeMessage = "testing NFC in Android"

        val signedMessageHex = libwalletClient.securityCardSignMessage(nfcBridger, challengeMessage)

        nfcSession.close()

        return signedMessageHex
    }

    internal fun reportNfcError(state: NfcReadState.Error) {
        analytics.report(
            E_ERROR(
                ERROR_TYPE.NFC_2FA_FAILED,
                state.cause.javaClass.name,
                state.cause.message ?: "empty message"
            )
        )
    }

    internal fun securityCard2faSuccess(nfcReaderActivity: NfcReaderActivity) {
        nfcReaderActivity.setResult(RESULT_OK, Intent())
        nfcReaderActivity.finishActivity()
    }

    /**
     * Subscribes to a merged flow of multiple sensor events using a coroutine launched in the given [lifecycleOwner]'s scope.
     *
     * Each sensor event is handled and logged, with potential for further processing such as sending data to a server.
     *
     * @param context The context used to obtain the [SensorManager].
     * @param lifecycleOwner The [LifecycleOwner] whose lifecycle is used to scope the coroutine collecting sensor updates.
     */
    internal fun subscribeToAllSensors(context: Context, lifecycleOwner: LifecycleOwner) {

        if (!featureSelector.get(MuunFeature.NFC_SENSORS)) {
            return
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val mergedFlow = merge(
            SensorUtils.mergedSensorFlow(sensorManager),
            gestureEvents,
        )

        sensorJob = lifecycleOwner.lifecycleScope.launch {
            mergedFlow.collect { event ->
                storeSensorsDataAction.run(event)
            }
        }
    }

    /**
     * Cancels the active job collecting merged sensor events, effectively unsubscribing from all sensor updates.
     */
    internal fun unsubscribeFromAllSensors() {
        sensorJob?.cancel()
    }

    /**
     * Handles a touch gesture event by wrapping the [MotionEvent] data into a [GestureEvent] and emitting it.
     *
     * @param event The [MotionEvent] containing gesture information such as action type, coordinates, and pointer count.
     */
    internal fun onGestureDetected(event: MotionEvent) {
        val gesture = SensorUtils.generateGestureEvent(event)
        _gestureEvents.tryEmit(gesture)
    }

    /**
     * Retrieves the current position of the NFC antenna, if available.
     *
     * @return A [Pair] of x and y coordinates representing the antenna position,
     * or null if not available.
     */
    internal fun getAntennaPosition(): Pair<Float, Float>? {
        return metricsProvider.nfcAntennaPosition.firstOrNull()
    }
}