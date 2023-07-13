package io.muun.apollo.presentation.ui.fragments.operations;

import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel;
import io.muun.apollo.presentation.ui.base.BaseView;

import java.util.List;

public interface OperationsView extends BaseView {

    /**
     * Set view state.
     */
    void setViewState(List<ItemViewModel> items);
}
