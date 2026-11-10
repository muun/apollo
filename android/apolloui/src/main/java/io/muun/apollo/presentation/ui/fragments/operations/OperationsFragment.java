package io.muun.apollo.presentation.ui.fragments.operations;

import io.muun.apollo.R;
import io.muun.apollo.databinding.HomeOperationsFragmentBinding;
import io.muun.apollo.presentation.ui.adapter.ItemAdapter;
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel;
import io.muun.apollo.presentation.ui.adapter.viewmodel.OperationViewModel;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.DividerItemDecoration;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;
import kotlin.Unit;
import kotlin.jvm.functions.Function3;

import java.util.List;

public class OperationsFragment extends SingleFragment<OperationsPresenter>
        implements OperationsView {

    /**
     * Create new fragment instance.
     */
    public static OperationsFragment newInstance() {
        return new OperationsFragment();
    }

    private HomeOperationsFragmentBinding binding() {
        return (HomeOperationsFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return HomeOperationsFragmentBinding::inflate;
    }

    private ItemAdapter adapter;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        adapter = new ItemAdapter(new ViewHolderFactory());
        adapter.setOnItemClickListener(this::onItemClicked);

        binding().homeOperationsRecyclerOperationList
                .setLayoutManager(new LinearLayoutManager(requireContext()));
        binding().homeOperationsRecyclerOperationList
                .addItemDecoration(new DividerItemDecoration(requireContext(), 82, 0));
        binding().homeOperationsRecyclerOperationList.setAdapter(adapter);

        binding().emptyScreen.setOnLinkClickListener(ignored -> {
            presenter.goToReceive();
            return Unit.INSTANCE; // Java you suuuuuuck!
        });
    }

    @Override
    protected void setUpHeader() {
        final MuunHeader header = getParentActivity().getHeader();
        header.clear();
        header.setNavigation(MuunHeader.Navigation.EXIT);
        header.showTitle(R.string.home_operations_list_title);
        header.setElevated(true);
    }

    @Override
    public void setViewState(List<ItemViewModel> ops) {
        final boolean hasOperations = !ops.isEmpty();

        if (hasOperations) {
            setVisible(binding().homeOperationsRecyclerOperationList, true);
            setVisible(binding().emptyScreen, false);
        } else {
            setVisible(binding().homeOperationsRecyclerOperationList, false);
            setVisible(binding().emptyScreen, true);
        }

        updateListItems(ops);
    }

    private void updateListItems(List<ItemViewModel> newItems) {
        adapter.setItems(newItems);
    }

    private void onItemClicked(ItemViewModel viewModel) {
        if (viewModel instanceof OperationViewModel) {
            // TODO we should use operation.Hid() here. Involves killing/replacing deprecated
            // OperationActions#fetchOperationById()
            final Long operationId = ((OperationViewModel) viewModel).operation.getId();
            if (operationId != null) {
                presenter.onOperationClicked(operationId);
            }
        }
    }
}
