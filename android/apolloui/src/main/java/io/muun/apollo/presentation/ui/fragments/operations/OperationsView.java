package io.muun.apollo.presentation.ui.fragments.operations;

import io.muun.apollo.domain.model.SecurityCenter;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel;
import io.muun.apollo.presentation.ui.base.BaseView;

import java.util.List;

public interface OperationsView extends BaseView {

    void setViewState(List<ItemViewModel> items, SecurityCenter sc);
}
