package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.presentation.app.Navigator;
import io.muun.apollo.presentation.sensors.ShakeEventListener;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;

import javax.inject.Inject;

@PerActivity
public class ShakeToDebugExtension extends ActivityExtension {

    private final Navigator navigator;

    private ShakeEventListener sensorListener;

    @Inject
    public ShakeToDebugExtension(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Globals.INSTANCE.isDebug()) {
            initShakeDetection();
        }
    }

    @Override
    public void onResume() {
        if (Globals.INSTANCE.isDebug()) {
            resumeShakeDetection();
        }
    }

    @Override
    public void onPause() {
        if (Globals.INSTANCE.isDebug()) {
            pauseShakeDetection();
        }
    }

    private void pauseShakeDetection() {
        final SensorManager manager = (SensorManager) getActivity()
                .getSystemService(Context.SENSOR_SERVICE);

        manager.unregisterListener(sensorListener);
    }

    private void resumeShakeDetection() {
        final SensorManager manager = (SensorManager) getActivity()
                .getSystemService(Context.SENSOR_SERVICE);

        manager.registerListener(
                sensorListener,
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI
        );
    }

    private void initShakeDetection() {
        sensorListener = new ShakeEventListener();
        sensorListener.setOnShakeListener(this::openDebugPanel);
    }

    private void openDebugPanel() {
        navigator.navigateToDebugPanel(getActivity());
    }
}
