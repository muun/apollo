package io.muun.apollo.presentation.ui.activity.extension;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.presentation.ui.base.ActivityExtension;
import io.muun.common.utils.Preconditions;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import icepick.Bundler;
import icepick.State;

import java.util.Map;

public abstract class BaseRequestExtension extends ActivityExtension {

    @State
    int globalRequestCodeCounter = 10000; // avoid clashing with typical request codes

    public interface BaseCaller {
        int getId();
    }

    int registerRequestFromCaller(BaseCaller caller, int viewRequestCode) {
        Preconditions.checkArgument(caller.getId() != View.NO_ID);

        final CallerRequest request = new CallerRequest();
        request.viewId = caller.getId();
        request.viewRequestCode = viewRequestCode;

        final int globalRequestCode = getUniqueRequestCode();
        registerRequestFromCaller(request, globalRequestCode);

        return globalRequestCode;
    }

    protected abstract void registerRequestFromCaller(CallerRequest request, int globalRequestCode);

    @Nullable
    <T extends  BaseCaller> T findCaller(CallerRequest request, Class<T> callerType) {
        if (request == null) {
            return null;
        }

        final View view = getActivity().findViewById(request.viewId);
        if (callerType.isInstance(view)) {
            return (T) view;
        }

        final Fragment fragment =  getActivity().getSupportFragmentManager()
                .findFragmentById(request.viewId);

        if (callerType.isInstance(fragment)) {
            return (T) fragment;
        }

        if (callerType.isInstance(getActivity())) {
            return (T) getActivity();
        }

        return null;
    }

    private int getUniqueRequestCode() {
        return ++globalRequestCodeCounter;
    }

    protected static class CallerRequest {
        // Making fields public for Jackson to de/serialize
        public int viewId;
        public int viewRequestCode;
    }

    public static class RequestMapBundler implements Bundler<Map<Integer, CallerRequest>> {

        @Override
        public void put(String key, Map<Integer, CallerRequest> map, Bundle bundle) {
            bundle.putString(
                    key,
                    SerializationUtils.serializeMap(Integer.class, CallerRequest.class , map)
            );
        }

        @Override
        public Map<Integer, CallerRequest> get(String key, Bundle bundle) {
            return SerializationUtils.deserializeMap(
                    Integer.class,
                    CallerRequest.class,
                    bundle.getString(key)
            );
        }
    }
}
