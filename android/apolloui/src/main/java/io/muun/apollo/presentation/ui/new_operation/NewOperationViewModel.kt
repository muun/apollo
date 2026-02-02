package io.muun.apollo.presentation.ui.new_operation

import android.content.Context
import android.hardware.SensorManager
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.muun.apollo.domain.action.sensor.StoreSensorsDataAction
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.presentation.ui.nfc.SensorUtils
import io.muun.apollo.presentation.ui.nfc.events.GestureEvent
import io.muun.apollo.presentation.ui.nfc.events.ISensorEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

class NewOperationViewModel @Inject constructor(
    private val featureSelector: FeatureSelector,
    private val storeSensorsDataAction: StoreSensorsDataAction,
) {

    private var sensorJob: Job? = null

    private val _appEvents = MutableSharedFlow<ISensorEvent>(extraBufferCapacity = 64)
    private val appEvents: Flow<ISensorEvent> = _appEvents

    /**
     * Subscribes to a merged flow of multiple sensor events using a coroutine launched in the given [lifecycleOwner]'s scope.
     *
     * Each sensor event is handled and logged, with potential for further processing such as sending data to a server.
     *
     * TODO: duplicated from [NfcReaderViewModel] unify both implementations.
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

    internal fun generateAppEvent(eventName: String) {
        val gesture = SensorUtils.generateAppEvent(
            eventName
        )
        _appEvents.tryEmit(gesture)
    }
}