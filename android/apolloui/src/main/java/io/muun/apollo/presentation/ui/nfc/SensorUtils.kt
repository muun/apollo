package io.muun.apollo.presentation.ui.nfc

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import io.muun.apollo.presentation.ui.nfc.events.Acceleration
import io.muun.apollo.presentation.ui.nfc.events.AccelerometerEvent
import io.muun.apollo.presentation.ui.nfc.events.ISensorEvent
import io.muun.apollo.presentation.ui.nfc.events.MagneticEvent
import io.muun.apollo.presentation.ui.nfc.events.Rotation
import io.muun.apollo.presentation.ui.nfc.events.RotationEvent
import io.muun.apollo.presentation.ui.nfc.events.SignificantMotionEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.math.abs

internal object SensorUtils {

    /**
     * Merges multiple sensor flows into a single [Flow] of [ISensorEvent]s.
     *
     * This flow combines rotation, magnetic field, and significant motion sensor events into a unified stream.
     * Each sensor event is mapped to its corresponding [ISensorEvent] implementation.
     *
     * @param sensorManager The [SensorManager] used to access the required sensors.
     * @return A [Flow] emitting [ISensorEvent] instances from all subscribed sensors.
     */
    internal fun mergedSensorFlow(sensorManager: SensorManager): Flow<ISensorEvent> {
        return merge(
            subscribeToRotationSensor(sensorManager).map { RotationEvent(it) },
            subscribeToMagneticSensor(sensorManager).map { MagneticEvent(it) },
            subscribeToSignificantMotionSensor(sensorManager).map { SignificantMotionEvent(it) },
            subscribeToAccelerometerSensor(sensorManager).map {
                AccelerometerEvent(it)
            },
        )
    }

    /**
     * Creates a cold [Flow] that emits [Rotation] values based on changes in the device's rotation sensor.
     *
     * The flow emits only when the difference in any of the three rotation axes (azimuth, pitch, roll)
     * exceeds [minDegreeChange]. It attempts to use the [Sensor.TYPE_ROTATION_VECTOR], falling back to
     * [Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR] if unavailable.
     * The listener is automatically unregistered when the flow collection is cancelled.
     *
     * @param sensorManager The [SensorManager] used to access the appropriate rotation sensor.
     * @param minDegreeChange The minimum degree difference on any axis required to emit a new [Rotation]. Defaults to 1.0f.
     * @return A cold [Flow] emitting [Rotation] objects representing orientation changes.
     */
    private fun subscribeToRotationSensor(
        sensorManager: SensorManager,
        minDegreeChange: Float = 3.0f,
    ): Flow<Rotation> = callbackFlow {
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val minDegChange = if (sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            minDegreeChange
        } else {
            minDegreeChange * 3
        }

        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                val xAxis = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val yAxis = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val zAxis = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                if (
                    abs(xAxis - lastX) >= minDegChange ||
                    abs(yAxis - lastY) >= minDegChange ||
                    abs(zAxis - lastZ) >= minDegChange
                ) {
                    lastX = xAxis
                    lastY = yAxis
                    lastZ = zAxis

                    trySendBlocking(Rotation(xAxis, yAxis, zAxis))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            /* listener = */ listener, 
            /* sensor = */ sensor,
            /* samplingPeriodUs = */ SensorManager.SENSOR_DELAY_NORMAL,
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Creates a cold [Flow] that emits magnetic field strength values from the device's magnetic field sensor.
     *
     * The flow emits only when the change in magnetic field strength exceeds [minMagneticFieldChange].
     * Sensor events are sampled with [SensorManager.SENSOR_DELAY_NORMAL], and the listener is automatically
     * unregistered when the flow collection is cancelled.
     *
     * @param sensorManager The [SensorManager] used to access the magnetic field sensor.
     * @param minMagneticFieldChange The minimum change in field strength required to trigger an emission. Defaults to 1.0f.
     * @return A cold [Flow] emitting [Float] values representing changes in magnetic field strength.
     */
    private fun subscribeToMagneticSensor(
        sensorManager: SensorManager,
        minMagneticFieldChange: Float = 1.0f,
    ): Flow<Float> = callbackFlow {
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (sensor == null) {
            close()
            return@callbackFlow
        }

        var lastMagneticField = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val magnetic = event.values[0]

                if (abs(magnetic - lastMagneticField) >= minMagneticFieldChange) {
                    lastMagneticField = magnetic
                    trySend(magnetic)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Creates a cold [Flow] that emits once when significant motion is detected.
     *
     * This sensor triggers a single time when it detects significant motion (e.g., walking),
     * and then automatically disables itself. Useful for detecting user activity changes.
     *
     * @param sensorManager The [SensorManager] used to access the sensor.
     * @return A cold [Flow] emitting [Unit] once upon detecting significant motion.
     */
    private fun subscribeToSignificantMotionSensor(
        sensorManager: SensorManager,
    ): Flow<Float> = callbackFlow {
        val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                trySend(event?.values?.firstOrNull() ?: 0f)
                // Sensor disables itself after firing
                close()
            }
        }

        val registered = sensorManager.requestTriggerSensor(listener, sensor)
        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose {
            sensorManager.cancelTriggerSensor(listener, sensor)
        }
    }

    /**
     * Creates a cold [Flow] that emits acceleration values from the device's accelerometer sensor.
     *
     * The flow emits a [Triple] of acceleration values (x, y, z) when the change on any axis exceeds [minAccelerationChange].
     * Sensor events are sampled with [SensorManager.SENSOR_DELAY_NORMAL], and the listener is automatically
     * unregistered when the flow collection is cancelled.
     *
     * @param sensorManager The [SensorManager] used to access the accelerometer sensor.
     * @param minAccelerationChange The minimum change in acceleration required to trigger an emission. Defaults to 0.5f.
     * @return A cold [Flow] emitting acceleration values as [Triple]s of [Float]s for the x, y, and z axes.
     */
    private fun subscribeToAccelerometerSensor(
        sensorManager: SensorManager,
        minAccelerationChange: Float = 0.5f,
    ): Flow<Acceleration> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensor == null) {
            close()
            return@callbackFlow
        }

        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (
                    abs(x - lastX) > minAccelerationChange ||
                    abs(y - lastY) > minAccelerationChange ||
                    abs(z - lastZ) > minAccelerationChange
                ) {
                    lastX = x
                    lastY = y
                    lastZ = z

                    trySend(Acceleration(x, y, z))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}