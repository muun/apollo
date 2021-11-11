package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportEmergencyKitJson {

    public enum Method {
        DRIVE,
        ICLOUD,
        MANUAL
    }

    public MuunZonedDateTime lastExportedAt;

    @Nullable
    public Boolean verified;

    public String verificationCode;

    @Since(
            apolloVersion = Supports.Taproot.APOLLO,
            falconVersion = Supports.Taproot.FALCON
    )
    @Nullable
    // Legacy clients already used this json and won't send this (e.g EkVersion.VERSION_DESCRIPTORS)
    public Integer version;

    @Since(apolloVersion = Supports.Taproot.APOLLO, falconVersion = Supports.Taproot.FALCON)
    @Nullable // Older clients wont send it and some DB entries won't have the value
    public Method method;

    /**
     * Json constructor.
     */
    public ExportEmergencyKitJson() {
    }

    /**
     * Apollo constructor.
     */
    public ExportEmergencyKitJson(
            MuunZonedDateTime lastExportedAt,
            @Nullable Boolean verified,
            String verificationCode,
            int version,
            @Nullable Method method
    ) {

        this.lastExportedAt = lastExportedAt;
        this.verified = verified;
        this.verificationCode = verificationCode;
        this.version = version;
        this.method = method;
    }

    /**
     * Houston constructor.
     */
    public ExportEmergencyKitJson(
            MuunZonedDateTime lastExportedAt,
            @Nullable Boolean verified,
            int version,
            @Nullable Method method
    ) {

        this.lastExportedAt = lastExportedAt;
        this.verified = verified;
        this.verificationCode = ""; // Yeah, THIS hackish. I'm not even ashamed. Come one by one.
        this.version = version;
        this.method = method;
    }
}
