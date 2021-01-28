package io.muun.apollo.presentation.ui.view;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class MuunViewHolder<T extends View> extends RecyclerView.ViewHolder {

    private final T view;

    public MuunViewHolder(T view) {
        super(view);
        this.view = view;
    }

    public T getView() {
        return view;
    }
}
