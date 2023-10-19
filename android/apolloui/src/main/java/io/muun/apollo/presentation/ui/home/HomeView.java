package io.muun.apollo.presentation.ui.home;

import io.muun.apollo.presentation.ui.base.BaseView;

public interface HomeView extends BaseView {

    /**
     * Takes user to SecurityCenter screen.
     */
    void navigateToSecurityCenter();

    /**
     * Takes user to SecurityCenter screen.
     */
    void showWelcomeToMuunDialog();

    /**
     * Show Taproot celebration.
     */
    void showTaprootCelebration();
}
