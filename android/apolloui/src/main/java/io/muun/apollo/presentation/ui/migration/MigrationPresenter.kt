package io.muun.apollo.presentation.ui.migration

import android.os.Bundle
import io.muun.apollo.R
import io.muun.apollo.domain.ApiMigrationsManager
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import javax.inject.Inject

class MigrationPresenter @Inject constructor(
    private val apiMigrationsManager: ApiMigrationsManager,
) : BasePresenter<BaseView>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        runMigrations()
    }

    private fun runMigrations() {
        subscribeTo(apiMigrationsManager.run()) {
            navigator.navigateToHome(context)
            view.finishActivity()
        }
    }

    override fun handleNonFatalError(error: Throwable): Boolean {
        val errorDialog = MuunDialog.Builder()
            .layout(R.layout.dialog_custom_layout)
            .message(R.string.migration_error_message)
            .positiveButton(resId = R.string.migration_error_retry, action = this::runMigrations)
            .build()

        view.showDialog(errorDialog)

        return true
    }
}
