package io.muun.apollo.presentation.ui.launcher;

import io.muun.apollo.databinding.LauncherActivityBinding;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.base.BaseView;

import android.os.Bundle;
import android.view.LayoutInflater;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

public class LauncherActivity extends BaseActivity<LauncherPresenter> implements BaseView {

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return LauncherActivityBinding::inflate;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        presenter.handleLaunch(getIntent().getData(), isTaskRoot());
    }
}
