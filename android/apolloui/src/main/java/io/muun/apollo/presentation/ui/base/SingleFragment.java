package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.listener.OnBackPressedListener;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import javax.validation.constraints.NotNull;

public abstract class SingleFragment<PresenterT extends SingleFragmentPresenter>
        extends BaseFragment<PresenterT> implements SingleFragmentView, OnBackPressedListener {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter.setParentPresenter(getParentActivity().getPresenter());
    }

    @Override
    protected void onActivityCreated() {
        getParentActivity().attachHeader();
        setUpHeader();
    }

    /**
     * Implement this method to perform initial visual setup of our MuunHeader (toolbar) component.
     *
     * <p>This is guaranteed to run AFTER:
     * - Activity#onCreate
     * - Fragment#onAttach (so fragment is attached to activity, getActivity returns non null)
     *
     * <p>Also, header is guaranteed to be already attached to activity (meaning its Toolbar is
     * ready to act as the ActionBar for the Activity window).
     */
    protected void setUpHeader() {
        // Leaving this default impl empty. Which signals that the fragment leaves the
        // responsibility of setting up the header to the parent activity. NOTE: we decided against
        // making this choice explicit in each fragment since there are so many cases and this
        // behaviour (delegating to the activity) is something we do in other places.
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
    public void clearFragmentBackStack() {
        getParentActivity().clearFragmentBackStack();
    }

    @Override
    public void replaceFragment(@NotNull Fragment fragment, boolean pushToBackstack) {
        getParentActivity().replaceFragment(fragment, pushToBackstack);
    }

    @Override
    public void replaceFragmentNow(@NotNull Fragment fragment) {
        getParentActivity().replaceFragmentNow(fragment);
    }

    @Override
    public void setLoading(boolean loading) {

    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    /**
     * Note: this is not a redundant overload. We're changing the return value (admittedly by using
     * an unsafe cast).
     */
    protected SingleFragmentActivity getParentActivity() {
        return (SingleFragmentActivity) super.getParentActivity();
    }
}
