package io.muun.apollo.presentation.ui.fragments.loading

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class LoadingFragmentPresenter @Inject constructor():
    SingleFragmentPresenter<BaseView, ParentPresenter>()