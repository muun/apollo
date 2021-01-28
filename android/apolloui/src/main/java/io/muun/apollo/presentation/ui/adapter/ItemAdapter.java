package io.muun.apollo.presentation.ui.adapter;

import io.muun.apollo.presentation.ui.adapter.holder.BaseViewHolder;
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class ItemAdapter extends RecyclerView.Adapter<BaseViewHolder<ItemViewModel>> {

    public interface SearchCriteria {
        boolean filter(ItemViewModel viewModel, String query);
    }

    public interface OnItemClickListener {
        void onItemClick(ItemViewModel item);
    }

    private final ViewHolderFactory viewHolderFactory;

    private List<ItemViewModel> items;
    private List<ItemViewModel> unfilteredItems;
    private OnItemClickListener onItemClickListener;

    /**
     * Constructor with ViewHolderFactory.
     */
    public ItemAdapter(ViewHolderFactory viewHolderFactory) {
        this.viewHolderFactory = viewHolderFactory;

        this.items = new ArrayList<>();
        this.unfilteredItems = new ArrayList<>();
        this.onItemClickListener = (item) -> { };
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public BaseViewHolder<ItemViewModel> onCreateViewHolder(ViewGroup parent, int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false);

        itemView.setClickable(true);

        return viewHolderFactory.create(viewType, itemView);
    }

    @Override
    public void onBindViewHolder(BaseViewHolder<ItemViewModel> holder, int position) {
        final ItemViewModel item = items.get(position);

        holder.bind(item);
        holder.itemView.setOnClickListener(view -> onItemClick(item));
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type(viewHolderFactory);
    }

    public List<ItemViewModel> getItems() {
        return items;
    }

    /**
     * Get the first item of a given ItemViewModel class, or null.
     */
    @Nullable
    public <T extends ItemViewModel> T getFirstOfType(Class<T> cls) {
        for (final ItemViewModel item: items) {
            if (cls.isInstance(item)) {
                return (T) item;
            }
        }

        return null;
    }

    /**
     * Get the number of a items of a given ItemViewModel class.
     */
    public <T extends ItemViewModel> int getCountOfType(Class<T> cls) {
        int count = 0;

        for (final ItemViewModel item: items) {
            if (cls.isInstance(item)) {
                count += 1;
            }
        }

        return count;
    }

    /**
     * Get the index for the first item of a given ItemViewModel class, or -1.
     */
    public <T extends ItemViewModel> int getFirstIndexOfType(Class<T> cls) {
        for (int i = 0; i < items.size(); i++) {
            if (cls.isInstance(items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Set this adapter's items and notifyDataSetChanged.
     */
    public void setItems(List<ItemViewModel> items) {
        setItemsWithoutNotifying(items);
        notifyDataSetChanged();
    }

    /**
     * Set this adapter's items.
     */
    public void setItemsWithoutNotifying(List<ItemViewModel> items) {
        this.items = items;
        this.unfilteredItems = new LinkedList<>(items);
    }

    /**
     * Clear this adapter's items.
     */
    public void clear() {
        this.items.clear();
        this.unfilteredItems.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Prepend all of the elements in the specified collection to the beginning of
     * this adapter.
     */
    public void addFirst(List<ItemViewModel> items) {
        addItems(0, items);
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this adapter.
     */
    public void addItems(List<ItemViewModel> items) {
        addItems(getItemCount(), items);
    }

    /**
     * Add items to this adapter.
     */
    protected void addItems(int index, List<ItemViewModel> items) {
        this.items.addAll(index, items);
        this.unfilteredItems.addAll(index, items);
        notifyDataSetChanged();
    }

    /**
     * Filter this adapter's items according to a query string.
     */
    public void filter(SearchCriteria criteria, String query) {
        final Set<ItemViewModel> orderedSet = new LinkedHashSet<>();

        for (ItemViewModel item : unfilteredItems) {
            if (criteria.filter(item, query)) {
                orderedSet.add(item);
            }
        }

        this.items = new ArrayList<>(orderedSet);
        notifyDataSetChanged();
    }

    public void resetFilter() {
        setItems(unfilteredItems);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    private void onItemClick(ItemViewModel item) {
        onItemClickListener.onItemClick(item);
    }
}
