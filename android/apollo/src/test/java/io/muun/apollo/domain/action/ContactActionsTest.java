package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ContactsProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.exception.KeyDerivationException;

import androidx.annotation.NonNull;
import br.com.six2six.fixturefactory.Fixture;
import com.squareup.sqldelight.Query;
import com.squareup.sqldelight.db.SqlCursor;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import rx.Completable;
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

@Ignore
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
        doReturn(Completable.complete()).when(contactDao).deleteAll();

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

        final long hid = contact.getHid();
        doReturn(Observable.just(contact)).when(contactDao).fetchByHid(hid);

        final MuunAddress address =
                fetchItemFromObservable(contactActions.fetchContactAddress(hid));

        final PublicKey derivedKey =
                contact.publicKey.deriveFromAbsolutePath(address.getDerivationPath());

        assertThat(oldDerivationIndex).isLessThan(derivedKey.getLastLevelIndex());
        assertThat(address.getAddress()).isEqualTo(derivedKey.toAddress());

        verify(contactDao).updateLastDerivationIndex(hid, derivedKey.getLastLevelIndex());
    }

    @Test
    public void updateContact() {

        final Contact existingContact = Fixture.from(Contact.class).gimme("valid");
        final PublicProfile existingProfile = existingContact.publicProfile;

        final PublicProfile updatedProfile = new PublicProfile(
                null,
                existingContact.publicProfile.getHid(),
                "New First Name",
                "New Last Name",
                "https://new.profile/picture"
        );

        final Contact updatedContact = new Contact(
                null,
                existingContact.getHid(),
                updatedProfile,
                existingContact.maxAddressVersion + 1,
                TemplateHelpers.contactPublicKey().generateValue(),
                TemplateHelpers.contactPublicKey().generateValue(),
                existingContact.lastDerivationIndex + 18
        );

        doReturn(Observable.just(existingContact))
                .when(contactDao).fetchByHid(existingContact.getHid());

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
        assertThat(savedContact.getId()).isEqualTo(existingContact.getId());
        assertThat(savedContact.getHid()).isEqualTo(existingContact.getHid());

        // Modified contact fields:
        assertThat(savedContact.publicKey).isEqualTo(updatedContact.publicKey);
        assertThat(savedContact.cosigningPublicKey).isEqualTo(updatedContact.cosigningPublicKey);
        assertThat(savedContact.maxAddressVersion).isEqualTo(updatedContact.maxAddressVersion);
        assertThat(savedContact.lastDerivationIndex).isEqualTo(updatedContact.lastDerivationIndex);

        // Preserved profile fields:
        assertThat(savedProfile.getId()).isEqualTo(existingProfile.getId());
        assertThat(savedProfile.getHid()).isEqualTo(existingProfile.getHid());

        // Modified profile fields:
        assertThat(savedProfile.firstName).isEqualTo(updatedProfile.firstName);
        assertThat(savedProfile.lastName).isEqualTo(updatedProfile.lastName);
        assertThat(savedProfile.profilePictureUrl).isEqualTo(updatedProfile.profilePictureUrl);
    }

    @Test
    public void createContact() {

        final Contact contact = Fixture.from(Contact.class).gimme("valid");

        doReturn(Observable.error(new ElementNotFoundException(new Query<Contact>(null, null) {
            @NonNull
            @Override
            public SqlCursor execute() {
                return null;
            }
        })))
                .when(contactDao).fetchByHid(contact.getHid());

        fetchItemFromObservable(contactActions.createOrUpdateContact(contact));

        verify(publicProfileDao).store(contact.publicProfile);
        verify(contactDao).store(contact);
    }
}