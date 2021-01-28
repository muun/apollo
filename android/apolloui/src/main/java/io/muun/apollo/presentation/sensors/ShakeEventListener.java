package io.muun.apollo.presentation.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeEventListener implements SensorEventListener {

    private static final int MIN_MOVEMENT_TO_CONSIDER = 13;

    private static final long MAX_TIME_BETWEEN_MOVEMENTS = 200;

    private static final int MIN_TIME_TO_SHAKE = 900;

    private static final int TIME_BETWEEN_SHAKES = 1000;

    private float lastX = 0;

    private float lastY = 0;

    private float lastZ = 0;

    private long firstConsideredMovementTime = 0;

    private long lastConsideredMovementTime = 0;

    private OnShakeListener shakeListener;

    public interface OnShakeListener {

        void onShake();
    }

    public void setOnShakeListener(OnShakeListener listener) {
        shakeListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        final float x = sensorEvent.values[SensorManager.DATA_X];
        final float y = sensorEvent.values[SensorManager.DATA_Y];
        final float z = sensorEvent.values[SensorManager.DATA_Z];

        final float totalMovement = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);

        final long now = System.currentTimeMillis();
        final long timeSinceFirstConsideredMovement = now - firstConsideredMovementTime;
        final long timeSinceLastConsideredMovement = now - lastConsideredMovementTime;

        if (totalMovement < MIN_MOVEMENT_TO_CONSIDER) {
            return;
        }

        lastX = x;
        lastY = y;
        lastZ = z;

        lastConsideredMovementTime = now;

        if (timeSinceLastConsideredMovement > MAX_TIME_BETWEEN_MOVEMENTS) {
            firstConsideredMovementTime = lastConsideredMovementTime;
            return;
        }

        if (timeSinceFirstConsideredMovement >= MIN_TIME_TO_SHAKE) {
            firstConsideredMovementTime = lastConsideredMovementTime + TIME_BETWEEN_SHAKES;
            shakeListener.onShake();
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}