package io.muun.common.api.beam.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

import javax.validation.constraints.NotNull;

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
