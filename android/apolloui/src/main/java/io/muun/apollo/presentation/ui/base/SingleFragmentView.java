package io.muun.apollo.presentation.ui.base;

import androidx.fragment.app.Fragment;

import javax.validation.constraints.NotNull;

public interface SingleFragmentView extends BaseView {

    void clearFragmentBackStack();

    void replaceFragment(@NotNull Fragment fragment, boolean pushToBackstack);

    void replaceFragmentNow(@NotNull Fragment fragment);

    void setLoading(boolean loading);
}
