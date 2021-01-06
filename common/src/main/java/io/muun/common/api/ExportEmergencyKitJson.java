package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportEmergencyKitJson {

    public MuunZonedDateTime lastExportedAt;

    @Nullable
    public Boolean verified;

    public String verificationCode;

    /**
     * Json constructor.
     */
    public ExportEmergencyKitJson() {
    }

    /**
     * Constructor.
     */
    public ExportEmergencyKitJson(MuunZonedDateTime lastExportedAt,
                                  @Nullable Boolean verified,
                                  String verificationCode) {

        this.lastExportedAt = lastExportedAt;
        this.verified = verified;
        this.verificationCode = verificationCode;
    }
}
