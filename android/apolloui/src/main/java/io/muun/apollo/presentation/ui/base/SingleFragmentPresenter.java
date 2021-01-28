package io.muun.apollo.presentation.ui.base;


import io.muun.common.utils.Preconditions;

import javax.inject.Inject;

public class SingleFragmentPresenter<ViewT extends BaseView, ParentT extends ParentPresenter>
        extends BasePresenter<ViewT> {

    private ParentT parentPresenter;

    protected ParentT getParentPresenter() {
        return parentPresenter;
    }

    /**
     * Creates a presenter.
     */
    @Inject
    public SingleFragmentPresenter() {
    }

    public void setParentPresenter(ParentT parentPresenter) {
        Preconditions.checkNotNull(parentPresenter);
        this.parentPresenter = parentPresenter;
    }

    @Override
    public void handleError(Throwable error) {
        parentPresenter.handleError(error);
    }
}
