package io.muun.apollo.presentation.ui.fragments.single_action;


import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.Presenter;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;

import javax.inject.Inject;

public class SingleActionPresenter<ViewT extends BaseView, ParentPresenterT extends Presenter>
        extends SingleFragmentPresenter<ViewT, ParentPresenterT> {

    @Inject
    public SingleActionPresenter() {
    }
}
