package io.muun.apollo.presentation.ui.select_country;

import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import javax.inject.Inject;

@PerActivity
public class SelectCountryPresenter extends BasePresenter<BaseView> {

    @Inject
    public SelectCountryPresenter() {
        super();
    }
}
