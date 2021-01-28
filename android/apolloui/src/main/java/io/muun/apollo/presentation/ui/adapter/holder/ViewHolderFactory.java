package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.presentation.model.CurrencyItem;
import io.muun.apollo.presentation.model.UiOperation;
import io.muun.apollo.presentation.ui.adapter.viewmodel.SectionHeaderViewModel.SectionHeader;

import android.view.View;

public class ViewHolderFactory {

    public int getLayoutRes(SectionHeader sectionHeader) {
        return R.layout.v_item_section_header;
    }

    public int getLayoutRes(CurrencyItem currency) {
        return R.layout.v_item_currency;
    }

    public int getLayoutRes(Contact model) {
        return R.layout.home_contacts_item;
    }

    public int getLayoutRes(UiOperation model) {
        return R.layout.home_operations_item;
    }

    /**
     * Maps viewType to ViewHolder type. This way all the information about adapter's viewTypes
     * is encapsulated in this class. (Using layout ids as recommended by Google).
     */
    public BaseViewHolder create(int viewType, View view) {
        switch (viewType) {
            case R.layout.v_item_currency:
                return new CurrencyViewHolder(view);

            case R.layout.v_item_section_header:
                return new SectionHeaderViewHolder(view);

            case R.layout.home_contacts_item:
                return new ContactViewHolder(view);

            case R.layout.home_operations_item:
                return new OperationViewHolder(view);

            default:
                throw new RuntimeException("Illegal view type");
        }
    }
}
