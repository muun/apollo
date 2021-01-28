package io.muun.apollo.presentation.ui.launcher;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;

import android.os.Bundle;
import androidx.annotation.Nullable;

public class LauncherActivity extends BaseActivity<LauncherPresenter> implements BaseView {

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.launcher_activity;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        presenter.handleLaunch(getIntent().getData(), isTaskRoot());
    }
}
