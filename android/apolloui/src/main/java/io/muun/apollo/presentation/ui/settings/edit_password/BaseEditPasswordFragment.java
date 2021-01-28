package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;

public abstract class BaseEditPasswordFragment<T extends BaseEditPasswordPresenter>
        extends SingleFragment<T> {

    protected MuunHeader getHeader() {
        return getParentActivity().getHeader();
    }
}
