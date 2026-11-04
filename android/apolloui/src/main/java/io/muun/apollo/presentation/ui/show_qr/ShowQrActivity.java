package io.muun.apollo.presentation.ui.show_qr;

import io.muun.apollo.R;
import io.muun.apollo.databinding.ShowQrActivityBinding;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.selector.UserPreferencesSelector;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.Presenter;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.show_qr.ln.LnInvoiceView;
import io.muun.apollo.presentation.ui.show_qr.unified.ShowUnifiedQrFragment;
import io.muun.apollo.presentation.ui.utils.OS;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.utils.Preconditions;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.RequiresApi;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;
import javax.inject.Inject;

public class ShowQrActivity extends SingleFragmentActivity<ShowQrPresenter> implements ShowQrView {

    static final String ORIGIN = "receive_origin";

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context,
                                                AnalyticsEvent.RECEIVE_ORIGIN origin) {
        return new Intent(context, ShowQrActivity.class)
                .putExtra(ORIGIN, origin);
    }

    @Inject
    UserPreferencesSelector userPreferencesSelector;

    private ShowQrActivityBinding binding() {
        return (ShowQrActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return ShowQrActivityBinding::inflate;
    }

    @Override
    public MuunHeader getHeader() {
        return binding().showQrHeader;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.show_qr_unified_qr_container;
    }

    @Override
    protected int getMenuResource() {
        return R.menu.activity_show_qr;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean showMenu = super.onCreateOptionsMenu(menu);

        final MenuItem item = menu.findItem(R.id.scan_lnurl);
        item.setOnMenuItemClickListener(menuItem -> {
            presenter.startScanLnUrlFlow();
            return true;
        });

        return showMenu;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean displayOptionsMenu = true;

        // Options menu has scan lnurl withdraw option which should be disable/invisible until we
        // have push notification permission.
        if (OS.supportsNotificationRuntimePermission()) {
            displayOptionsMenu = hasNotificationsPermission();
        }

        return displayOptionsMenu;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().showQrHeader.attachToActivity(this);
        binding().showQrHeader.showTitle(R.string.showqr_title);
        binding().showQrHeader.setNavigation(Navigation.BACK);

        // TODO avoid presenter questions, extract view state and make presenter provide it
        if (OS.supportsNotificationRuntimePermission()) {

            if (!hasNotificationsPermission() && presenter.showFirstTimeNotificationPriming())  {
                initializeUiForNotificationPrimingFirstTime();
            } else {
                initializeUiWithoutInitialNotificationPriming();
            }
        } else {
            initializeUiWithoutInitialNotificationPriming();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {

        // We've detected that sometimes onPermissionsDenied() gets called "instantaneously" right
        // after the call to requestPermissions(). This is related to the logic in
        // PermissionManagerExtension#onRequestPermissionsResult(). Instead of diving into that
        // rabbit hole, we're ignoring this event as it does nothing for us here besides messing up
        // our complex logic.
        if (deniedPermissions.length == 0) {
            return;
        }

        final boolean shouldShowRequestPermissionRationale = canShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
        );

        presenter.reportNotificationPermissionDenied(shouldShowRequestPermissionRationale);
    }

    @Override
    public void onPermissionsGranted(String[] grantedPermissions) {
        presenter.reportNotificationPermissionGranted();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void requestNotificationPermission() {
        requestPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    public void handleNotificationPermissionGranted() {

        invalidateOptionsMenu(); // Show scan lnurl option now that we have notification permission

        // This handling is perhaps a bit more complex than what you might expected.
        // In the case where we are showing "notification priming first time" we haven't yet
        // initialized all the UI components (viewPager, tabLayout, etc...), so we need to do it by
        // calling initializeUiWithoutInitialNotificationPriming(). When we aren't showing
        // notificationsPrimingView, then we have already initialized all components and we just
        // refresh the lightning state of the LnInvoiceQrFragment.
        // We do all this just to avoid redrawing stuff. It can probably be done better but, citing
        // an old wizard from Middle Earth, all we can do is "decide what to do with the time
        // that is given to us".

        if (binding().showQrNotificationsPriming.getVisibility() == View.VISIBLE) {
            initializeUiWithoutInitialNotificationPriming();
        } else {
            refreshLightningViewState();
        }
    }

    @Override
    public void handleNotificationPermissionPromptWhenPermanentlyDenied() {

        final StyledStringRes styledDesc = new StyledStringRes(
                this,
                R.string.priming_notifications_permanently_denied_dialog_desc
        );

        final MuunDialog muunDialog = new MuunDialog.Builder()
                .title(R.string.priming_notifications_permanently_denied_dialog_title)
                .message(styledDesc.toCharSequence())
                .positiveButton(R.string.go_to_settings, 0, presenter::navigateToSystemSettings)
                .negativeButton(R.string.cancel, 0, null)
                .build();

        showDialog(muunDialog);
    }

    @Override
    public ExternalResultExtension.Caller getDelegateCaller() {
        return getFragmentInDisplay();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void initializeUiForNotificationPrimingFirstTime() {
        binding().showQrViewpager.setVisibility(View.GONE);
        binding().showQrTab.setVisibility(View.GONE);

        binding().showQrNotificationsPriming.setVisibility(View.VISIBLE);
        binding().showQrNotificationsPriming.setUpForFirstTime();
        binding().showQrNotificationsPriming.setEnableClickListener(v ->
                presenter.handleNotificationPermissionPrompt()
        );
        binding().showQrNotificationsPriming.setSkipClickListener(v -> {
            presenter.reportNotificationPermissionSkipped();
            initializeUiWithoutInitialNotificationPriming();
        });
    }

    private void initializeUiWithoutInitialNotificationPriming() {
        binding().showQrNotificationsPriming.setVisibility(View.GONE);

        if (presenter.showUnifiedQr()) {
            binding().showQrViewpager.setVisibility(View.GONE);
            binding().showQrTab.setVisibility(View.GONE);
            binding().showQrViewpagerContainer.setVisibility(View.GONE);

            replaceFragment(new ShowUnifiedQrFragment(), false);
            binding().showQrUnifiedQrContainer.setVisibility(View.VISIBLE);
        } else {

            binding().showQrViewpager.setVisibility(View.VISIBLE);
            binding().showQrTab.setVisibility(View.VISIBLE);
            binding().showQrViewpagerContainer.setVisibility(View.VISIBLE);

            final ShowQrFragmentPagerAdapter adapter = new ShowQrFragmentPagerAdapter(
                    getSupportFragmentManager(),
                    this,
                    new ShowQrPager(userPreferencesSelector)
            );
            binding().showQrViewpager.setAdapter(adapter);
            binding().showQrViewpager.addOnPageChangeListener(adapter.onPageChangeListener);
            binding().showQrTab.setupWithViewPager(binding().showQrViewpager);
        }
    }

    /**
     * This is currently required for LnInvoiceQrFragment due to the fact that it resides inside a
     * FragmentPagerAdapter and doesn't receives certain fragment's lifecycle callbacks
     * (e.g onResume). Since sometimes onResume doesn't refresh the lightning state (e.g invoice) we
     * need to manually do it. ShowUnifiedQrFragment does NOT need this, since its inserted in the
     * layout "as is", and it receives these callbacks. In particular, onResume() calls presenter's
     * setUp() which makes a view.refresh().
     */
    private void refreshLightningViewState() {
        final BaseFragment<Presenter<BaseView>> fragmentInDisplay = getFragmentInDisplay();

        if (fragmentInDisplay instanceof LnInvoiceView) {
            ((LnInvoiceView) fragmentInDisplay).refresh();
        }
    }

    private <T extends Presenter<V>, V extends BaseView> BaseFragment<T> getFragmentInDisplay() {
        if (presenter.showUnifiedQr()) {
            //noinspection unchecked
            return (BaseFragment<T>) getSupportFragmentManager().getFragments().get(0);
        }

        final ShowQrFragmentPagerAdapter adapter = (ShowQrFragmentPagerAdapter) binding()
                .showQrViewpager.getAdapter();

        Preconditions.checkNotNull(adapter);

        //noinspection unchecked
        return (BaseFragment<T>) adapter
                .getExistingItem(binding().showQrViewpager.getCurrentItem());
    }
}
