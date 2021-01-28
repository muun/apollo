package io.muun.apollo.presentation.ui.show_qr;

import io.muun.apollo.R;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import com.google.android.material.tabs.TabLayout;

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

    @BindView(R.id.show_qr_header)
    MuunHeader header;

    @BindView(R.id.show_qr_tab)
    TabLayout tabLayout;

    @BindView(R.id.show_qr_viewpager)
    ViewPager viewPager;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void setUpExtensions() {
        super.setUpExtensions();

        // This Activity would normally be protected by the application lock (the user is logged
        // in and the Application is lock-capable at this point), but it's revealing nothing private
        // and this way we can show it using the unlock shortcut.
        getExtension(ApplicationLockExtension.class).setRequireUnlock(false);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.show_qr_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.showqr_title);
        header.setNavigation(Navigation.BACK);

        final ShowQrFragmentPagerAdapter adapter = new ShowQrFragmentPagerAdapter(
                getSupportFragmentManager(),
                this
        );
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(adapter.onPageChangeListener);

        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container; // Only used to show auxiliary (help, moreInfo) fragments
    }
}
