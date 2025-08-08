package io.muun.apollo.domain.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.TestUtils
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.address.CreateAddressAction
import io.muun.apollo.domain.action.address.SyncExternalAddressIndexesAction
import io.muun.apollo.template.TemplateHelpers
import io.muun.common.api.ExternalAddressesRecord
import io.muun.common.crypto.hd.PublicKeyTriple
import org.assertj.core.api.Assertions
import org.bitcoinj.core.Context
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.params.RegTestParams
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import rx.Observable

class AddressActionsTest : BaseTest() {

    private val keysRepository = mockk<KeysRepository>(relaxed = true)

    private val houstonClient = mockk<HoustonClient>(relaxed = true)

    private lateinit var syncExternalAddressIndexes: SyncExternalAddressIndexesAction
    private lateinit var createAddress: CreateAddressAction

    private val networkParameters: NetworkParameters = RegTestParams.get()

    @Before
    fun setUp() {
        Context.propagate(Context(networkParameters))
        syncExternalAddressIndexes = SyncExternalAddressIndexesAction(
            houstonClient,
            keysRepository
        )
        createAddress = CreateAddressAction(
            keysRepository,
            networkParameters,
            syncExternalAddressIndexes
        )
    }

    private fun toTransactionSchemeV3Address(
        basePublicKeyTriple: PublicKeyTriple,
        index: Int,
    ): String? {
        return null
//        TODO mv this and ignored tests to libwallet
//        final PublicKeyTriple pubKeyTriple = basePublicKeyTriple.deriveNextValidChild(index);
//        return TransactionScheme.V3.createAddress(pubKeyTriple, networkParameters).getAddress();
    }

    @Test
    @Ignore("ignore tests that run libwallet code for now")
    fun testGetExternalAddress() {
        val basePublicKeyTriple =
            TemplateHelpers.externalPublicKeyTriple().generateValue<PublicKeyTriple>()

        Mockito.doReturn(basePublicKeyTriple.toPair()).`when`(keysRepository).basePublicKeyPair
        Mockito.doReturn(5).`when`(keysRepository).maxUsedExternalAddressIndex
        Mockito.doReturn(10).`when`(keysRepository).maxWatchingExternalAddressIndex

        Assertions.assertThat(createAddress.actionNow().legacy.address)
            .isEqualTo(toTransactionSchemeV3Address(basePublicKeyTriple, 6))

        Mockito.verify(keysRepository).maxUsedExternalAddressIndex = 6
        Mockito.verify(syncExternalAddressIndexes).run()
    }

    @Test
    @Ignore("ignore tests that run libwallet code for now")
    fun testGetExternalAddress_when_maxUsedIndex_is_null() {
        val basePublicKeyTriple =
            TemplateHelpers.externalPublicKeyTriple().generateValue<PublicKeyTriple>()

        Mockito.doReturn(basePublicKeyTriple.toPair()).`when`(keysRepository).basePublicKeyPair
        Mockito.doReturn(null).`when`(keysRepository).maxUsedExternalAddressIndex
        Mockito.doReturn(10).`when`(keysRepository).maxWatchingExternalAddressIndex

        Assertions.assertThat(createAddress.actionNow().legacy.address)
            .isEqualTo(toTransactionSchemeV3Address(basePublicKeyTriple, 0))

        Mockito.verify(keysRepository).maxUsedExternalAddressIndex = 0
    }

    @Test
    @Ignore("ignore tests that run libwallet code for now")
    fun testGetExternalAddress_when_maxUsedIndex_is_equal_to_maxWatchingIndex() {
        val basePublicKeyTriple =
            TemplateHelpers.externalPublicKeyTriple().generateValue<PublicKeyTriple>()

        Mockito.doReturn(basePublicKeyTriple.toPair()).`when`(keysRepository).basePublicKeyPair
        Mockito.doReturn(3).`when`(keysRepository).maxUsedExternalAddressIndex
        Mockito.doReturn(3).`when`(keysRepository).maxWatchingExternalAddressIndex

        Assertions.assertThat(createAddress.actionNow().legacy.address).isIn(
            toTransactionSchemeV3Address(basePublicKeyTriple, 0),
            toTransactionSchemeV3Address(basePublicKeyTriple, 1),
            toTransactionSchemeV3Address(basePublicKeyTriple, 2),
            toTransactionSchemeV3Address(basePublicKeyTriple, 3)
        )

        Mockito.verify(keysRepository, Mockito.never()).maxUsedExternalAddressIndex =
            ArgumentMatchers.anyInt()
    }

    @Test
    fun syncExternalAddressesIndexes_when_maxUsedIndex_is_null() {
        val record = ExternalAddressesRecord(0, 10)

        every { keysRepository.maxUsedExternalAddressIndex } returns null
        every { keysRepository.maxWatchingExternalAddressIndex } returns 0
        every { houstonClient.fetchExternalAddressesRecord() } returns Observable.just(record)

        TestUtils.fetchItemFromObservable(syncExternalAddressIndexes.action())

        verify { keysRepository setProperty "maxUsedExternalAddressIndex" value 0 }
        verify { keysRepository setProperty "maxWatchingExternalAddressIndex" value 10 }
    }

    @Test
    fun syncExternalAddressesIndexes_when_local_maxUsedIndex_is_greater_than_remote() {
        val record = ExternalAddressesRecord(0, 10)

        every { keysRepository.maxUsedExternalAddressIndex } returns 2
        every { keysRepository.maxWatchingExternalAddressIndex } returns 5
        every { houstonClient.updateExternalAddressesRecord(any(Int::class)) } returns
            Observable.just(record)

        TestUtils.fetchItemFromObservable(syncExternalAddressIndexes.action())

        verify { keysRepository setProperty "maxUsedExternalAddressIndex" value 2 }
        verify { keysRepository setProperty "maxWatchingExternalAddressIndex" value 10 }
    }
}