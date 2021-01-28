package io.muun.apollo.presentation.ui.signup;

import io.muun.apollo.domain.model.SignupStep;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface SignupView extends SingleFragmentView {

    void changeStep(SignupStep step, SignupStep previousStep);
}
