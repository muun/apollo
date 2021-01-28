package io.muun.apollo.presentation.ui.migration

import android.content.Context
import android.content.Intent
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.setup_password.SetupPasswordActivity
import io.muun.apollo.presentation.ui.view.LoadingView

class MigrationActivity: BaseActivity<MigrationPresenter>(), MigrationView {

    companion object {
        fun getStartActivityIntent(context: Context) =
                Intent(context, MigrationActivity::class.java)
    }

    @BindView(R.id.migration_loading)
    lateinit var loadingView: LoadingView

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_migration
    }

    override fun initializeUi() {
        super.initializeUi()

        loadingView.setTitle(R.string.migration_title)
    }

}