package io.muun.apollo.presentation.ui.feedback.anon;

import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.common.Optional;

public interface AnonFeedbackView extends BaseView {

    String SUPPORT_ID = "support_id";

    void setSupportId(Optional<String> maybeSupportId);

}
