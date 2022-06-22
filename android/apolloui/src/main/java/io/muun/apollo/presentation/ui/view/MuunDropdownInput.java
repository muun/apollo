package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.OS;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;
import butterknife.BindColor;
import butterknife.BindDrawable;
import butterknife.BindView;
import rx.functions.Func1;

import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

public class MuunDropdownInput<T> extends MuunView {

    public interface OnChangeListener<Item> {
        /**
         * This method will be called when a new item has been selected.
         */
        void onSelectionChange(@Nullable Item item);
    }

    @BindView(R.id.muun_dropdown_input_spinner)
    protected Spinner spinner;

    @BindDrawable(R.drawable.bg_muun_dropdown)
    protected Drawable spinnerBkg;

    @BindColor(R.color.muun_spinner_drawable_color)
    protected ColorStateList spinnerBkgTintList;

    private DropdownAdapter adapter;

    private boolean notifyChanges = true;
    private OnChangeListener<T> changeListener;

    public MuunDropdownInput(Context context) {
        super(context);
    }

    public MuunDropdownInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunDropdownInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_dropdown_input;
    }

    @LayoutRes
    protected int getItemLayoutResource() {
        return R.layout.muun_dropdown_input_item;
    }

    @LayoutRes
    protected int getSelectedItemLayoutResource() {
        return getItemLayoutResource();
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        spinner.setOnItemSelectedListener(createSpinnerListener());

        adapter = new DropdownAdapter(context);
        spinner.setAdapter(adapter);

        setSpinnerBackground();
    }

    private void setSpinnerBackground() {
        // Doing this here in code as we can't easily and reliably do it via xml
        // Head's up we maaaaay have to adjust this to support rtl layouts
        Drawable drawable = spinnerBkg;
        if (OS.supportsBackgroundTintList()) {
            spinner.setBackgroundTintList(spinnerBkgTintList);
        } else {
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(drawable, spinnerBkgTintList);
        }
        spinner.setBackground(drawable);
    }

    protected void setUpItem(T item, View itemView) {
        ((TextView) itemView).setText(item.toString());
    }

    protected void setUpSelectedItem(T item, View itemView) {
        setUpItem(item, itemView);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        spinner.setDropDownVerticalOffset(height);
    }

    private AdapterView.OnItemSelectedListener createSpinnerListener() {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (changeListener != null && notifyChanges) {
                    changeListener.onSelectionChange(adapter.getItem(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (changeListener != null && notifyChanges) {
                    changeListener.onSelectionChange(null);
                }
            }
        };
    }

    /**
     * Set the options for this dropdown, preserving selection if possible.
     */
    public void setItems(List<T> items) {
        final T selectedItem = getSelection();

        adapter.clear();
        adapter.addAll(items);
        adapter.notifyDataSetChanged();

        if (selectedItem != null) {
            final int newPosition = adapter.getPosition(selectedItem);

            if (newPosition > -1) {
                setSelectionSkipListener(newPosition);
            } else {
                setSelection(null);
            }
        }
    }

    /**
     * Sort the items in this dropdown.
     */
    public void sortItems(Comparator<T> comparator) {
        adapter.sort(comparator);
    }

    /**
     * Set the view resource ID to render each Item in this dropdown.
     */
    public void setItemView(int resourceId) {
        adapter.setDropDownViewResource(resourceId);
    }

    /**
     * Get the Item at a given position.
     */
    public T getItem(int position) {
        return adapter.getItem(position);
    }

    /**
     * Get the number of available items.
     */
    public int getItemCount() {
        return adapter.getCount();
    }

    /**
     * Find the first Item that satisfies a predicate (or return null).
     */
    public T findItem(Func1<T, Boolean> predicate) {
        for (int i = 0; i < adapter.getCount(); i++) {
            final T item = adapter.getItem(i);

            if (predicate.call(item)) {
                return item;
            }
        }

        return null;
    }

    /**
     * Set a Listener to be invoked with an Item (or null) when selection changes.
     */
    public void setOnChangeListener(OnChangeListener<T> listener) {
        changeListener = listener;
    }

    /**
     * Manually set the selected Item, by position.
     */
    public void setSelection(int position) {
        spinner.setSelection(position);
    }

    /**
     * Manually set the selected Item, given an instance.
     */
    public void setSelection(T item) {
        spinner.setSelection(adapter.getPosition(item));
    }

    @SuppressWarnings("unchecked")
    public T getSelection() {
        return (T) spinner.getSelectedItem();
    }

    /**
     * Manually set the selected Item by position, without notifying listeners.
     */
    public void setSelectionSkipListener(int position) {
        notifyChanges = false;
        setSelection(position);

        // Unlike other standard Views, Spinner changes value asynchronously. It hasn't processed
        // the call to `setSelection()` yet, so if we restore the `notifyChanges` flag to `true`
        // right now (as we do in `MuunTextInput`), listeners will be called later.

        // Yeah. This is going to be ugly.

        // We need to wait until the change is processed, and then until listeners are fired. This
        // amounts to postponing the reset of `notifyChanges` *twice* in the event queue our Spinner
        // is using:
        spinner.post(() -> spinner.post(() -> notifyChanges = true));
    }

    /**
     * Manually set the selected Item given an instance, without notifying listeners.
     */
    public void setSelectionSkipListener(T item) {
        setSelectionSkipListener(adapter.getPosition(item));
    }

    private class DropdownAdapter extends ArrayAdapter<T> {

        private final LayoutInflater inflater;

        public DropdownAdapter(@NonNull Context context) {
            super(context, R.layout.muun_dropdown_input_item);
            inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            final View selectedItemView = (convertView != null)
                    ? convertView
                    : inflater.inflate(getSelectedItemLayoutResource(), parent, false);

            selectedItemView.setLayoutParams(new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            selectedItemView.setPadding(0, 0, 0, 0);

            setUpSelectedItem(adapter.getItem(position), selectedItemView);

            return selectedItemView;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
            final View itemView = (convertView != null)
                    ? convertView
                    : inflater.inflate(getItemLayoutResource(), parent, false);

            setUpItem(adapter.getItem(position), itemView);

            return itemView;
        }
    }
}
