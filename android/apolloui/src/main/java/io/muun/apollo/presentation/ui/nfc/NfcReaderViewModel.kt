package io.muun.apollo.presentation.ui.nfc

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.muun.apollo.data.afs.MetricsProvider
import io.muun.apollo.data.nfc.NfcBridgerFactory
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.apollo.domain.FeatureOverrideStore
import io.muun.apollo.domain.action.FetchFeasibleZoneBoundaryAction
import io.muun.apollo.domain.action.sensor.StoreSensorsDataAction
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.AnalyticsEvent.ERROR_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_ERROR
import io.muun.apollo.domain.analytics.AnalyticsEvent.E_NEW_OP_ACTION_TYPE
import io.muun.apollo.domain.analytics.AnalyticsEvent.SECURITY_CARD_TAP_PARAM
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.libwallet.errors.ErrorDetailCode
import io.muun.apollo.domain.libwallet.errors.LibwalletGrpcError
import io.muun.apollo.domain.model.FeasibleZone
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.presentation.ui.nfc.events.GestureEvent
import io.muun.apollo.presentation.ui.nfc.events.ISensorEvent
import io.muun.common.utils.Encodings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class NfcReaderViewModel @Inject constructor(
    private val featureSelector: FeatureSelector,
    private val featureOverrideStore: FeatureOverrideStore,
    private val libwalletClient: LibwalletClient,
    private val nfcBridgerFactory: NfcBridgerFactory,
    private val analytics: Analytics,
    private val metricsProvider: MetricsProvider,
    private val storeSensorsDataAction: StoreSensorsDataAction,
    private val fetchFeasibleZoneBoundaryAction: FetchFeasibleZoneBoundaryAction,
) : ViewModel() {

    sealed interface ViewState {
        object Reading : ViewState
        data class Scanning(val feasibleZone: FeasibleZone? = null) : ViewState
    }

    sealed interface ViewCommand {
        object Success : ViewCommand
        data class Error(val message: String) : ViewCommand
        object DisableSecurityCardFlag : ViewCommand
    }

    private var sensorJob: Job? = null
    private var feasibleZone: FeasibleZone? = null

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Scanning(feasibleZone))
    val viewState = _viewState.asStateFlow()

    private val _viewCommand = MutableSharedFlow<ViewCommand>(replay = 0)
    val viewCommand = _viewCommand.asSharedFlow()

    private val _appEvents = MutableSharedFlow<ISensorEvent>(extraBufferCapacity = 64)
    private val appEvents: Flow<ISensorEvent> = _appEvents

    internal var latestError: Throwable? = null

    /**
     * Handles the discovery of an NFC [Tag] and attempts to perform a signing operation.
     *
     * Launches a coroutine in the [viewModelScope] to process the tag asynchronously.
     * Uses [IsoDep] to communicate with the tag and delegates signing logic to [handleSignChallenge].
     * Emits a [ViewCommand] based on whether the signing operation was successful.
     *
     * @param nfcSession The NFC [NfcSession] built from the [Tag] discovered by the system.
     */
    internal fun onNewNfcSession(nfcSession: NfcSession) {
        analytics.report(AnalyticsEvent.E_SECURITY_CARD_TAP(SECURITY_CARD_TAP_PARAM.DETECTED))

        viewModelScope.launch {
            _viewState.emit(ViewState.Reading)
            try {
                handleSignChallenge(nfcSession)
                analytics.report(AnalyticsEvent.E_SECURITY_CARD_SIGN_SUCCESS())
                _viewCommand.emit(ViewCommand.Success)
            } catch (error: Exception) {

                val errorMessage = buildErrorMessage(error)
                reportNfcError(error, errorMessage)
                latestError = error
                _viewCommand.emit(ViewCommand.Error(errorMessage))

                if (error is LibwalletGrpcError) {
                    if (error.errorDetail?.code == ErrorDetailCode.NO_SLOTS_AVAILABLE.code) {
                        analytics.report(
                            AnalyticsEvent.E_NEW_OP_ACTION(
                                E_NEW_OP_ACTION_TYPE.DISABLE_FLAG_DIALOG_SHOWN
                            )
                        )
                        _viewCommand.emit(ViewCommand.DisableSecurityCardFlag)
                    }
                }
            }
        }
    }

    private fun buildErrorMessage(exception: Exception?): String {
        return if (exception is LibwalletGrpcError) {
            // TODO: move all those copy to strings.xml when defined
            val errorDetail = exception.errorDetail
            if (errorDetail?.code == ErrorDetailCode.SIGN_MAC_VALIDATION_FAILED.code) {
                "Invalid Signature Verification! You're probably tapping with another " +
                    "(incorrect) security card"
            } else {
                if (errorDetail?.developerMessage?.isNotEmpty() == true) {
                    errorDetail.developerMessage
                } else {
                    "empty message"
                }
            }
        } else {
            "Error! See Debug logs or dismiss and try again\n\n${exception?.message ?: "empty message"}"
        }
    }

    private suspend fun handleSignChallenge(nfcSession: NfcSession) {
        withContext(Dispatchers.IO) {
            nfcSession.connect()
            val nfcBridger = nfcBridgerFactory.forSession(nfcSession)
            val challengeMessage = "testing NFC in Android"

            if (featureSelector.get(MuunFeature.NFC_CARD)) {
                val signedMessageHex = libwalletClient.securityCardSignMessage(
                    nfcBridger,
                    challengeMessage
                )
                Timber.d("NfcReaderViewModel: ${Encodings.bytesToHex(signedMessageHex)}")

            } else if (featureSelector.get(MuunFeature.NFC_CARD_V2)) {
                libwalletClient.securityCardV2SignMessage(nfcBridger)

            } else {
                // this shouldn't happen
                Timber.e("No security card enabled in nfc reader activity")
            }

            nfcSession.close()
        }
    }

    private fun reportNfcError(error: Exception, errorMessage: String) {
        Timber.e(error)
        analytics.report(
            E_ERROR(
                ERROR_TYPE.NFC_2FA_FAILED,
                error,
                "errorMessage" to errorMessage
            )
        )

        if (error is LibwalletGrpcError) {
            val code = error.errorDetail?.code.toString()
            val message = error.errorDetail?.developerMessage
            analytics.report(AnalyticsEvent.E_SECURITY_CARD_TAP_ERROR(code, message))

        } else {
            analytics.report(AnalyticsEvent.E_SECURITY_CARD_TAP_ERROR("unknown", error.message))
        }
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
            appEvents,
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
        _appEvents.tryEmit(gesture)
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

    internal fun generateAppEvent(eventName: String) {
        val gesture = SensorUtils.generateAppEvent(
            eventName
        )
        _appEvents.tryEmit(gesture)
    }

    internal fun fetchFeasibleZoneBoundary() {
        // TODO: make this send the necessary signals to recognize the correct measure
        val deviceModel = "${Build.MANUFACTURER}_${Build.MODEL}"

        fetchFeasibleZoneBoundaryAction.action(deviceModel)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { feasibleArea ->
                    this.feasibleZone = feasibleArea
                    _viewState.tryEmit(ViewState.Scanning(feasibleZone))
                },
                { error ->
                    _viewCommand.tryEmit(ViewCommand.Error(error.message ?: ""))
                }
            )
    }

    internal fun disableSecurityCardFF() {
        featureOverrideStore.storeOverride(MuunFeature.NFC_CARD_V2, true)
        analytics.report(AnalyticsEvent.E_NEW_OP_ACTION(E_NEW_OP_ACTION_TYPE.DISABLE_FLAG))
    }

    fun cancelDisableSecurityCardFF() {
        analytics.report(AnalyticsEvent.E_NEW_OP_ACTION(E_NEW_OP_ACTION_TYPE.CANCEL_DISABLE_FLAG))
    }
}