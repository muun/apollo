package io.muun.apollo.presentation.ui.base;

import android.os.Bundle;
import androidx.annotation.Nullable;

import javax.validation.constraints.NotNull;

public interface Presenter<ViewT extends BaseView> extends ParentPresenter {


    /**
     * Override this method to add any initialization logic that needs to happen at view creation,
     * e.g at Activity/Fragment#onCreate.
     */
    void onViewCreated(@Nullable Bundle savedInstanceState);

    /**
     * Override this method to add any initialization logic that the presenter needs.
     */
    void setUp(@NotNull Bundle arguments);

    /**
     * Override this method to add any logic that the presenter needs to run right AFTER
     * initialization success.
     */
    void afterSetUp();

    /**
     * Override this method to add any clean up logic that the presenter needs.
     */
    void tearDown();

    /**
     * Saves the state of the presenter.
     */
    void saveState(@NotNull Bundle state);

    /**
     * Restores the state of the presenter.
     */
    void restoreState(@Nullable Bundle state);

    /**
     * Set presenter's view (e.g activity or fragment).
     */
    void setView(@NotNull ViewT view);

    /**
     * Send an email with a detailed error report, if the device has an email client/app installed.
     * Otherwise show a dialog with an explanatory comment and offer the possibility of copying
     * error report to the clipboard.
     */
    void sendErrorReport(Throwable throwable);

}
