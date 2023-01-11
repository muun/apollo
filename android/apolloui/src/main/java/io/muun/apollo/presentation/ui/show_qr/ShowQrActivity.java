package io.muun.apollo.presentation.ui.show_qr;

import io.muun.apollo.R;
import io.muun.apollo.domain.selector.UserPreferencesSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.show_qr.unified.ShowUnifiedQrFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import com.google.android.material.tabs.TabLayout;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class ShowQrActivity extends SingleFragmentActivity<ShowQrPresenter> {

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

    @BindView(R.id.show_qr_header)
    MuunHeader header;

    @BindView(R.id.show_qr_tab)
    TabLayout tabLayout;

    @BindView(R.id.show_qr_viewpager)
    ViewPager viewPager;

    @BindView(R.id.show_qr_viewpager_container)
    View viewPagerContainer;

    @BindView(R.id.show_qr_unified_qr_container)
    View unifiedQrLayoutContainer;

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.show_qr_activity;
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
            onScanLnUrlClick();
            return true;
        });

        return showMenu;
    }

    private void onScanLnUrlClick() {
        presenter.startScanLnUrlFlow();
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.showqr_title);
        header.setNavigation(Navigation.BACK);

        if (presenter.showUnifiedQr()) {
            viewPager.setVisibility(View.GONE);
            tabLayout.setVisibility(View.GONE);
            viewPagerContainer.setVisibility(View.GONE);

            replaceFragment(new ShowUnifiedQrFragment(), false);
            unifiedQrLayoutContainer.setVisibility(View.VISIBLE);

        } else {

            final ShowQrFragmentPagerAdapter adapter = new ShowQrFragmentPagerAdapter(
                    getSupportFragmentManager(),
                    this,
                    new ShowQrPager(userPreferencesSelector)
            );
            viewPager.setAdapter(adapter);
            viewPager.addOnPageChangeListener(adapter.onPageChangeListener);
            tabLayout.setupWithViewPager(viewPager);

        }
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.show_qr_unified_qr_container;
    }

    @Override
    public ExternalResultExtension.Caller getDelegateCaller() {

        if (presenter.showUnifiedQr()) {
            return (BaseFragment) getSupportFragmentManager().getFragments().get(0);
        }

        final ShowQrFragmentPagerAdapter adapter = (ShowQrFragmentPagerAdapter) viewPager
                .getAdapter();

        return (BaseFragment) adapter.getExistingItem(viewPager.getCurrentItem());
    }
}
