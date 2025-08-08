package io.muun.apollo.domain.model;


import io.muun.common.api.beam.notification.NotificationJson;

import java.util.Collections;
import java.util.List;

public class NotificationReport {

    private final long previousId;
    private final long maximumId;
    private final List<NotificationJson> preview;

    /**
     * Construct a NotificationReport.
     */
    public NotificationReport(long previousId, long maximumId, List<NotificationJson> preview) {
        this.previousId = previousId;
        this.maximumId = maximumId;
        this.preview = Collections.unmodifiableList(preview);
    }

    public List<NotificationJson> getPreview() {
        return preview;
    }

    public long getMaximumId() {
        return maximumId;
    }

    public long getPreviousId() {
        return previousId;
    }

}
