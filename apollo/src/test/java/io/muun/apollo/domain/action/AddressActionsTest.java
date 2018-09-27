package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.TestUtils;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.template.TemplateHelpers;
import io.muun.common.api.ExternalAddressesRecord;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Schema;

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

    private AsyncActionStore asyncActionStore = TestUtils.getAsyncActionStore();

    private AddressActions addressActions;

    @Before
    public void setUp() {
        addressActions = new AddressActions(
                keysRepository,
                houstonClient,
                asyncActionStore
        );
    }

    @Ignore
    @Test
    public void getExternalAddress() {

        final PublicKey basePublicKey = TemplateHelpers.externalPublicKey().generateValue();

        final PublicKey publicKey = basePublicKey
                .deriveFromAbsolutePath(Schema.getExternalKeyPath());

        doReturn(basePublicKey).when(keysRepository).getBasePublicKey();

        doReturn(5).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(10).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(addressActions.getExternalAddress())
                .isEqualTo(publicKey.deriveNextValidChild(6).toAddress());

        verify(keysRepository).setMaxUsedExternalAddressIndex(6);

        verify(addressActions.syncExternalAddressIndexes).run();
    }

    @Ignore
    @Test
    public void getExternalAddress_when_maxUsedIndex_is_null() {

        final PublicKey basePublicKey = TemplateHelpers.externalPublicKey().generateValue();

        final PublicKey publicKey = basePublicKey
                .deriveFromAbsolutePath(Schema.getExternalKeyPath());

        doReturn(basePublicKey).when(keysRepository).getBasePublicKey();

        doReturn(null).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(10).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(addressActions.getExternalAddress())
                .isEqualTo(publicKey.deriveNextValidChild(0).toAddress());

        verify(keysRepository).setMaxUsedExternalAddressIndex(0);
    }

    @Ignore
    @Test
    public void getExternalAddress_when_maxUsedIndex_is_equal_to_maxWatchingIndex() {

        final PublicKey basePublicKey = TemplateHelpers.externalPublicKey().generateValue();

        final PublicKey publicKey = basePublicKey
                .deriveFromAbsolutePath(Schema.getExternalKeyPath());

        doReturn(basePublicKey).when(keysRepository).getBasePublicKey();

        doReturn(3).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(3).when(keysRepository).getMaxWatchingExternalAddressIndex();

        assertThat(addressActions.getExternalAddress()).isIn(
                publicKey.deriveNextValidChild(0).toAddress(),
                publicKey.deriveNextValidChild(1).toAddress(),
                publicKey.deriveNextValidChild(2).toAddress(),
                publicKey.deriveNextValidChild(3).toAddress()
        );

        verify(keysRepository, never()).setMaxUsedExternalAddressIndex(anyInt());
    }

    @Test
    public void syncExternalAddressesIndexes_when_maxUsedIndex_is_null() {

        final ExternalAddressesRecord record = new ExternalAddressesRecord(0, 10);

        doReturn(null).when(keysRepository).getMaxUsedExternalAddressIndex();
        doReturn(null).when(keysRepository).getMaxWatchingExternalAddressIndex();
        doReturn(Observable.just(record)).when(houstonClient).fetchExternalAddressesRecord();

        fetchItemFromObservable(addressActions.syncExternalAddressesIndexes());

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

        fetchItemFromObservable(addressActions.syncExternalAddressesIndexes());

        verify(keysRepository).setMaxUsedExternalAddressIndex(2);
        verify(keysRepository).setMaxWatchingExternalAddressIndex(10);
    }
}