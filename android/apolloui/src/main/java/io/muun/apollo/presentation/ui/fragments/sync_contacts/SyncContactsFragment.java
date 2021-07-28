package io.muun.apollo.presentation.ui.fragments.sync_contacts;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.RichText;

import android.Manifest;
import android.text.TextUtils;
import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

public class SyncContactsFragment extends SingleFragment<SyncContactsPresenter>
        implements SyncContactsView {

    @BindView(R.id.sync_contacts_explanation)
    HtmlTextView explanation;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.sync_contacts_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final String howItWorkText = getString(R.string.sync_contacts_how_it_works);

        final CharSequence content = TextUtils.concat(
                getString(R.string.sync_contacts_explanation),
                " ",
                new RichText(howItWorkText).setLink(this::onHowItWorksTextClick)
        );

        explanation.setText(content);

        hideKeyboard(view);
    }

    @OnClick(R.id.sync_contacts_button)
    void onButtonClick() {
        requestPermissions(Manifest.permission.READ_CONTACTS);
    }

    @Override
    public void onPermissionsGranted(String[] grantedPermissions) {
        presenter.reportContactsPermissionGranted();
    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {

        final boolean canRequestPermissionRationale = canShowRequestPermissionRationale(
                Manifest.permission.READ_CONTACTS
        );

        if (! canRequestPermissionRationale) {
            // User has checked never ask again
            presenter.reportContactsPermissionNeverAskAgain();
        }
    }

    private void onHowItWorksTextClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.sync_contacts_how_it_works_title);
        dialog.setDescription(getString(R.string.sync_contacts_how_it_works_title_description));
        dialog.show(getParentFragmentManager(), null);

        presenter.reportShowReadContactsInfo();
    }
}
