package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AndroidBuildInfoJson {

    @Nullable
    public List<String> abis;

    @Nullable
    public String fingerprint;

    @Nullable
    public String hardware;

    @Nullable
    public String bootloader;

    @Nullable
    public String manufacturer;

    @Nullable
    public String brand;

    @Nullable
    public String display;

    @Nullable
    public Long time;

    @Nullable
    public String host;

    @Nullable
    public String type;

    @Nullable
    public String radioVersion;

    @Nullable
    public String securityPatch;

    @Nullable
    public String baseOs;

    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public AndroidBuildInfoJson() {
    }

    /**
     * Code constructor.
     */
    public AndroidBuildInfoJson(
            @Nullable List<String> abis,
            @Nullable String fingerprint,
            @Nullable String hardware,
            @Nullable String bootloader,
            @Nullable String manufacturer,
            @Nullable String brand,
            @Nullable String display,
            @Nullable Long time,
            @Nullable String host,
            @Nullable String type,
            @Nullable String radioVersion,
            @Nullable String securityPatch,
            @Nullable String baseOs
    ) {
        this.abis = abis;
        this.fingerprint = fingerprint;
        this.hardware = hardware;
        this.bootloader = bootloader;
        this.manufacturer = manufacturer;
        this.brand = brand;
        this.display = display;
        this.time = time;
        this.host = host;
        this.type = type;
        this.radioVersion = radioVersion;
        this.securityPatch = securityPatch;
        this.baseOs = baseOs;
    }
}
