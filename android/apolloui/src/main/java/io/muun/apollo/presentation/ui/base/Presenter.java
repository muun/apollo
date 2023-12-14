package io.muun.apollo.presentation.ui.base;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import javax.validation.constraints.NotNull;

public interface Presenter<ViewT extends BaseView> extends ParentPresenter {

    /**
     * Override this method to add any initialization logic that needs to happen only once at view
     * creation, instead of each time the view is brought to the foreground aka resumed.
     * NOTE: this is called AFTER view creation (Activity/Fragment#onCreate), in fact for fragments
     * this is technically called in {@link Fragment#onViewCreated(View, Bundle)}..
     */
    void onViewCreated(@Nullable Bundle savedInstanceState);

    /**
     * Override this method to add any initialization logic that the presenter needs.
     */
    void setUp(@NotNull Bundle arguments);

    /**
     * Override this method to add any logic that the presenter needs to run right AFTER
     * initialization success.
     * NOTE: this is useful for our base classes (or any other parent class) that need to defer some
     * logic until subclass finishes initialization/setup.
     */
    void onSetUpFinished();

    /**
     * Override this method to add any clean up logic that the presenter needs when going to
     * background.
     */
    void tearDown();

    /**
     * Override this method to add any final actions before presenter gets destroyed.
     */
    void destroy();

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
