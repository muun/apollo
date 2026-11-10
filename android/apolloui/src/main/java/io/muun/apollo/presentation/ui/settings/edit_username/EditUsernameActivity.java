package io.muun.apollo.presentation.ui.settings.edit_username;

import io.muun.apollo.R;
import io.muun.apollo.databinding.EditUsernameActivityBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class EditUsernameActivity extends BaseActivity<EditUsernamePresenter>
        implements EditUsernameView {

    private EditUsernameActivityBinding binding() {
        return (EditUsernameActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return EditUsernameActivityBinding::inflate;
    }

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
    protected int getMenuResource() {
        return R.menu.edit_username_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().editUsernameHeader.attachToActivity(this);
        binding().editUsernameHeader.showTitle(R.string.edit_username_title);
        binding().editUsernameHeader.setNavigation(Navigation.EXIT);
        binding().editUsernameHeader.setPadding(0, 0, UiUtils.dpToPx(this, 16), 0);

        binding().settingsEditFirstName.setOnChangeListener(this, ignored -> onInputChange());
        binding().settingsEditLastName.setOnChangeListener(this, ignored -> onInputChange());
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding().settingsEditFirstName.requestFocusInput();
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
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
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
        binding().settingsEditFirstName.setText(
                user.profile.map(UserProfile::getFirstName).orElse("")
        );
        binding().settingsEditLastName.setText(
                user.profile.map(UserProfile::getLastName).orElse("")
        );
    }

    @Override
    public void setFirstNameError(UserFacingError error) {
        binding().settingsEditFirstName.setError(error);

        if (error != null) {
            binding().settingsEditLastName.requestFocusInput();
            toggleMenuButtonEnabled(saveMenuItem, true);
        }
    }

    @Override
    public void setLastNameError(UserFacingError error) {
        binding().settingsEditLastName.setError(error);

        if (error != null) {
            binding().settingsEditLastName.requestFocusInput();
            toggleMenuButtonEnabled(saveMenuItem, true);
        }
    }

    @Override
    public void setLoading(boolean loading) {
        toggleMenuButtonEnabled(saveMenuItem, !loading);
    }

    private void onInputChange() {
        final String firstNameText = binding().settingsEditFirstName.getText().toString();
        final String lastNameText = binding().settingsEditLastName.getText().toString();

        final boolean bothNotEmpty = !firstNameText.isEmpty() && !lastNameText.isEmpty();
        toggleMenuButtonEnabled(saveMenuItem, bothNotEmpty);
    }

    private void toggleMenuButtonEnabled(MenuItem item, boolean enabled) {
        // In case this gets called before onCreateOptionsMenu
        if (item == null) {
            return;
        }

        item.setEnabled(enabled);

        final View itemView = findViewById(item.getItemId());
        if (itemView instanceof TextView) {
            // Ideally we would like to check if item is an ActionMenuItemView but is restrictedApi

            final int color = enabled
                    ? getMenuItemEnabledColor()
                    : ContextCompat.getColor(this, R.color.disabled_color);
            ((TextView) itemView).setTextColor(color);
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
        presenter.onSave(
                binding().settingsEditFirstName.getText().toString(),
                binding().settingsEditLastName.getText().toString()
        );
    }
}
