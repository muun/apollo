package io.muun.apollo.presentation.ui.operation_detail;

import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.base.BaseView;

public interface OperationDetailView extends BaseView {

    void setOperation(UiOperation operation);
}
