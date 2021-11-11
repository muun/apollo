package io.muun.apollo.presentation.ui.recovery_code.priming;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.View;
import android.widget.TextView;
import butterknife.BindView;

public class PrimingRecoveryCodeFragment extends SingleFragment<PrimingRecoveryCodePresenter>
        implements PrimingRecoveryCodeView {

    @BindView(R.id.priming_recovery_code_title)
    TextView title;

    @BindView(R.id.priming_recovery_code_desc)
    TextView description;

    @BindView(R.id.priming_recovery_code_start)
    MuunButton startButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.priming_recovery_code_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final MuunHeader header = getParentActivity().getHeader();
        header.setElevated(true);
        header.setNavigation(MuunHeader.Navigation.BACK);

        startButton.setOnClickListener(v -> presenter.continueToShowRecoveryCode());
    }

    @Override
    public void setTexts(User user) {
        if (!user.hasPassword) {
            title.setText(R.string.priming_recovery_code_email_skipped_title);
            description.setText(R.string.priming_recovery_code_email_skipped_desc);
        }
    }
}

