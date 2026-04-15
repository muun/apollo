package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Android Build Information JSON for `device` (a.k.a static pipeline)
 *
 * <p>This class represents the first version of Android Build Information JSON.
 * It reflects the previous reporting strategy, where this signal was emitted only
 * at specific session creation points.
 *
 * <p>The newer reporting strategy {@code houston/presentation/models/AndroidBuildInfoJson.java}
 * uses a dynamic counterpart, which reports repeatedly a conceptually equivalent signal
 * (with minor field-level differences) as part of the BackgroundExecutionMetrics flow.
 *
 * <p>This JSON model is retained for backward compatibility with older Apollo versions.
 */

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

    @Nullable
    public String model;

    @Nullable
    public String product;

    @Nullable
    public String release;

    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public AndroidBuildInfoJson() {
    }

    // Constructor removed: Apollo moved this flow to the dynamic pipeline.
}
