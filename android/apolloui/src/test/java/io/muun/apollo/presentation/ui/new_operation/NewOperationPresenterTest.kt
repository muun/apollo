package io.muun.apollo.presentation.ui.new_operation

import android.os.Bundle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.muun.apollo.data.external.Gen
import io.muun.apollo.domain.action.base.ActionState
import io.muun.apollo.domain.action.operation.ResolveOperationUriAction
import io.muun.apollo.domain.action.operation.SubmitPaymentAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.errors.UserFacingError
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.Operation
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector
import io.muun.apollo.domain.selector.ExchangeRateSelector
import io.muun.apollo.domain.selector.PaymentContextSelector
import io.muun.apollo.domain.selector.UserSelector
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.common.model.Currency
import io.muun.common.model.ExchangeRateProvider
import io.muun.common.utils.BitcoinUtils
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import rx.Observable
import rx.subjects.BehaviorSubject

// TODO:
// I can't get Mockito to mock PaymentContextSelector. When I call watch() on the mock, the original
// method runs despite being mocked and throws an NPE. This can be verified with the debugger.

@Ignore
@RunWith(MockitoJUnitRunner.Silent::class) // using Silent runner temporarily, allowing unused mocks
class NewOperationPresenterTest : BasePresentationTest() {

    // Sample data:
    val SOME_ADDRESS = Gen.address()

    val CONTACT_URI = OperationUri.fromContactHid(1)
    val ADDRESS_URI = OperationUri.fromAddress(SOME_ADDRESS)
    val BIP21_URI = OperationUri.fromBitcoinUri("bitcoin:$SOME_ADDRESS")
    val BIP72_URI = OperationUri.fromBitcoinUri("bitcoin:?r=https://merchant.com/pay")
    val ANY_URI = CONTACT_URI
    val USER = Gen.user(email = "user1@muun.com", primaryCurrency = Currency.getUnit("BTC").get())

    // Selector result mocks:
    lateinit var maxSpendableAmount: BehaviorSubject<Long>
    lateinit var canPayVerification: BehaviorSubject<UserFacingError>
    lateinit var exchangeRates: BehaviorSubject<ExchangeRateProvider>

    // Action result mocks:
    lateinit var fetchRealTimeDataResult: BehaviorSubject<ActionState<Void>>
    lateinit var resolveOperationUriResult: BehaviorSubject<ActionState<PaymentRequest>>
    lateinit var paymentContextResult: BehaviorSubject<PaymentContext>
    lateinit var submitPaymentResult: BehaviorSubject<ActionState<Operation>>

    // Bag mocks:
    lateinit var userSel: UserSelector

    // Presenter, to be initialized with mocks:
    lateinit var presenter: NewOperationPresenter

    @Mock
    lateinit var paymentContextSelector: PaymentContextSelector

    @Before
    fun setUp() {
        // Reset all selector mocks:
        maxSpendableAmount = BehaviorSubject.create()
        canPayVerification = BehaviorSubject.create()
        exchangeRates = BehaviorSubject.create()

        // Reset all action mocks:
        fetchRealTimeDataResult = BehaviorSubject.create()
        resolveOperationUriResult = BehaviorSubject.create()
        paymentContextResult = BehaviorSubject.create()
        submitPaymentResult = BehaviorSubject.create()


        userSel = mock {
            on { get() } doReturn (USER)
        }

        doAnswer { invocation -> paymentContextResult }
            .`when`(paymentContextSelector).watch()

        // Rebuild the mocked Presenter:
        presenter = spy(NewOperationPresenterWithMocks(
            mock { on { state } doReturn (resolveOperationUriResult) },
            paymentContextSelector,
            mock { on { state } doReturn (submitPaymentResult) },
            mock { on { state } doReturn (null) },
            mock { on { get() } doReturn (CurrencyDisplayMode.BTC) },
            userSel,
            mock { on { getWindow() } doReturn (Gen.exchangeRateWindow()) }
        ))

        presenter.setView(mock())
    }

    @Test
    fun `creates correct form given a contact URI`() {
        presenter.onViewCreated(CONTACT_URI, NewOperationOrigin.SEND_CONTACT)
        assertFormInitialState(presenter.form, true)
    }

    @Test
    fun `creates correct form given an address URI`() {
        presenter.onViewCreated(ADDRESS_URI, NewOperationOrigin.SCAN_QR)
        assertFormInitialState(presenter.form, true)
    }

    @Test
    fun `creates correct form given a BIP-21 URI`() {
        presenter.onViewCreated(BIP21_URI, NewOperationOrigin.SCAN_QR)
        assertFormInitialState(presenter.form, true)
    }

    @Test
    fun `creates correct form given a BIP-72`() {
        presenter.onViewCreated(BIP72_URI, NewOperationOrigin.SCAN_QR)
        assertFormInitialState(presenter.form, false)
    }

    @Test
    fun `starts running resolveOperationUri on view created`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        verify(presenter.resolveOperationUri, times(1)).run(ANY_URI)
    }

    @Test
    fun `sets form on view when recreated`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR) // recreated

        verify(presenter.view, times(1)).setForm(presenter.form)
    }

    @Test
    fun `progressively subscribes to streams as initialization happens`() {
        // Subscribe on view creation:
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        assertThat(resolveOperationUriResult.hasObservers()).isTrue()

        // Subscribe on setup:
        presenter.setUp(Bundle())
        assertThat(submitPaymentResult.hasObservers()).isTrue()
    }

    @Test
    fun `does not attempt to set receiver on view when recreated before URI resolve`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR) // recreated

        verify(presenter.view, times(1)).setForm(presenter.form)
        verifyNoMoreInteractions(presenter.view)
    }

    @Test
    fun `sets form data from a complete PaymentRequest when URI is resolved`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.setUp(Bundle())

        val payReq = PaymentRequest.toAddress(SOME_ADDRESS, Money.of(300, "EUR"), "Boo!", 10.0)
        resolveOperationUriResult.onNext(ActionState.createValue(payReq))

        assertThat(presenter.form.payReq).isEqualTo(payReq)

        presenter.form.let {
            // NOTE: some checks are commented below because they don't happen at this stage, but
            // when the amount validation is executed. Leaving this here because maybe they should.
            assertThat(it.payReq).isNotNull()

            assertThat(it.amount).isEqualTo(payReq.amount)
            assertThat(it.isAmountConfirmed).isTrue()
            // assertThat(it.isAmountTooSmall).isFalse()
            // assertThat(it.isAmountTooLarge).isFalse()
            assertThat(it.isUsingAllFunds).isFalse()

            assertThat(it.description).isEqualTo(payReq.description)
            // assertThat(it.isDescriptionTooSmall).isFalse()
            assertThat(it.isDescriptionConfirmed).isTrue()
        }
    }

    @Test
    fun `does not confirm amount from incomplete PaymentRequest when URI is resolved`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.setUp(Bundle())

        val payReq = PaymentRequest.toAddress(SOME_ADDRESS, Money.of(0, "BTC"), "Woof!", 10.0)
        resolveOperationUriResult.onNext(ActionState.createValue(payReq))

        presenter.form.let {
            assertThat(it.amount).isEqualTo(payReq.amount)
            assertThat(it.isAmountConfirmed).isFalse()
        }
    }

    @Test
    fun `does not confirm description from incomplete PaymentRequest when URI is resolved`() {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.setUp(Bundle())

        val payReq = PaymentRequest.toAddress(SOME_ADDRESS, Money.of(10, "BTC"), "", 10.0)
        resolveOperationUriResult.onNext(ActionState.createValue(payReq))

        presenter.form.let {
            assertThat(it.description).isEqualTo(payReq.description)
            assertThat(it.isDescriptionConfirmed).isFalse()
        }
    }

    @Test
    fun `sets analysis in view when toContact is resolved`() {
        val payReq = PaymentRequest.toContact(mock(), Money.of(3, "EUR"), "", 10.0)

        resolveVerify(payReq)
        verify(presenter.view, times(1)).setPaymentAnalysis(any())
    }

    @Test
    fun `sets analysis in view when toAddress is resolved`() {
        val payReq = PaymentRequest.toAddress(SOME_ADDRESS, Money.of(3, "EUR"), "", 10.0)

        resolveVerify(payReq)
        verify(presenter.view, times(1)).setPaymentAnalysis(any())
    }

    @Test
    fun `updates amount with user input when asked`() {
        val amount = Money.of(18, "ARS")

        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.updateAmount(amount)

        assertThat(presenter.form.amount).isEqualTo(amount)
        assertThat(presenter.form.isUsingAllFunds).isFalse()
    }

    @Test
    fun `updates amount with use-all-funds when asked`() {
        val maxSpendableSatoshis = BitcoinUtils.bitcoinsToSatoshis(Money.of(1, "BTC"))
        val rates = mapOf("BTC" to 1.0, "ARS" to 2.0)

        resolveVerify(PaymentRequest.toAddress(SOME_ADDRESS, Money.of(0, "ARS"), "", 10.0))

        maxSpendableAmount.onNext(maxSpendableSatoshis)
        exchangeRates.onNext(ExchangeRateProvider(rates))

        presenter.confirmAmountUseAllFunds()

        presenter.form.let {
            assertThat(it.amount).isEqualTo(Money.of(2, "ARS"))
        }
    }

    @Test
    fun `updates description when asked`() {
        val before = "Some description"
        val after = "Some OTHER description"

        resolveVerify(PaymentRequest.toAddress(SOME_ADDRESS, Money.of(0, "ARS"), before, 10.0))
        assertThat(presenter.form.description).isEqualTo(before)

        presenter.updateDescription(after)
        assertThat(presenter.form.description).isEqualTo(after)
    }


    // ---------------------------------------------------------------------------------------------
    // Private helpers:

    private fun resolveVerify(payReq: PaymentRequest) {
        presenter.onViewCreated(ANY_URI, NewOperationOrigin.SCAN_QR)
        presenter.setUp(Bundle())

        resolveOperationUriResult.onNext(ActionState.createValue(payReq))

        assertThat(presenter.form.payReq).isEqualTo(payReq)
    }

    private fun assertFormInitialState(form: NewOperationForm, shouldBeReady: Boolean) {
        assertThat(form.payReq != null).isEqualTo(shouldBeReady)

        // The rest of the initial state is the same for all URIs:
        assertThat(form.amount).isEqualTo(Money.of(0, "BTC"))
        assertThat(form.isAmountConfirmed).isFalse()
        assertThat(form.description).isEmpty()
        assertThat(form.isDescriptionConfirmed).isFalse()
    }

    private fun assertObservers(list: List<BehaviorSubject<*>>, hasObservers: Boolean) {
        list.forEach { assertThat(it.hasObservers()).isEqualTo(hasObservers) }
    }

    open class NewOperationPresenterWithMocks(
        a: ResolveOperationUriAction,
        b: PaymentContextSelector,
        c: SubmitPaymentAction,
        d: FetchRealTimeDataAction,
        e: CurrencyDisplayModeSelector,
        f: UserSelector,
        g: ExchangeRateSelector
    ) : NewOperationPresenter(a, b, c, d, e, f, g) {

        override fun setUpNetworkInfo() {}
        override fun tearDownNetworkInfo() {}

        override fun <T : Any?> getAsyncExecutor(): Observable.Transformer<T, T> {
            return Observable.Transformer { it }
        }
    }
}
