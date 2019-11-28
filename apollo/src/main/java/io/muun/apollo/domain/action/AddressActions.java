package io.muun.apollo.domain.action;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.LibwalletBridge;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.PasswordIntegrityError;
import io.muun.common.Optional;
import io.muun.common.crypto.hd.KeyCrypter;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Schema;
import io.muun.common.crypto.hd.exception.KeyDerivationException;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.crypto.schemes.TransactionSchemeV4;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.rx.RxHelper;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.RandomGenerator;

import org.bitcoinj.core.NetworkParameters;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
public class AddressActions {

    private final KeysRepository keysRepository;

    private final HoustonClient houstonClient;

    private final NetworkParameters networkParameters;

    public final AsyncAction0<Void> syncExternalAddressIndexes;

    /**
     * Constructor.
     */
    @Inject
    public AddressActions(KeysRepository keysRepository,
                          HoustonClient houstonClient,
                          NetworkParameters networkParameters,
                          AsyncActionStore asyncActionStore) {

        this.keysRepository = keysRepository;
        this.houstonClient = houstonClient;
        this.networkParameters = networkParameters;

        this.syncExternalAddressIndexes = asyncActionStore
                .get("address/syncExternalIndexes", this::syncExternalAddressesIndexes);
    }

    /**
     * Return an external address.
     */
    public MuunAddress createLegacyAddress() {
        return createMuunAddress(TransactionSchemeV3.ADDRESS_VERSION);
    }

    public MuunAddress createSegwitAddress() {
        return createMuunAddress(TransactionSchemeV4.ADDRESS_VERSION);
    }

    private MuunAddress createMuunAddress(int addressVersion) {

        final Integer maxUsedIndex = keysRepository.getMaxUsedExternalAddressIndex();
        final Integer maxWatchingIndex = keysRepository.getMaxWatchingExternalAddressIndex();

        Preconditions.checkState(maxUsedIndex == null || maxWatchingIndex != null);
        Preconditions.checkState(maxUsedIndex == null || maxUsedIndex <= maxWatchingIndex);

        final int nextIndex;

        if (maxUsedIndex == null) {
            nextIndex = 0;

        } else if (maxUsedIndex < maxWatchingIndex) {
            nextIndex = maxUsedIndex + 1;

        } else {
            nextIndex = RandomGenerator.getInt(maxWatchingIndex + 1);
        }

        // FIXME: if the nextIndex derived key is invalid (highly improbable),
        // childPublicKey.getLastLevelIndex() will be greater than maxWatchingIndex, which is a bug:
        // it will violate the second precondition.

        final PublicKeyPair derivedPublicKeyPair = keysRepository
                .getBasePublicKeyPair()
                .deriveFromAbsolutePath(Schema.getExternalKeyPath())
                .deriveNextValidChild(nextIndex);

        if (maxUsedIndex == null || derivedPublicKeyPair.getLastLevelIndex() > maxUsedIndex) {
            keysRepository.setMaxUsedExternalAddressIndex(derivedPublicKeyPair.getLastLevelIndex());
        }

        syncExternalAddressIndexes.run();

        // Use only latest supported TransactionSchemes
        switch (addressVersion) {
            case TransactionSchemeV3.ADDRESS_VERSION:
                return LibwalletBridge.createAddressV3(derivedPublicKeyPair, networkParameters);

            case TransactionSchemeV4.ADDRESS_VERSION:
                return LibwalletBridge.createAddressV4(derivedPublicKeyPair, networkParameters);

            default:
                throw new MissingCaseError(addressVersion, "Unexpected address version");
        }
    }

    /**
     * Sync the external address indexes with Houston.
     */
    public Observable<Void> syncExternalAddressesIndexes() {

        return Observable.defer(() -> {

            final Integer maxUsedIndex = keysRepository.getMaxUsedExternalAddressIndex();

            if (maxUsedIndex == null) {
                return houstonClient.fetchExternalAddressesRecord();
            }

            return houstonClient.updateExternalAddressesRecord(maxUsedIndex);

        }).doOnNext((record) -> storeExternalAddressIndexes(
                record.maxUsedIndex,
                record.maxWatchingIndex
        )).map(RxHelper::toVoid);
    }

    /**
     * Update the local storage of external address indexes.
     */
    private void storeExternalAddressIndexes(Integer maxUsedIndex, Integer maxWatchingIndex) {

        maxUsedIndex = maxWithNulls(
                maxUsedIndex,
                keysRepository.getMaxUsedExternalAddressIndex()
        );

        maxWatchingIndex = maxWithNulls(
                maxWatchingIndex,
                keysRepository.getMaxWatchingExternalAddressIndex()
        );

        keysRepository.setMaxUsedExternalAddressIndex(maxUsedIndex);
        keysRepository.setMaxWatchingExternalAddressIndex(maxWatchingIndex);
    }

    /**
     * Synchronize all the root public keys with the server.
     */
    public Observable<Void> syncPublicKeySet() {

        return Observable.defer(() ->
                houstonClient.updatePublicKeySet(keysRepository.getBasePublicKey())

        ).doOnNext(keySet -> {
            storeExternalAddressIndexes(
                    keySet.getExternalMaxUsedIndex(),
                    keySet.getExternalMaxWatchingIndex()
            );

            keysRepository.storeBaseMuunPublicKey(keySet.getBasePublicKeyPair().getMuunPublicKey());

        }).map(RxHelper::toVoid);
    }

    private Integer maxWithNulls(@Nullable Integer a, @Nullable Integer b) {

        return a == null ? b : b == null ? a : (Integer) Math.max(a, b);
    }

    /**
     * Store the root private key in a secure local storage.
     */
    private Observable<Void> storeRootPrivateKey(PrivateKey rootPrivateKey) {
        try {
            final PrivateKey basePrivateKey =
                    rootPrivateKey.deriveFromAbsolutePath(Schema.getBasePath());

            return keysRepository.storeBasePrivateKey(basePrivateKey);

        } catch (KeyDerivationException e) {
            throw new RuntimeException(e); // This should not happen
        }
    }

    /**
     * Creates a local pair of keys, which can derive everything in our schema.
     *
     * @return the encrypted root private key.
     */
    public Observable<String> createAndStoreRootPrivateKey(String passphrase) {

        while (true) {

            try {
                final PrivateKey rootPrivateKey = keysRepository.createRootPrivateKey();

                // Check if we can derive all the subtree keys
                for (String subtreePath : Schema.getAllSubtreePaths()) {
                    rootPrivateKey.deriveFromAbsolutePath(subtreePath);
                }

                return storeRootPrivateKey(rootPrivateKey)
                        .map(ignored -> encryptRootPrivateKey(rootPrivateKey, passphrase));

            } catch (KeyDerivationException e) {
                // Sadly, not all keys are derivable at all paths. There's always a (really) small
                // chance that the derivation will fail, so we have to make sure that the generated
                // private key is derivable at our base paths. If it isn't, then we just try with
                // another root key.
            }
        }
    }

    /**
     * Encrypt a root private key with the given user input.
     */
    public String encryptRootPrivateKey(PrivateKey rootPrivateKey, String userInput) {
        return new KeyCrypter().encrypt(rootPrivateKey, userInput);
    }

    /**
     * Try to decrypt a private key with the given user input.
     */
    public Observable<Void> decryptAndStoreRootPrivateKey(String encryptedRootPrivateKey,
                                                          String userInput)
            throws PasswordIntegrityError {

        final Optional<PrivateKey> rootPrivateKey =
                new KeyCrypter().decrypt(encryptedRootPrivateKey, userInput);

        if (rootPrivateKey.isPresent()) {
            return storeRootPrivateKey(rootPrivateKey.get());

        } else {
            throw new PasswordIntegrityError();
        }
    }
}
