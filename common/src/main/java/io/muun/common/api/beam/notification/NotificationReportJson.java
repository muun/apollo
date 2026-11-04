package io.muun.common.api.beam.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationReportJson {

    @NotNull
    public Long previousId;

    @NotNull
    public Long maximumId;

    @NotEmpty
    public List<NotificationJson> preview;

    /**
     * Json constructor.
     */
    public NotificationReportJson() {
    }

    /**
     * Beam constructor.
     */
    public NotificationReportJson(Long previousId, Long maximumId, List<NotificationJson> preview) {

        this.previousId = previousId;
        this.maximumId = maximumId;
        this.preview = preview;
    }
}
