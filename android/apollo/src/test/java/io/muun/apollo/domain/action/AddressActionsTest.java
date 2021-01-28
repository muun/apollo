package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.address.CreateAddressAction;
import io.muun.apollo.domain.action.address.SyncExternalAddressIndexesAction;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.schemes.TransactionScheme;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.RegTestParams;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import rx.Observable;

import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


public class AddressActionsTest extends BaseTest {

    @Mock
    private KeysRepository keysRepository;

    @Mock
    private HoustonClient houstonClient;

    @Mock

    private SyncExternalAddressIndexesAction syncExternalAddressIndexes;
    private CreateAddressAction createAddress;

    private NetworkParameters networkParameters = RegTestParams.get();

    @Before
    public void setUp() {
        Context.propagate(new Context(networkParameters));

        syncExternalAddressIndexes = new SyncExternalAddressIndexesAction(
                houstonClient,
                keysRepository
        );

        createAddress = new CreateAddressAction(
                keysRepository,
                networkParameters,
                syncExternalAddressIndexes
        );
    }

    private String toTransactionSchemeV3Address(PublicKeyTriple basePublicKeyTriple, int index) {
        final PublicKeyTriple derivedKeyTriple = basePublicKeyTriple.deriveNextValidChild(index);
        return TransactionScheme.V3.createAddress(derivedKeyTriple, networkParameters).getAddress();
    }

    @Test
    @Ignore
    public void getExternalAddress() {

        final PublicKeyTriple basePublicKeyTriple =
                TemplateHelpers.externalPublicKeyTriple().generateValue();

        doReturn(basePublicKeyTriple.toPair()).when(keysRepository).getBasePublicKeyPair();

        doReturn(5).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(10).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(createAddress.actionNow().legacy.getAddress())
                .isEqualTo(toTransactionSchemeV3Address(basePublicKeyTriple, 6));

        verify(keysRepository).setMaxUsedExternalAddressIndex(6);

        verify(syncExternalAddressIndexes).run();
    }

    @Test
    @Ignore
    public void getExternalAddress_when_maxUsedIndex_is_null() {

        final PublicKeyTriple basePublicKeyTriple =
                TemplateHelpers.externalPublicKeyTriple().generateValue();

        doReturn(basePublicKeyTriple.toPair()).when(keysRepository).getBasePublicKeyPair();

        doReturn(null).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(10).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(createAddress.actionNow().legacy.getAddress())
                .isEqualTo(toTransactionSchemeV3Address(basePublicKeyTriple, 0));

        verify(keysRepository).setMaxUsedExternalAddressIndex(0);
    }

    @Test
    @Ignore
    public void getExternalAddress_when_maxUsedIndex_is_equal_to_maxWatchingIndex() {

        final PublicKeyTriple basePublicKeyTriple =
                TemplateHelpers.externalPublicKeyTriple().generateValue();

        doReturn(basePublicKeyTriple.toPair()).when(keysRepository).getBasePublicKeyPair();

        doReturn(3).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(3).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(createAddress.actionNow().legacy.getAddress()).isIn(
                toTransactionSchemeV3Address(basePublicKeyTriple, 0),
                toTransactionSchemeV3Address(basePublicKeyTriple, 1),
                toTransactionSchemeV3Address(basePublicKeyTriple, 2),
                toTransactionSchemeV3Address(basePublicKeyTriple, 3)
        );

        verify(keysRepository, never()).setMaxUsedExternalAddressIndex(anyInt());
    }

    @Test
    public void syncExternalAddressesIndexes_when_maxUsedIndex_is_null() {

        final ExternalAddressesRecord record = new ExternalAddressesRecord(0, 10);

        doReturn(null).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(null).when(keysRepository).getMaxWatchingExternalAddressIndex();
        doReturn(Observable.just(record)).when(houstonClient).fetchExternalAddressesRecord();

        fetchItemFromObservable(syncExternalAddressIndexes.action());

        verify(keysRepository).setMaxUsedExternalAddressIndex(0);
        verify(keysRepository).setMaxWatchingExternalAddressIndex(10);
    }

    @Test
    public void syncExternalAddressesIndexes_when_local_maxUsedIndex_is_greater_than_remote() {

        final ExternalAddressesRecord record = new ExternalAddressesRecord(0, 10);

        doReturn(2).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(5).when(keysRepository).getMaxWatchingExternalAddressIndex();

        doReturn(Observable.just(record))
                .when(houstonClient).updateExternalAddressesRecord(anyInt());

        fetchItemFromObservable(syncExternalAddressIndexes.action());

        verify(keysRepository).setMaxUsedExternalAddressIndex(2);
        verify(keysRepository).setMaxWatchingExternalAddressIndex(10);
    }
}