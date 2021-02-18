package io.muun.apollo.presentation.ui.fragments.settings;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.NightMode;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.apollo.presentation.ui.base.BaseView;

import org.jetbrains.annotations.NotNull;

public interface SettingsView extends BaseView {

    void setUser(User user, CurrencyDisplayMode currencyMode, ExchangeRateWindow rateWindow);

    void setNightMode(@NotNull NightMode get);

    void profilePictureUpdated(UserProfile userProfile);

    void setLoading(boolean loading);

    void hidePublicProfileSection();

    void handleLogout(boolean shouldDisplayLogoutExplanation);

    void handleDeleteWallet(boolean shouldDisplayDeleteWalletExplanation);

}
