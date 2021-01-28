package io.muun.apollo.presentation.ui.adapter.viewmodel;


import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;

public class ContactViewModel implements ItemViewModel {

    public final Contact model;

    public ContactViewModel(Contact model) {
        this.model = model;
    }

    @Override
    public int type(ViewHolderFactory typeFactory) {
        return typeFactory.getLayoutRes(model);
    }
}
