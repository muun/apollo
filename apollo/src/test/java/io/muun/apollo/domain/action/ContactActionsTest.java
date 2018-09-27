package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ContactsProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.NotificationService;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.exception.KeyDerivationException;

import br.com.six2six.fixturefactory.Fixture;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.List;
import java.util.concurrent.Executors;

import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ContactActionsTest extends BaseTest {

    @Mock
    private AsyncActionStore asyncActionStore;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private ContactDao contactDao;

    @Mock
    private PublicProfileDao publicProfileDao;

    @Mock
    private ContactsProvider contactsProvider;

    @Mock
    private PhoneContactDao phoneContactDao;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    ExecutionTransformerFactory executionTransformerFactory = new ExecutionTransformerFactory(
            Executors.newSingleThreadExecutor(),
            Schedulers.newThread()
    );

    private ContactActions contactActions;

    @Before
    public void setUp() {

        doAnswer(invocation -> Observable.just(invocation.getArgument(0)))
                .when(publicProfileDao).store(any());

        doAnswer(invocation -> Observable.just(invocation.getArgument(0)))
                .when(contactDao).store(any());

        contactActions = spy(new ContactActions(
                asyncActionStore,
                houstonClient,
                contactDao,
                publicProfileDao,
                contactsProvider,
                phoneContactDao,
                userRepository,
                executionTransformerFactory,
                notificationService,
                TestNet3Params.get()
        ));
    }

    @Test
    public void fetchReplaceContacts() {

        final List<Contact> remote = Fixture.from(Contact.class).gimme(3, "valid");

        doReturn(Observable.just(remote)).when(houstonClient).fetchContacts();
        doReturn(Observable.just(0)).when(contactDao).deleteAll();

        doReturn(Observable.just(null)).when(contactActions).createOrUpdateContact(any());

        fetchItemFromObservable(contactActions.fetchReplaceContacts());

        verify(contactActions, times(remote.size()))
                .createOrUpdateContact(argThat(remote::contains));
    }

    @Ignore
    @Test
    public void fetchContactAddress() throws KeyDerivationException {

        final Contact contact = Fixture.from(Contact.class).gimme("valid");
        final long oldDerivationIndex = contact.lastDerivationIndex;

        doReturn(Observable.just(contact)).when(contactDao).fetchByHid(contact.hid);

        final MuunAddress address =
                fetchItemFromObservable(contactActions.fetchContactAddress(contact.hid));

        final PublicKey derivedKey =
                contact.publicKey.deriveFromAbsolutePath(address.getDerivationPath());

        assertThat(oldDerivationIndex).isLessThan(derivedKey.getLastLevelIndex());
        assertThat(address.getAddress()).isEqualTo(derivedKey.toAddress());

        verify(contactDao).updateLastDerivationIndex(contact.hid, derivedKey.getLastLevelIndex());
    }

    @Test
    public void updateContact() {

        final Contact existingContact = Fixture.from(Contact.class).gimme("valid");
        final PublicProfile existingProfile = existingContact.publicProfile;

        final PublicProfile updatedProfile = new PublicProfile(
                null,
                existingContact.publicProfile.hid,
                "New First Name",
                "New Last Name",
                "https://new.profile/picture"
        );

        final Contact updatedContact = new Contact(
                null,
                existingContact.hid,
                updatedProfile,
                existingContact.maxAddressVersion + 1,
                TemplateHelpers.contactPublicKey().generateValue(),
                TemplateHelpers.contactPublicKey().generateValue(),
                existingContact.lastDerivationIndex + 18
        );

        doReturn(Observable.just(existingContact)).when(contactDao).fetchByHid(existingContact.hid);

        fetchItemFromObservable(contactActions.createOrUpdateContact(updatedContact));

        // TODO the following logic belongs in a model test. Temporarily hosted here. When moved,
        // just verify the `mergeWithUpdate()` calls in Contact and PublicProfile were made:
        final ArgumentCaptor<PublicProfile> profileCaptor = ArgumentCaptor
                .forClass(PublicProfile.class);

        final ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor
                .forClass(Contact.class);

        verify(publicProfileDao, times(1)).store(profileCaptor.capture());
        verify(contactDao, times(1)).store(contactCaptor.capture());

        final Contact savedContact = contactCaptor.getValue();
        final PublicProfile savedProfile = profileCaptor.getValue();

        // Preserved contact fields:
        assertThat(savedContact.id).isEqualTo(existingContact.id);
        assertThat(savedContact.hid).isEqualTo(existingContact.hid);

        // Modified contact fields:
        assertThat(savedContact.publicKey).isEqualTo(updatedContact.publicKey);
        assertThat(savedContact.cosigningPublicKey).isEqualTo(updatedContact.cosigningPublicKey);
        assertThat(savedContact.maxAddressVersion).isEqualTo(updatedContact.maxAddressVersion);
        assertThat(savedContact.lastDerivationIndex).isEqualTo(updatedContact.lastDerivationIndex);

        // Preserved profile fields:
        assertThat(savedProfile.id).isEqualTo(existingProfile.id);
        assertThat(savedProfile.hid).isEqualTo(existingProfile.hid);

        // Modified profile fields:
        assertThat(savedProfile.firstName).isEqualTo(updatedProfile.firstName);
        assertThat(savedProfile.lastName).isEqualTo(updatedProfile.lastName);
        assertThat(savedProfile.profilePictureUrl).isEqualTo(updatedProfile.profilePictureUrl);
    }

    @Test
    public void createContact() {

        final Contact contact = Fixture.from(Contact.class).gimme("valid");

        doReturn(Observable.error(new ElementNotFoundException("")))
                .when(contactDao).fetchByHid(contact.hid);

        fetchItemFromObservable(contactActions.createOrUpdateContact(contact));

        verify(publicProfileDao).store(contact.publicProfile);
        verify(contactDao).store(contact);
    }
}