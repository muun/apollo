package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.databinding.HomeContactsItemBinding;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ContactViewModel;

import android.view.View;

public class ContactViewHolder extends BaseViewHolder<ContactViewModel> {

    private final HomeContactsItemBinding binding;

    /**
     * View holder for the contact list items.
     */
    public ContactViewHolder(View view) {
        super(view);
        binding = HomeContactsItemBinding.bind(view);
    }

    /**
     * Bind this viewHolder to a Contact.
     */
    @Override
    public void bind(ContactViewModel viewModel) {
        final Contact contact = viewModel.model;
        binding.profilePicture.setPictureUri(contact.publicProfile.profilePictureUrl);
        binding.name.setText(contact.publicProfile.getFullName());
    }
}
