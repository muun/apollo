package io.muun.apollo.domain.action.incoming_swap

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.data.db.incoming_swap.IncomingSwapDao
import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.libwallet.LibwalletService
import io.muun.apollo.domain.libwallet.errors.UnfulfillableIncomingSwapError
import io.muun.apollo.domain.model.FulfillmentPushedResult
import io.muun.apollo.domain.model.IncomingSwap
import io.muun.apollo.domain.model.IncomingSwapFulfillmentData
import io.muun.apollo.domain.model.Preimage
import io.muun.common.api.error.ErrorCode
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.exception.HttpException
import io.muun.common.utils.Hashes
import io.muun.common.utils.RandomGenerator
import org.bitcoinj.params.RegTestParams
import org.junit.Before
import org.junit.Test
import rx.Completable
import rx.Observable
import rx.Single

class FulfillIncomingSwapActionTest : BaseTest() {

    private val houstonClient = mockk<HoustonClient>(relaxed = true)

    private val operationDao = mockk<OperationDao>(relaxed = true)

    private val keysRepository = mockk<KeysRepository>(relaxed = true)

    private val incomingSwapDao = mockk<IncomingSwapDao>(relaxed = true)

    private val transactionSizeRepository = mockk<TransactionSizeRepository>(relaxed = true)

    private val libwalletService = mockk<LibwalletService>(relaxed = true)

    private lateinit var action: FulfillIncomingSwapAction

    private val params = RegTestParams.get()

    @Before
    fun setUp() {

        // Required until we kotlinize BaseTest
        // TODO: kotlinize BaseTest and remove this
        Globals.INSTANCE = mockk()

        every { Globals.INSTANCE.network } returns params

        every {
            keysRepository.basePrivateKey
        } returns
            Observable.just(PrivateKey.getNewRootPrivateKey(params))

        every {
            keysRepository.baseMuunPublicKey
        } returns
            PrivateKey.getNewRootPrivateKey(params).publicKey

        every { incomingSwapDao.store(any()) } answers { Observable.just(firstArg()) }
        every { operationDao.store(any()) } answers { Observable.just(firstArg()) }

        action = FulfillIncomingSwapAction(
            houstonClient,
            operationDao,
            keysRepository,
            params,
            incomingSwapDao,
            transactionSizeRepository,
            libwalletService
        )
    }

    @Test(expected = java.lang.RuntimeException::class)
    fun unknownSwap() {
        every {
            operationDao.fetchByIncomingSwapUuid("fake")
        } returns
            Observable.error(RuntimeException())

        action.actionNow("fake")
    }

    @Test
    fun unfulfillableSwap() {
        val swap = spyk(Gen.incomingSwap())
        val operation = Gen.operation(swap)

        every {
            operationDao.fetchByIncomingSwapUuid(swap.houstonUuid)
        } returns
            Observable.just(operation)

        every {
            swap.verifyFulfillable(any(), params)
        } throws
            UnfulfillableIncomingSwapError(swap.houstonUuid, java.lang.RuntimeException())

        every {
            houstonClient.fetchFulfillmentData(swap.houstonUuid)
        } returns
            Single.just(null)

        every {
            houstonClient.expireInvoice(swap.getPaymentHash())
        } returns
            Completable.complete()

        action.action(swap.houstonUuid).toBlocking().subscribe()

        verify(exactly = 1) { houstonClient.expireInvoice(swap.getPaymentHash()) }
        verify(exactly = 0) { houstonClient.pushFulfillmentTransaction(any(), any()) }
    }

    @Test
    fun onchainFulfillment() {
        val preimage = RandomGenerator.getBytes(32)
        val swap = spyk(Gen.incomingSwap())
        val operation = Gen.operation(swap)
        val fulfillmentData = IncomingSwapFulfillmentData(
            ByteArray(0),
            ByteArray(0),
            "",
            1
        )
        val fulfillmentPushedResult = FulfillmentPushedResult(
            Gen.nextTransactionSize(),
            Gen.feeBumpFunctions()
        )

        every {
            operationDao.fetchByIncomingSwapUuid(swap.houstonUuid)
        } returns
            Observable.just(operation)

        every { swap.verifyFulfillable(any(), params) } just Runs

        every {
            houstonClient.fetchFulfillmentData(swap.houstonUuid)
        } returns
            Single.just(fulfillmentData)

        every {
            swap.fulfill(fulfillmentData, any(), any(), params)
        } returns
            IncomingSwap.FulfillmentResult(ByteArray(0), Preimage.fromBytes(preimage))

        every {
            houstonClient.pushFulfillmentTransaction(swap.houstonUuid, any())
        } returns
            Single.just(fulfillmentPushedResult)

        action.action(swap.houstonUuid).toBlocking().subscribe()

        verify(exactly = 0) { houstonClient.expireInvoice(swap.getPaymentHash()) }
        verify(exactly = 1) { houstonClient.pushFulfillmentTransaction(any(), any()) }
        verify(exactly = 1) { transactionSizeRepository.setTransactionSize(any())}
        verify(exactly = 1) { libwalletService.persistFeeBumpFunctions(any(), any())}
    }

    @Test
    fun fullDebtFulfilment() {
        val preimage = RandomGenerator.getBytes(32)
        val swap = spyk(Gen.incomingSwap(paymentHash = Hashes.sha256(preimage), htlc = null))
        val operation = Gen.operation(swap)

        every {
            operationDao.fetchByIncomingSwapUuid(swap.houstonUuid)
        } returns
            Observable.just(operation)

        every { swap.verifyFulfillable(any(), params) } just Runs

        every {
            swap.fulfillFullDebt()
        } returns
            IncomingSwap.FulfillmentResult(byteArrayOf(), Preimage.fromBytes(preimage))

        every {
            houstonClient.fulfillIncomingSwap(swap.houstonUuid, preimage)
        } returns
            Completable.complete()

        action.action(swap.houstonUuid).toBlocking().subscribe()

        verify(exactly = 1) { houstonClient.fulfillIncomingSwap(swap.houstonUuid, preimage) }
        verify(exactly = 0) { houstonClient.expireInvoice(swap.getPaymentHash()) }
        verify(exactly = 0) { houstonClient.fetchFulfillmentData(swap.houstonUuid) }
        verify(exactly = 0) { houstonClient.pushFulfillmentTransaction(swap.houstonUuid, any()) }
    }

    @Test
    fun onchainAlreadyFulfilled() {
        val swap = spyk(Gen.incomingSwap())
        val operation = Gen.operation(swap)

        every {
            operationDao.fetchByIncomingSwapUuid(swap.houstonUuid)
        } returns
            Observable.just(operation)

        every { swap.verifyFulfillable(any(), params) } just Runs

        every {
            houstonClient.fetchFulfillmentData(swap.houstonUuid)
        } returns
            Single.error(HttpException(ErrorCode.INCOMING_SWAP_ALREADY_FULFILLED))

        action.action(swap.houstonUuid).toBlocking().subscribe()

        verify(exactly = 0) { houstonClient.expireInvoice(swap.getPaymentHash()) }
        verify(exactly = 0) { houstonClient.pushFulfillmentTransaction(swap.houstonUuid, any()) }
    }
}