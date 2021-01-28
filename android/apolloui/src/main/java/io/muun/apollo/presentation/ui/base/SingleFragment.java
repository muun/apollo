package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.listener.OnBackPressedListener;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import javax.validation.constraints.NotNull;

public abstract class SingleFragment<PresenterT extends SingleFragmentPresenter>
        extends BaseFragment<PresenterT> implements SingleFragmentView, OnBackPressedListener {

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        presenter.setParentPresenter(getParentActivity().getPresenter());
    }

    @Override
    public void onResume() {
        super.onResume();
        getParentActivity().setOnBackPressedListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getParentActivity().setOnBackPressedListener(null);
    }

    @Override
    protected void initializePresenter(@Nullable Bundle savedInstanceState) {
        super.initializePresenter(savedInstanceState);
    }

    @Override
    public void clearFragmentBackStack() {
        getParentActivity().clearFragmentBackStack();
    }

    @Override
    public void replaceFragment(@NotNull Fragment fragment, boolean pushToBackstack) {
        getParentActivity().replaceFragment(fragment, pushToBackstack);
    }

    @Override
    public void setLoading(boolean loading) {

    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    protected SingleFragmentActivity getParentActivity() {
        return (SingleFragmentActivity) getActivity();
    }
}
