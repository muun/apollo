package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportEmergencyKitJson {

    public MuunZonedDateTime lastExportedAt;

    public String verificationCode;

    /**
     * Json constructor.
     */
    public ExportEmergencyKitJson() {
    }

    /**
     * Constructor.
     */
    public ExportEmergencyKitJson(MuunZonedDateTime date, String verificationCode) {
        this.lastExportedAt = date;
        this.verificationCode = verificationCode;
    }
}
