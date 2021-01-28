package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ContactViewModel;
import io.muun.apollo.presentation.ui.view.ProfilePictureView;

import android.view.View;
import android.widget.TextView;
import butterknife.BindView;

public class ContactViewHolder extends BaseViewHolder<ContactViewModel> {

    @BindView(R.id.home_contacts_item_profile_picture)
    protected ProfilePictureView profilePicture;

    @BindView(R.id.home_contacts_item_text_full_name)
    protected TextView name;

    /**
     * View holder for the contact list items.
     */
    public ContactViewHolder(View view) {
        super(view);
    }

    /**
     * Bind this viewHolder to a Contact.
     */
    @Override
    public void bind(ContactViewModel viewModel) {
        final Contact contact = viewModel.model;
        profilePicture.setPictureUri(contact.publicProfile.profilePictureUrl);
        name.setText(contact.publicProfile.getFullName());
    }
}
