package io.muun.apollo.presentation.ui.home;

import io.muun.apollo.R;
import io.muun.apollo.data.external.Globals;
import io.muun.apollo.databinding.HomeActivityBinding;
import io.muun.apollo.domain.analytics.AnalyticsEvent.SECURITY_CENTER_ORIGIN;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.home.HomeFragmentArgs;
import io.muun.apollo.presentation.ui.fragments.security_center.SecurityCenterFragmentArgs;
import io.muun.apollo.presentation.ui.view.BlockClock;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.viewbinding.ViewBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import kotlin.jvm.functions.Function1;
import timber.log.Timber;

import java.util.Objects;
import javax.inject.Inject;
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
     * Creates an intent to launch this activity, and show a new operation badge.
     */
    public static Intent getStartActivityIntent(@NotNull Context context, final Operation op) {
        return getStartActivityIntent(context).putExtra(NEW_OP_ID, op.getHid());
    }

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, HomeActivity.class);
    }

    static final String SHOW_WELCOME_TO_MUUN = "SHOW_WELCOME_TO_MUUN";

    public static final String NEW_OP_ID = "NEW_OP_ID";

    private NavController navController;

    @Inject
    InAppUpdateManager.Factory inAppUpdateManagerFactory;

    private final ActivityResultLauncher<IntentSenderRequest> updateLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        final int resultCode = result.getResultCode();
                        if (resultCode != Activity.RESULT_OK) {
                            Timber.i("InAppUpdate: update flow failed (code=%s)", resultCode);
                        }
                    }
            );

    private HomeActivityBinding binding() {
        return (HomeActivityBinding) getBinding();
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.home_activity;
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return HomeActivityBinding::inflate;
    }

    @Override
    protected int getMenuResource() {
        return R.menu.home_activity;
    }

    @Override
    public MuunHeader getHeader() {
        return binding().homeHeader;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        final NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        final BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        navController = Objects.requireNonNull(navHostFragment).getNavController();
        final Bundle initialBundle = new Bundle();
        initialBundle.putAll(new HomeFragmentArgs
                .Builder()
                .setNewOpId(getArgumentsBundle().getLong(NEW_OP_ID, -1L))
                .build().toBundle()
        );
        navController.setGraph(R.navigation.home_nav_graph, initialBundle);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // Override the listener installed by setupWithNavController so we can disable the
        // default fragment animations on tab swaps.
        final NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(R.id.home_fragment, false, true)
                .setEnterAnim(0)
                .build();

        bottomNav.setOnItemSelectedListener(item -> {
            navController.navigate(item.getItemId(), null, navOptions);
            return true;
        });

        if (Globals.INSTANCE.isDogfood()) {
            inAppUpdateManagerFactory.create(this, updateLauncher).checkForUpdate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        final boolean showMenu = super.onCreateOptionsMenu(menu);

        final MenuItem item = menu.findItem(R.id.feedback);
        item.setOnMenuItemClickListener(menuItem -> {
            presenter.navigateToSendFeedbackScreen();
            return true;
        });

        return showMenu;
    }

    @Override
    public void navigateToSecurityCenter() {

        final SecurityCenterFragmentArgs args = new SecurityCenterFragmentArgs
                .Builder()
                .setOrigin(SECURITY_CENTER_ORIGIN.EMPTY_HOME_ANON_USER)
                .build();

        navController.navigate(R.id.security_center_fragment, args.toBundle());
    }

    @Override
    public void showWelcomeToMuunDialog() {
        final MuunDialog muunDialog = new MuunDialog.Builder()
                .layout(R.layout.dialog_welcome_to_muun)
                .fixedWidthInDp(290) // Workaround for bug with foldables and tablets
                .style(R.style.MuunWelcomeDialog)
                .addOnClickAction(R.id.welcome_to_muun_cta, v -> dismissDialog())
                .build();

        showDialog(muunDialog);
    }

    /**
     * Show Taproot celebration dialog! A once-in-a-lifetime special event.
     */
    @Override
    public void showTaprootCelebration() {
        if (applicationLockExtension.isShowingLockOverlay()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showTaprootCelebration, 100);
            return;
        }

        presenter.reportTaprootCelebrationShown();

        new MuunDialog.Builder()
                .layout(R.layout.dialog_taproot_celebration, (view, dialog) -> {
                    final BlockClock blockClock = view.findViewById(R.id.dialog_block_clock);
                    final MuunButton confirmButton = view.findViewById(R.id.dialog_confirm);
                    final TextView title = view.findViewById(R.id.dialog_title);

                    blockClock.setValue(0);
                    confirmButton.setOnClickListener(v -> dialog.dismiss());
                    title.setText(R.string.tr_celebration_user_native_title);

                    return null;
                })
                .build()
                .show(this);
    }
}
