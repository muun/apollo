package io.muun.apollo.presentation.ui.fragments.settings;

import io.muun.apollo.domain.model.NightMode;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.fragments.settings.SettingsPresenter.SettingsState;

import org.jetbrains.annotations.NotNull;

public interface SettingsView extends BaseView {

    /**
     * Set this view's model.
     */
    void setState(SettingsState state);

    /**
     * Set app's night mode to one of the possible modes. See {@code NightMode} for possible values.
     */
    void setNightMode(@NotNull NightMode get);

    /**
     * Handle profile picture update success.
     */
    void profilePictureUpdated(UserProfile userProfile);

    /**
     * Set this view's loading state.
     */
    void setLoading(boolean loading);

    /**
     * Hide PublicProfile section, this is a user without a PublicProfile.
     */
    void hidePublicProfileSection();

    /**
     * Handle the logout action.
     */
    void handleLogout(boolean shouldDisplayLogoutExplanation);

    /**
     * Handle the delete wallet action.
     */
    void handleDeleteWallet(boolean canDeleteWallet, boolean isRecoverableUser);

    /**
     * Show a simple, standard muun error dialog to communicate that non empty wallet can't be
     * deleted.
     */
    void showCantDeleteNonEmptyWalletDialog();
}
