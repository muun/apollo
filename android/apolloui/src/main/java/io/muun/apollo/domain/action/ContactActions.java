package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.contact.ContactDao;
import io.muun.apollo.data.db.phone_contact.PhoneContactDao;
import io.muun.apollo.data.db.public_profile.PublicProfileDao;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.os.ContactsProvider;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.MultisigContact;
import io.muun.apollo.domain.model.PhoneContact;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.model.Diff;
import io.muun.common.model.PhoneNumber;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.Preconditions;

import libwallet.Libwallet;
import org.bitcoinj.core.NetworkParameters;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func0;
import timber.log.Timber;

import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class ContactActions {

    private static final Object addressCreationLock = new Object();

    private final HoustonClient houstonClient;

    private final ContactDao contactDao;

    private final PublicProfileDao publicProfileDao;

    private final ContactsProvider contactsProvider;

    private final PhoneContactDao phoneContactDao;

    private final UserRepository userRepository;

    private final ExecutionTransformerFactory transformerFactory;

    private final NotificationService notificationService;

    private final NetworkParameters networkParameters;

    private static Subscription phoneContactsAutoSyncSub;

    public final AsyncAction0<Void> initialSyncPhoneContactsAction;

    /**
     * Constructor.
     */
    @Inject
    public ContactActions(AsyncActionStore asyncActionStore,
                          HoustonClient houstonClient,
                          ContactDao contactDao,
                          PublicProfileDao publicProfileDao,
                          ContactsProvider contactsProvider,
                          PhoneContactDao phoneContactDao,
                          UserRepository userRepository,
                          ExecutionTransformerFactory transformerFactory,
                          NotificationService notificationService,
                          NetworkParameters networkParameters) {

        this.houstonClient = houstonClient;
        this.contactDao = contactDao;
        this.publicProfileDao = publicProfileDao;
        this.contactsProvider = contactsProvider;
        this.phoneContactDao = phoneContactDao;
        this.userRepository = userRepository;
        this.transformerFactory = transformerFactory;
        this.notificationService = notificationService;
        this.networkParameters = networkParameters;

        this.initialSyncPhoneContactsAction =
            asyncActionStore.get("user/intialSyncContacts",
                    (Func0<Observable<Void>>) this::syncPhoneContacts);
    }

    /**
     * Fetch the complete contact list from Houston.
     */
    public Observable<Void> fetchReplaceContacts() {
        Timber.d("[Contacts] Fetching full contact list");

        return contactDao.deleteAll().andThen(
                houstonClient.fetchContacts()
                        .flatMap(Observable::from)
                        // using concatMap to avoid parallelization, overflows JobExecutor's queue
                        // TODO use batching
                        .concatMap(this::createOrUpdateContact)
                        .lastOrDefault(null)
        );
    }

    /**
     * Start a background watcher of PhoneContacts, automatically syncing with Houston on changes.
     * Safe to call multiple times.
     */
    public synchronized void startPhoneContactsAutoSync() {
        if (phoneContactsAutoSyncSub != null && !phoneContactsAutoSyncSub.isUnsubscribed()) {
            return;
        }

        Timber.d("[Contacts] Watch started");

        phoneContactsAutoSyncSub = contactsProvider
                .watchContactChanges()
                .debounce(2, TimeUnit.SECONDS) // changes instantly trigger more than one event
                .onBackpressureBuffer(200)
                .concatMap(uselessUriAndroidPlease -> {
                    Timber.d("[Contacts] Watch triggered sync");

                    return syncPhoneContacts().onErrorReturn(error -> {
                        Timber.e(error);
                        return null;
                    });
                })
                .compose(transformerFactory.getAsyncExecutor())
                .subscribe();
    }

    /**
     * Sync the local PhoneContact table with the address book, and upload changes to watched
     * phone numbers.
     */
    public Observable<Void> syncPhoneContacts() {
        return userRepository.fetch()
                .first()
                .flatMap(user -> {
                    if (user.hasP2PEnabled) {
                        return syncPhoneContacts(user.phoneNumber.get());
                    } else {
                        return Observable.just(null);
                    }
                });
    }

    private Observable<Void> syncPhoneContacts(@NotNull UserPhoneNumber phoneNumber) {
        return Observable.defer(() -> {
            final String defaultAreaCode = getDefaultAreaCode(phoneNumber);
            final String defaultRegionCode = phoneNumber.getCountryCode();

            final long currentTs = System.currentTimeMillis();

            final List<PhoneContact> phoneContacts = contactsProvider
                    .readPhoneContacts(defaultAreaCode, defaultRegionCode, currentTs);

            return phoneContactDao
                    .syncWith(phoneContacts, currentTs)
                    .doOnSubscribe(() -> Timber.d("[Contacts] Reading system address book..."))
                    .doOnNext(this::logDiff)
                    .filter(diff -> !diff.isEmpty())
                    .flatMap(houstonClient::patchPhoneNumbers)
                    .doOnNext(newContacts -> {
                        Timber.d("[Contacts] Synchronized changes with Houston.");

                        for (Contact contact: newContacts) {
                            createOrUpdateContact(contact).toCompletable().await();
                        }
                    })
                    .lastOrDefault(null)
                    .map(RxHelper::toVoid);
        });
    }

    /**
     * Delete all stored phone contacts, rescan and upload full patch to Houston.
     */
    public Observable<Void> resetSyncPhoneContacts() {
        return phoneContactDao.deleteAll()
                .andThen(syncPhoneContacts())
                .map(RxHelper::toVoid);
    }

    /**
     * Fetch a single contact from the database, by id.
     */
    public Observable<Contact> fetchContact(Long contactHid) {
        return contactDao.fetchByHid(contactHid);
    }

    /**
     * Return an address to pay to a given contact.
     */
    public Observable<MuunAddress> fetchContactAddress(Long contactHid) {

        return fetchContact(contactHid)
                .first()
                .map(this::getAddressForContact);
    }

    /**
     * Generate a new address to pay a contact.
     */
    public MuunAddress getAddressForContact(Contact contact) {
        Preconditions.checkNotNull(contact.getHid());

        synchronized (addressCreationLock) {
            // Re-fetch the contact inside this synchronized block to avoid races:
            contact = fetchContact(contact.getHid()).toBlocking().first();

            switch (contact.maxAddressVersion) {
                case (int) Libwallet.AddressVersionV1:
                    return createContactAddressV1(contact);

                case (int) Libwallet.AddressVersionV2:
                    return createContactAddressV2(new MultisigContact(contact));

                case (int) Libwallet.AddressVersionV3:
                    return createContactAddressV3(new MultisigContact(contact));

                case (int) Libwallet.AddressVersionV4:
                    return createContactAddressV4(new MultisigContact(contact));

                case (int) Libwallet.AddressVersionV5:
                default: // contact can handle higher, we can't.
                    return createContactAddressV5(new MultisigContact(contact));

            }
        }
    }

    private MuunAddress createContactAddressV1(Contact contact) {
        final PublicKey basePublicKey = contact.publicKey;

        final PublicKey derivedPublicKey = basePublicKey
                .deriveNextValidChild(contact.lastDerivationIndex.intValue() + 1);

        contact.lastDerivationIndex = (long) derivedPublicKey.getLastLevelIndex();
        contactDao.updateLastDerivationIndex(contact.getHid(), contact.lastDerivationIndex);

        return LibwalletBridge.createAddressV1(derivedPublicKey, networkParameters);
    }

    private MuunAddress createContactAddressV2(MultisigContact contact) {
        final PublicKeyPair derivedPublicKeyPair = derivePublicKeyPair(contact);

        return LibwalletBridge.createAddressV2(derivedPublicKeyPair, networkParameters);
    }

    private MuunAddress createContactAddressV3(MultisigContact contact) {
        final PublicKeyPair derivedPublicKeyPair = derivePublicKeyPair(contact);

        return LibwalletBridge.createAddressV3(derivedPublicKeyPair, networkParameters);
    }

    private MuunAddress createContactAddressV4(MultisigContact contact) {
        final PublicKeyPair derivedPublicKeyPair = derivePublicKeyPair(contact);

        return LibwalletBridge.createAddressV4(derivedPublicKeyPair, networkParameters);
    }

    private MuunAddress createContactAddressV5(MultisigContact contact) {
        final PublicKeyPair derivedPublicKeyPair = derivePublicKeyPair(contact);

        return LibwalletBridge.createAddressV5(derivedPublicKeyPair, networkParameters);
    }

    private PublicKeyPair derivePublicKeyPair(MultisigContact contact) {
        final PublicKeyPair basePublicKeyPair = contact.getPublicKeyPair();

        final PublicKeyPair derivedPublicKeyPair = basePublicKeyPair
                .deriveNextValidChild(contact.lastDerivationIndex.intValue() + 1);

        contact.lastDerivationIndex = (long) derivedPublicKeyPair.getLastLevelIndex();
        contactDao.updateLastDerivationIndex(contact.getHid(), contact.lastDerivationIndex);

        return derivedPublicKeyPair;
    }

    /**
     * Update a contact if it exists.
     */
    public void updateContact(Contact contact) {

        contactDao.updateLastDerivationIndex(contact.getHid(), contact.lastDerivationIndex);
        publicProfileDao.update(contact.publicProfile);
    }

    /**
     * Show a notification for a new contact.
     */
    public void showNewContactNotification(Contact contact) {
        notificationService.showNewContactNotification(contact);
    }

    /**
     * Insert a contact if it doesn't exist, update it if it does.
     */
    public Observable<Void> createOrUpdateContact(Contact maybeNewContact) {

        final Observable<Contact> contactToStore = contactDao
                .fetchByHid(maybeNewContact.getHid())
                .first()
                .map(existingContact -> existingContact.mergeWithUpdate(maybeNewContact))
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(maybeNewContact)
                ));

        return contactToStore.flatMap(contact ->
                    publicProfileDao.store(contact.publicProfile)
                            .flatMap(ignored -> contactDao.store(contact))
                )
                .map(RxHelper::toVoid);
    }

    private String getDefaultAreaCode(PhoneNumber phoneNumber) {
        return phoneNumber
                .getAreaNumber()
                .orElse("");
    }

    private void logDiff(Diff<String> diff) {
        Timber.d(String.format(
                "[Contacts] Found %d additions and %d deletions",
                diff.added.size(),
                diff.removed.size()
        ));
    }

    /**
     * Stop watching contacts.
     */
    public void stopWatchingContacts() {
        if (phoneContactsAutoSyncSub != null) {

            phoneContactsAutoSyncSub.unsubscribe();
            phoneContactsAutoSyncSub = null;
        }
    }
}
