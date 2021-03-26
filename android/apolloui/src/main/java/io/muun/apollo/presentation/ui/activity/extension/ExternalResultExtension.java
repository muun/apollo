package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.presentation.ui.base.di.PerActivity;

import android.content.Intent;
import androidx.fragment.app.DialogFragment;
import icepick.State;

import java.util.HashMap;
import javax.inject.Inject;

@PerActivity
public class ExternalResultExtension extends BaseRequestExtension {

    public interface Caller extends BaseRequestExtension.BaseCaller {
        void onExternalResult(int requestCode, int resultCode, Intent data);
    }

    /**
     * Yeah. Names sucks huh? This represents a Caller that can delegate the onExternalResult
     * callback to another Caller. This is mainly to workaround some of Android's delicious
     * shortcomings, for example the inability to proper set fragments ID when inside
     * ViewPager/FragmentPagerAdapter (and apparently they all have the same ID) and the hardcoded
     * fragment tags in undocumented internal code (which we would rather not (ab)use).
     * More info:
     * - https://stackoverflow.com/q/18609261/901465
     * - https://stackoverflow.com/questions/34861257/how-can-i-set-a-tag-for-viewpager-fragments
     */
    public interface DelegableCaller extends Caller {
        Caller getDelegateCaller();
    }

    /**
     * Ideally, this would go in parent class but for some reason Icepick serialisation doesn't
     * work correctly for children so we store this in both child classes.
     */
    @State(RequestMapBundler.class)
    HashMap<Integer, CallerRequest> pendingRequests = new HashMap<>();

    @Inject
    public ExternalResultExtension() {
    }

    /**
     * Show a DialogFragment, expecting back a result.
     */
    public String showDialogForResult(Caller caller, int viewRequestCode, DialogFragment dialog) {
        final int globalRequestCode = registerRequestFromCaller(caller, viewRequestCode);

        dialog.setTargetFragment(null, globalRequestCode);
        dialog.show(getActivity().getSupportFragmentManager(), "dialog-" + globalRequestCode);
        return "dialog-" + globalRequestCode;
    }

    /**
     * Start an Activity Intent, expecting back a result.
     */
    public void startActivityForResult(Caller view, int viewRequestCode, Intent intent) {
        final int globalRequestCode = registerRequestFromCaller(view, viewRequestCode);

        getActivity().startActivityForResult(intent, globalRequestCode);
    }

    @Override
    public void onActivityResult(int globalRequestCode, int resultCode, Intent data) {
        final CallerRequest request = pendingRequests.get(globalRequestCode);
        Caller view = findCaller(request, Caller.class);

        if (view == null) {
            return;
        }

        if (view instanceof DelegableCaller) {
            final DelegableCaller delegableCaller = (DelegableCaller) view;
            if (delegableCaller.getDelegateCaller() != null) {
                view = delegableCaller.getDelegateCaller();
            }
        }

        pendingRequests.remove(globalRequestCode);
        view.onExternalResult(request.viewRequestCode, resultCode, data);
    }

    @Override
    protected void registerRequestFromCaller(CallerRequest request, int globalRequestCode) {
        pendingRequests.put(globalRequestCode, request);
    }
}
