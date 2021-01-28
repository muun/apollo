package io.muun.apollo.domain.model;


import io.muun.common.api.beam.notification.NotificationJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationReport {

    private final long previousId;
    private final long maximumId;
    private final List<NotificationJson> preview;

    /**
     * Construct an empty NotificationReport.
     */
    public NotificationReport() {
        this(0L, 0L, new ArrayList<>());
    }

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

    /**
     * Return true if there are gaps before or after this report, given a last seen Notification ID.
     */
    public boolean isMissingNotifications(long sinceId) {
        if (previousId > sinceId) {
            return true; // we missed past notifications.
        }

        final long maximumIdInPreview = preview.isEmpty()
                ? -1L
                : preview.get(preview.size() - 1).id;

        final long maximumSeenId = Math.max(maximumIdInPreview, sinceId);

        if (maximumId > maximumSeenId) {
            return true; // there's more notifications waiting for us.
        }

        return false;
    }
}
