package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AndroidDeviceFeaturesJson {

    @Nullable
    public AndroidDeviceFeaturesValue touch;

    @Nullable
    public AndroidDeviceFeaturesValue proximity;

    @Nullable
    public AndroidDeviceFeaturesValue accelerometer;

    @Nullable
    public AndroidDeviceFeaturesValue gyro;

    @Nullable
    public AndroidDeviceFeaturesValue compass;

    @Nullable
    public AndroidDeviceFeaturesValue telephony;

    @Nullable
    public AndroidDeviceFeaturesValue cdma;

    @Nullable
    public AndroidDeviceFeaturesValue gsm;

    @Nullable
    public AndroidDeviceFeaturesValue cameras;

    @Nullable
    public AndroidDeviceFeaturesValue pc;

    @Nullable
    public AndroidDeviceFeaturesValue pip;

    @Nullable
    public AndroidDeviceFeaturesValue dactylogram;

    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public AndroidDeviceFeaturesJson() {
    }

    public enum AndroidDeviceFeaturesValue {
        PRESENT,
        ABSENT,
        UNKNOWN
    }


    private AndroidDeviceFeaturesValue mapDeviceFeaturesValue(Integer value) {

        if (value == null) {
            return null;
        }

        switch (value) {
            case 0:
                return AndroidDeviceFeaturesValue.ABSENT;
            case 1:
                return AndroidDeviceFeaturesValue.PRESENT;
            default:
                return AndroidDeviceFeaturesValue.UNKNOWN;
        }
    }

    /**
     * Code constructor.
     */
    public AndroidDeviceFeaturesJson(
            @Nullable Integer proximity,
            @Nullable Integer accelerometer,
            @Nullable Integer gyro,
            @Nullable Integer compass,
            @Nullable Integer telephony,
            @Nullable Integer pc,
            @Nullable Integer pip
    ) {
        this.touch = null;
        this.proximity = mapDeviceFeaturesValue(proximity);
        this.accelerometer = mapDeviceFeaturesValue(accelerometer);
        this.gyro = mapDeviceFeaturesValue(gyro);
        this.compass = mapDeviceFeaturesValue(compass);
        this.telephony = mapDeviceFeaturesValue(telephony);
        this.cdma = null;
        this.gsm = null;
        this.cameras = null;
        this.pc = mapDeviceFeaturesValue(pc);
        this.pip = mapDeviceFeaturesValue(pip);
        this.dactylogram = null;
    }
}