package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.activity.extension.OverflowMenuExtension;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import rx.functions.Action1;

import java.util.ArrayList;
import java.util.List;

public abstract class ExtensibleActivity extends AppCompatActivity {

    private List<ActivityExtension> extensions = new ArrayList<>();

    protected abstract void setUpExtensions();

    protected void addExtension(ActivityExtension extension) {
        extension.setActivity(this);
        extensions.add(extension);
    }

    /**
     * Return an Extension previously added to this Activity.
     */
    public <T extends ActivityExtension> T getExtension(Class<T> extensionClass) {
        for (ActivityExtension extension : extensions) {
            if (extensionClass.isInstance(extension)) {
                return extensionClass.cast(extension);
            }
        }

        throw new IllegalArgumentException("Missing extension " + extensionClass.getSimpleName());
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setUpExtensions();
        forEachExtension(extension -> extension.onCreate(savedInstanceState));
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        forEachExtension(extension -> extension.onSaveInstanceState(outState));
    }

    @Override
    @CallSuper
    protected void onStart() {
        super.onStart();
        forEachExtension(ActivityExtension::onStart);

        // Trigger onPrepareOptionsMenu every time (otherwise it is not or may not be called when
        // navigating back to an activity). See https://stackoverflow.com/q/32376928/901465
        // In particular, OverflowMenuExtension assumes onPrepareOptionsMenu behaves like this
        supportInvalidateOptionsMenu();
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        forEachExtension(ActivityExtension::onResume);
    }

    @Override
    @CallSuper
    protected void onPause() {
        super.onPause();
        forEachExtension(ActivityExtension::onPause);
    }

    @Override
    @CallSuper
    protected void onStop() {
        super.onStop();
        forEachExtension(ActivityExtension::onStop);
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        super.onDestroy();
        forEachExtension(ActivityExtension::onDestroy);
    }

    @Override
    @CallSuper
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        forEachExtension(extension -> extension.onActivityResult(requestCode, resultCode, data));
    }

    @Override
    @CallSuper
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        forEachExtension(extension ->
                extension.onRequestPermissionsResult(requestCode, permissions, grantResults)
        );
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        getExtension(OverflowMenuExtension.class).onPrepareOptionsMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (getExtension(OverflowMenuExtension.class).onOptionsItemSelected(item)) {
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    protected void forEachExtension(Action1<ActivityExtension> action) {
        for (ActivityExtension extension : extensions) {
            action.call(extension);
        }
    }
}
