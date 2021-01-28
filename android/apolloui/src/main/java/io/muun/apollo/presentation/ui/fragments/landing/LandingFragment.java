package io.muun.apollo.presentation.ui.fragments.landing;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.MuunButton;

import android.os.Bundle;
import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;
import com.airbnb.lottie.LottieAnimationView;

public class LandingFragment extends SingleFragment<LandingPresenter> {

    private static final String ARG_SHOW_ANIMATION = "show_animation";

    public static LandingFragment newInstance() {
        return new LandingFragment();
    }

    public static LandingFragment newInstanceWithAnimation() {
        final LandingFragment fragment = new LandingFragment();

        final Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_SHOW_ANIMATION, true);
        fragment.setArguments(arguments);

        return fragment;
    }

    @BindView(R.id.signup_start)
    MuunButton startButton;

    @BindView(R.id.animation_view)
    LottieAnimationView lottieView;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.landing_activity;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        if (getArgumentsBundle().getBoolean(ARG_SHOW_ANIMATION)) {
            lottieView.setAnimation(R.raw.logo_animation);
            lottieView.setMinFrame(40); // Small arbitrary correction to json exported animation

        } else {
            lottieView.setImageResource(R.drawable.wordmark_blue_beta_big_top);
        }
    }

    @OnClick(R.id.signup_start)
    public void onStartButtonClick() {
        startButton.setEnabled(false); // avoid double tap while preparing next Fragment
        presenter.startSignup();
    }

    @OnClick(R.id.login_start)
    public void onLoginButtonClick() {
        startButton.setEnabled(false); // avoid double tap while preparing next Fragment
        presenter.startLogin();
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }
}
