package io.muun.apollo.presentation.ui.home;

import io.muun.apollo.R;
import io.muun.apollo.presentation.analytics.AnalyticsEvent.SECURITY_CENTER_ORIGIN;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterFragmentArgs;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import butterknife.BindColor;
import butterknife.BindView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import icepick.State;

import javax.validation.constraints.NotNull;

public class HomeActivity extends SingleFragmentActivity<HomePresenter>
        implements HomeView {

    /**
     * Creates an intent to launch this activity for a NEW user.
     */
    public static Intent getStartActivityIntentForNewUser(@NotNull Context context) {
        return getStartActivityIntent(context).putExtra(SHOW_WELCOME_TO_MUUN, true);
    }

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, HomeActivity.class);
    }

    public static String SHOW_WELCOME_TO_MUUN = "SHOW_WELCOME_TO_MUUN";

    @BindView(R.id.home_header)
    MuunHeader header;

    @BindColor(R.color.muun_gray_dark)
    int menuIconColor;

    @State
    String profilePictureUrl;

    @State
    int currentNavItem = R.id.home_fragment;

    private NavController navController;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.home_activity;
    }

    @Override
    protected int getMenuResource() {
        return R.menu.home_activity;
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter.onActivityCreated();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.onActivityDestroyed();
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);

        final NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        final BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(bottomNav, navController);

        bottomNav.setOnNavigationItemSelectedListener(item -> {

                    final Bundle bundle = new Bundle();

                    if (item.getItemId() == R.id.security_center_fragment) {
                        final SecurityCenterFragmentArgs args = new SecurityCenterFragmentArgs
                                .Builder(SECURITY_CENTER_ORIGIN.SHIELD_BUTTON)
                                .build();

                        bundle.putAll(args.toBundle());
                    }

                    navigateToItem(item.getItemId(), bundle);
                    return true;
                }
        );

        if (getIntent().hasExtra(SHOW_WELCOME_TO_MUUN)) {
            getIntent().removeExtra(SHOW_WELCOME_TO_MUUN);

            final MuunDialog muunDialog = new MuunDialog.Builder()
                    .layout(R.layout.dialog_welcome_to_muun)
                    .style(R.style.MuunWelcomeDialog)
                    .addOnClickAction(R.id.welcome_to_muun_cta, v -> dismissDialog())
                    .build();

            showDialog(muunDialog);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        final boolean showMenu = super.onCreateOptionsMenu(menu);

        final MenuItem item = menu.findItem(R.id.feedback);
        item.setOnMenuItemClickListener(menuItem -> {
            onSendFeedbackClick();
            return true;
        });

        return showMenu;
    }

    public void onSendFeedbackClick() {
        presenter.navigateToSendFeedbackScreen();
    }

    @Override
    public void navigateToSecurityCenter() {

        final SecurityCenterFragmentArgs args = new SecurityCenterFragmentArgs
                .Builder(SECURITY_CENTER_ORIGIN.EMPTY_HOME_ANON_USER)
                .build();

        navigateToItem(R.id.security_center_fragment, args.toBundle());
    }

    private void navigateToItem(int itemId, Bundle bundle) {
        // TODO: define transition animation See NavigationUI.onNavDestinationSelected()

        if (currentNavItem != itemId) {
            navController.navigate(itemId, bundle, null);
            currentNavItem = itemId;
        }
    }
}
