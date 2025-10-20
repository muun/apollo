package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorEventJson {

    public Long eventId;

    public MuunZonedDateTime eventTimestamp;

    public String eventType;

    public Map<String, Object> eventData;

    public SensorEventJson(
            Long eventId,
            MuunZonedDateTime eventTimestamp,
            String eventType,
            Map<String, Object> eventData
    ) {
        this.eventId = eventId;
        this.eventTimestamp = eventTimestamp;
        this.eventType = eventType;
        this.eventData = eventData;
    }

    public SensorEventJson() {
    }
}
