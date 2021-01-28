package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.domain.errors.BugDetected;
import io.muun.apollo.presentation.ui.activity.extension.ExternalResultExtension.Caller;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension;
import io.muun.apollo.presentation.ui.activity.extension.PermissionManagerExtension.PermissionRequester;
import io.muun.apollo.presentation.ui.base.BaseActivity;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.fragment.app.DialogFragment;
import butterknife.ButterKnife;
import icepick.Icepick;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nullable;


public abstract class MuunView extends FrameLayout implements Caller, PermissionRequester {

    private static final String OWN_STATE = "own-state";
    private static final String CHILD_STATE = "child-state";

    public MuunView(Context context) {
        super(context);
        setUp(context, null);
    }

    public MuunView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp(context, attrs);
    }

    public MuunView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setUp(context, attrs);
    }

    @LayoutRes
    protected abstract int getLayoutResource();

    @CallSuper
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        final LayoutInflater inflater = (LayoutInflater)
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // When attachToRoot is false, inflate returns innerView, otherwise it returns
        // parentView (in this case, `this`). See its javadoc
        final View innerView = inflater.inflate(getLayoutResource(), this, false);

        addView(innerView);
        ButterKnife.bind(this, innerView);

        // WAIT: when did we change the code to add this `innerView` nonsense, instead of using
        // ourselves as the root layout like in the good old times?

        // Well, glad you asked. As you know, one of the guarantees of MuunView is that internal IDs
        // will never cause problems if the same IDs are assigned to our ancestors or siblings in
        // the view tree (which may be other instances of this class, with every ID shared).

        // This is critical to the processes of finding children and saving/restoring state. View
        // parents can do whatever they want, we handle our internal tree. We're smart that way.

        // This ran into a silly problem: what if our parent assigns an ID to THIS VERY VIEW, and
        // that ID is also present in our internal tree? See, when finding a view by ID, the first
        // attempted match is the view doing the finding (that's us), so we're going to match
        // before any of our children. Yeah, this actually happened during development.

        // The solution was changing the binding to use the inflated child as the binding source.

        // In doing so, we split the layout inflation in two steps: inflate and addView(innerView).
        // This has the disadvantage of banning the use of <merge> (when our first child is actually
        // us).

        // So, a lot of lessons learned and a huge comment left mostly because I wanted to get it
        // out of my chest. Hope you enjoyed this broadcast.
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        // Our children may have XML IDs that MuunView subclasses might want to pick up. Two
        // instances of the same subclass will thus share their internal IDs. For example:

        // <MuunViewSubclass>
        //   <EditText android:id="foo" />
        // </>

        // If there are two instances of this View in the same layout, they will have problems
        // persisting and restoring state. They will compete to save their state, overwriting each
        // other, and then they will both restore the *same* state (originally belonging to one
        // of the two). This is not theoretical, it happens on recreation and config changes,
        // systematically.

        // What we need to do is save the state of MuunView children manually, without resorting
        // to the "global" (per-layout) ID-to-state map. As long as the MuunView itself has a
        // unique ID, nothing will clash.

        final Bundle state = new Bundle();

        final Parcelable ownState = Icepick.saveInstanceState(this, super.onSaveInstanceState());
        final SparseArray<Parcelable> childState = new SparseArray<>();

        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).saveHierarchyState(childState);
        }

        state.putParcelable(OWN_STATE, ownState);
        state.putSparseParcelableArray(CHILD_STATE, childState);

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        if (parcelable instanceof Bundle) {
            final Bundle state = (Bundle) parcelable;

            final Parcelable ownState = state.getParcelable(OWN_STATE);
            final SparseArray<Parcelable> childState = state.getSparseParcelableArray(CHILD_STATE);

            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).restoreHierarchyState(childState);
            }

            super.onRestoreInstanceState(Icepick.restoreInstanceState(this, ownState));

        } else {
            super.onRestoreInstanceState(parcelable);
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        // Do not propagate default behavior to children, since we'll handle this ourselves.
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // Do not propagate default behavior to children, since we'll handle this ourselves.
        dispatchThawSelfOnly(container);
    }

    protected ViewGroup getRoot() {
        return (ViewGroup) getChildAt(0);
    }

    // Taken from Android Support Library: https://goo.gl/a8RM4a
    protected BaseActivity<?> getActivity() {
        Context context = getContext();

        while (context instanceof ContextWrapper) {
            if (context instanceof BaseActivity) {
                return (BaseActivity) context;
            }

            context = ((ContextWrapper) context).getBaseContext();
        }

        Timber.e(new BugDetected(
                "MuunView's context is not a FragmentActivity in " + this.getClass().getSimpleName()
        ));
        return null;
    }

    protected <T extends View> List<T> findViewsByTag(String tag) {
        final List<T> results = new LinkedList<>();
        addViewsByTagRecursively(tag, this, results);

        return new ArrayList<>(results); // caller can index in O(1)
    }

    private <T extends View> void addViewsByTagRecursively(String tag,
                                                           ViewGroup root,
                                                           List<T> results) {
        final int childCount = root.getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = root.getChildAt(i);

            if (tag.equals(child.getTag())) {
                results.add((T) child);
            }

            if (child instanceof ViewGroup) {
                addViewsByTagRecursively(tag, (ViewGroup) child, results);
            }
        }
    }

    protected void showDrawerDialog(DialogFragment dialog) {
        getActivity().showDrawerDialog(dialog);
    }

    protected final void requestExternalResult(int requestCode, Intent intent) {
        getActivity().requestExternalResult(this, requestCode, intent);
    }

    protected final void requestExternalResult(int requestCode, DialogFragment dialog) {
        getActivity().requestExternalResult(this, requestCode, dialog);
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
    }

    protected final void requestPermissions(String... permissions) {
        getActivity().requestPermissions(this, permissions);
    }

    protected final boolean allPermissionsGranted(String... permissions) {
        return getActivity().allPermissionsGranted(permissions);
    }

    /**
     * Gets whether you should show UI with rationale for requesting a permission.
     * Return false if the permission was denied with the 'Never ask again' checkbox checked.
     * See: {@link PermissionManagerExtension#canShowRequestPermissionRationale(String)}
     */
    protected final boolean canShowRequestPermissionRationale(String permission) {
        return getActivity().canShowRequestPermissionRationale(permission);
    }

    @Override
    public void onPermissionsGranted(String[] grantedPermissions) {

    }

    @Override
    public void onPermissionsDenied(String[] deniedPermissions) {

    }
}
