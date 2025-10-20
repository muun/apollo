package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorEventBatchJson {

    public List<SensorEventJson> events;

    public SensorEventBatchJson(
            List<SensorEventJson> events
    ) {
        this.events = events;
    }

    public SensorEventBatchJson() {
    }
}
