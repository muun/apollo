package io.muun.apollo.presentation.ui.edit_fee;

import io.muun.apollo.presentation.ui.base.SingleFragmentView;

interface EditFeeView extends SingleFragmentView {

    void finishWithResult(int resultCode, double selectedFeeRate);
}
