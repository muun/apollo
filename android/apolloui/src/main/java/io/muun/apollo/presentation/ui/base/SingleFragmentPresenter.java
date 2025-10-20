package io.muun.apollo.presentation.ui.base;


import io.muun.common.utils.Preconditions;

import timber.log.Timber;

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

    /**
     * Initializes parentPresenter.
     */
    public void setParentPresenter(ParentT parentPresenter) {
        Preconditions.checkNotNull(parentPresenter);
        this.parentPresenter = parentPresenter;
    }

    @Override
    public void handleError(Throwable error) {
        // We've seen errors happening way to earlier in the fragment's lifecycle, and
        // parentPresenter not being set. This shouldn't really happen anymore but, just in case,
        // to avoid ugly stacktraces and hard to debug crash reports, we'll try to handleError
        // by "ourselves".
        if (parentPresenter != null) {
            parentPresenter.handleError(error);
        } else {
            Timber.i("ParentPresenter not set in handleError: %s", getClass().getSimpleName());
            try {
                // We'll try to handle error "ourselves" but, in case we can't we'll avoid crashing
                // and log error for further research.
                super.handleError(error);
            } catch (Exception e) {
                Timber.i("Couldn't handleError 'ourselves': %s", getClass().getSimpleName());
                Timber.e(e);
            }
        }
    }
}
