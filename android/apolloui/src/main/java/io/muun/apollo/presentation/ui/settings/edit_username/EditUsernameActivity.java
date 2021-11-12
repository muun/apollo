package io.muun.apollo.presentation.ui.settings.edit_username;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunTextInput;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.view.menu.ActionMenuItemView;
import butterknife.BindColor;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class EditUsernameActivity extends BaseActivity<EditUsernamePresenter>
        implements EditUsernameView {

    @BindView(R.id.edit_username_header)
    MuunHeader header;

    @BindView(R.id.settings_edit_first_name)
    MuunTextInput firstName;

    @BindView(R.id.settings_edit_last_name)
    MuunTextInput lastName;

    @BindColor(R.color.disabled_color)
    int disabledColor;

    private MenuItem saveMenuItem;

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, EditUsernameActivity.class);
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.edit_username_activity;
    }

    @Override
    protected int getMenuResource() {
        return R.menu.edit_username_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.edit_username_title);
        header.setNavigation(Navigation.EXIT);
        header.setPadding(0, 0, UiUtils.dpToPx(this, 16), 0);

        firstName.setOnChangeListener(ignored -> onInputChange());
        lastName.setOnChangeListener(ignored -> onInputChange());
    }

    @Override
    protected void onResume() {
        super.onResume();
        firstName.requestFocusInput();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean showMenu = super.onCreateOptionsMenu(menu);

        saveMenuItem = menu.findItem(R.id.edit_username_save);

        // Need a post here as item view is not added to view hierarchy just yet.
        // I know, hackish, if you find a better way please save me from this one.
        new Handler().post(() -> {
            final View itemview = findViewById(R.id.edit_username_save);
            UiUtils.setRippleBackground(this, itemview);
        });

        return showMenu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.edit_username_save:

                toggleMenuButtonEnabled(item, false);

                onSave();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void setUsername(User user) {
        firstName.setText(user.profile.map(UserProfile::getFirstName).orElse(""));
        lastName.setText(user.profile.map(UserProfile::getLastName).orElse(""));
    }

    @Override
    public void setFirstNameError(UserFacingError error) {
        firstName.setError(error);

        if (error != null) {
            lastName.requestFocusInput();
            toggleMenuButtonEnabled(saveMenuItem, true);
        }
    }

    @Override
    public void setLastNameError(UserFacingError error) {
        lastName.setError(error);

        if (error != null) {
            lastName.requestFocusInput();
            toggleMenuButtonEnabled(saveMenuItem, true);
        }
    }

    @Override
    public void setLoading(boolean loading) {
        toggleMenuButtonEnabled(saveMenuItem, !loading);
    }

    private void onInputChange() {
        final String firstNameText = firstName.getText().toString();
        final String lastNameText = lastName.getText().toString();

        final boolean bothNotEmpty = firstNameText.length() > 0 && lastNameText.length() > 0;
        toggleMenuButtonEnabled(saveMenuItem, bothNotEmpty);
    }

    private void toggleMenuButtonEnabled(MenuItem item, boolean enabled) {
        // In case this gets called before onCreateOptionsMenu
        if (item == null) {
            return;
        }

        item.setEnabled(enabled);

        final View itemView = findViewById(item.getItemId());
        if (itemView instanceof ActionMenuItemView) {

            final int color = enabled ? getMenuItemEnabledColor() : disabledColor;
            ((ActionMenuItemView) itemView).setTextColor(color);

        }
    }

    private int getMenuItemEnabledColor() {
        return UiUtils.getColorAttrValueFromStyle(
                this,
                R.style.MuunActionBarStyle,
                android.R.attr.actionMenuTextColor
        );
    }

    private void onSave() {
        presenter.onSave(firstName.getText().toString(), lastName.getText().toString());
    }
}
