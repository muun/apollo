package io.muun.apollo.domain.libwallet

import android.os.CancellationSignal
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.muun.apollo.data.libwallet.UtxoScanUpdate
import io.muun.apollo.data.nfc.NfcBridger
import io.muun.apollo.domain.action.ek.GenerateEmergencyKitPDF
import io.muun.apollo.domain.errors.MuunErrorMapper
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError
import io.muun.apollo.domain.libwallet.errors.BackgroundActionsRunInterruptedError.Reason
import io.muun.apollo.domain.libwallet.errors.ErrorDetailType
import io.muun.apollo.domain.libwallet.errors.LibwalletGrpcError
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.background_actions.ExecutionContext
import io.muun.apollo.domain.model.marketplace.MarketplaceCountryCode
import io.muun.apollo.domain.model.marketplace.SecurityCardDetail
import io.muun.apollo.domain.model.marketplace.SecurityCardFullSpecs
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.domain.model.marketplace.SecurityCardMaterial
import io.muun.apollo.domain.model.marketplace.SecurityCardProviderTheme
import io.muun.apollo.domain.model.marketplace.SecurityCardSecureElement
import org.javamoney.moneta.Money
import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import rpc.WalletServiceGrpc
import rpc.WalletServiceGrpc.WalletServiceBlockingStub
import rpc.WalletServiceGrpc.WalletServiceStub
import rpc.WalletServiceOuterClass.DeleteRequest
import rpc.WalletServiceOuterClass.DiagnosticSessionDescriptor
import rpc.WalletServiceOuterClass.EKInputRequest
import rpc.WalletServiceOuterClass.ExchangeRateWindow as RpcExchangeRateWindow
import rpc.WalletServiceOuterClass.GenerateEmergencyKitPDFRequest
import rpc.WalletServiceOuterClass.GenerateEmergencyKitPDFResponse
import rpc.WalletServiceOuterClass.GetRequest
import rpc.WalletServiceOuterClass.NullValue
import rpc.WalletServiceOuterClass.RunDueBackgroundActionsRequest
import rpc.WalletServiceOuterClass.SCMBitcoinAmount
import rpc.WalletServiceOuterClass.SCMCardDetailRequest
import rpc.WalletServiceOuterClass.SCMCardDetailResponse
import rpc.WalletServiceOuterClass.SCMCountriesByProviderRequest
import rpc.WalletServiceOuterClass.SCMCountryInfo
import rpc.WalletServiceOuterClass.SCMFullSpecsRequest
import rpc.WalletServiceOuterClass.SCMFullSpecsResponse
import rpc.WalletServiceOuterClass.SCMMarketplaceRequest
import rpc.WalletServiceOuterClass.SCMMarketplaceResponse
import rpc.WalletServiceOuterClass.SCMProviderTheme
import rpc.WalletServiceOuterClass.SCMSaveControlPlaneRequest
import rpc.WalletServiceOuterClass.SaveExchangeRateWindowRequest
import rpc.WalletServiceOuterClass.SaveRequest
import rpc.WalletServiceOuterClass.Value
import rpc.WalletServiceOuterClass.ZipDataDirRequest
import rx.Emitter
import rx.Observable
import rx.schedulers.Schedulers
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import io.grpc.Context as GrpcContext
import rpc.WalletServiceOuterClass.ExecutionContext as RpcExecutionContext

class LibwalletClient(private val channel: ManagedChannel) {
    private val asyncStub = WalletServiceGrpc.newStub(channel)
    private val blockingStub = WalletServiceGrpc.newBlockingStub(channel)

    private val emptyMessage = Empty.getDefaultInstance()

    /**
     * Sign with the security card the user taps, and return the firmware version it reported.
     */
    fun securityCardSignMessage(nfcBridger: NfcBridger): String {
        val response = withNfcBridge(nfcBridger) {
            blockingStub.performSyncRequest {
                signMessageSecurityCard(emptyMessage)
            }
        }

        return response.firmwareVersion
    }

    fun generateEmergencyKitPDF(
        data: GenerateEmergencyKitPDF.RequiredData,
        outputPath: String,
        language: String,
    ): GenerateEmergencyKitPDFResponse {
        val ekInput = EKInputRequest.newBuilder()
            .setFirstEncryptedKey(data.userKey)
            .setFirstFingerprint(data.userFingerprint)
            .setSecondEncryptedKey(data.muunKey)
            .setSecondFingerprint(data.muunFingerprint)
            .setRcChecksum(data.rcChecksum)
            .build()

        val request = GenerateEmergencyKitPDFRequest.newBuilder()
            .setEkInput(ekInput)
            .setOutputPath(outputPath)
            .setLanguage(language)
            .build()

        return blockingStub.performSyncRequest {
            generateEmergencyKitPDF(request)
        }
    }

    fun startDiagnosticSession(): Observable<String> {
        val observable = asyncStub.performAsyncRequest { streamObserver ->
            startDiagnosticSession(emptyMessage, streamObserver)
        }
        return observable.map { it.sessionId }
    }

    fun scanForUtxos(sessionId: String): Observable<UtxoScanUpdate> {
        val scanParams = DiagnosticSessionDescriptor.newBuilder()
            .setSessionId(sessionId)
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            performDiagnosticScanForUtxos(scanParams, streamObserver)
        }
        return observable.map {
            UtxoScanUpdate(
                scanComplete = it.hasScanComplete(),
                scanStatus = it.scanComplete?.status,
                address = it.foundUtxoReport?.address,
                amount = it.foundUtxoReport?.amount
            )
        }
    }

    fun submitDiagnosticLog(sessionId: String): Observable<String> {
        val scanParams = DiagnosticSessionDescriptor.newBuilder()
            .setSessionId(sessionId)
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            submitDiagnosticLog(scanParams, streamObserver)
        }
        return observable.map {
            it.statusMessage
        }
    }

    fun shutdown() {
        channel.shutdownNow()
    }

    private fun save(key: String, rpcValue: Value) {
        val saveRequest = SaveRequest.newBuilder()
            .setKey(key)
            .setValue(rpcValue)
            .build()

        blockingStub.performSyncRequest {
            save(saveRequest)
        }
    }

    private fun get(key: String): Value {
        val getRequest = GetRequest.newBuilder()
            .setKey(key)
            .build()

        val getResponse = blockingStub.performSyncRequest {
            get(getRequest)
        }
        return getResponse.value
    }

    fun saveString(key: String, value: String?) {
        val rpcValue: Value =
            if (value != null) {
                Value.newBuilder().setStringValue(value).build()
            } else {
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
            }

        save(key, rpcValue)
    }

    fun getString(key: String): String? {
        val rpcValue = get(key)

        if (rpcValue.hasStringValue()) {
            return rpcValue.stringValue
        } else {
            if (rpcValue.hasNullValue()) {
                return null
            }
            throw Exception("Value for key " + key + "is not of type String")
        }
    }

    fun getString(key: String, defaultValue: String): String =
        getString(key) ?: defaultValue

    fun saveBoolean(key: String, value: Boolean?) {
        val rpcValue: Value =
            if (value != null) {
                Value.newBuilder().setBoolValue(value).build()
            } else {
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
            }

        save(key, rpcValue)
    }

    fun getBoolean(key: String): Boolean? {
        val rpcValue = get(key)

        if (rpcValue.hasBoolValue()) {
            return rpcValue.boolValue
        } else {
            if (rpcValue.hasNullValue()) {
                return null
            }
            throw Exception("Value for key " + key + "is not of type Boolean")
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        getBoolean(key) ?: defaultValue

    fun <T : Enum<T>> saveEnum(key: String, value: T?) {
        saveString(key, value?.name)
    }

    inline fun <reified T : Enum<T>> getEnum(key: String): T? =
        getString(key)?.let { enumValueOf<T>(it) }

    inline fun <reified T : Enum<T>> getEnum(key: String, defaultValue: T): T =
        getEnum<T>(key) ?: defaultValue

    fun saveInt(key: String, value: Int?) {
        val rpcValue: Value =
            if (value != null) {
                Value.newBuilder().setIntValue(value).build()
            } else {
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
            }

        save(key, rpcValue)
    }

    fun getInt(key: String): Int? {
        val rpcValue = get(key)

        if (rpcValue.hasIntValue()) {
            return rpcValue.intValue
        } else {
            if (rpcValue.hasNullValue()) {
                return null
            }
            throw Exception("Value for key " + key + "is not of type Int")
        }
    }

    fun getInt(key: String, defaultValue: Int): Int =
        getInt(key) ?: defaultValue

    fun resetData() {
        blockingStub.performSyncRequest {
            resetData(emptyMessage)
        }
    }

    /**
     * Run the due background actions, blocking until they finish or timeout expires.
     * Cancelling the signal cancels the call.
     *
     * @throws BackgroundActionsRunInterruptedError when the call was canceled or ran out of time.
     * @throws io.muun.apollo.domain.errors.MuunError on any other failure.
     */
    fun runDueBackgroundActions(
        executionContext: ExecutionContext,
        timeout: Duration,
        cancellation: CancellationSignal,
    ) {
        val request = RunDueBackgroundActionsRequest.newBuilder()
            .setExecutionContext(executionContext.toRpc())
            .build()

        val cancellable = GrpcContext.current().withCancellation()
        // Fires immediately if the signal is already canceled, so a stop that lands before the
        // call starts still cancels it.
        cancellation.setOnCancelListener { cancellable.cancel(null) }

        try {
            cancellable.run {
                blockingStub
                    .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .runDueBackgroundActions(request)
            }

        } catch (e: StatusRuntimeException) {
            throw when (e.status.code) {
                Status.Code.CANCELLED -> BackgroundActionsRunInterruptedError(Reason.CANCELLED, e)
                Status.Code.DEADLINE_EXCEEDED ->
                    BackgroundActionsRunInterruptedError(Reason.TIMEOUT, e)
                else -> toMuunError(LibwalletGrpcError(e))
            }

        } finally {
            cancellation.setOnCancelListener(null)
            cancellable.close()
        }
    }

    fun zipDataDir(outputPath: String) {
        val request = ZipDataDirRequest.newBuilder()
            .setOutputPath(outputPath)
            .build()

        blockingStub.performSyncRequest {
            zipDataDir(request)
        }
    }

    fun delete(key: String) {
        val deleteRequest = DeleteRequest.newBuilder()
            .setKey(key)
            .build()

        blockingStub.performSyncRequest {
            delete(deleteRequest)
        }
    }

    fun saveExchangeRateWindow(window: ExchangeRateWindow) {
        val request = SaveExchangeRateWindowRequest.newBuilder()
            .setWindow(window.toRpc())
            .build()

        blockingStub.performSyncRequest {
            saveExchangeRateWindow(request)
        }
    }

    /**
     * Returns null when no window has been stored yet.
     */
    fun getExchangeRateWindow(): ExchangeRateWindow? {
        val response = blockingStub.performSyncRequest {
            getExchangeRateWindow(emptyMessage)
        }
        return if (response.hasWindow()) {
            response.window.toExchangeRateWindow()
        } else {
            null
        }
    }

    fun fetchSecurityCardsMarketplace(
        countryCode: MarketplaceCountryCode,
    ): Observable<SecurityCardMarketplace> {
        val request = SCMMarketplaceRequest.newBuilder()
            .setCountry(
                SCMCountryInfo.newBuilder().setCode(countryCode.value).build()
            )
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            fetchSecurityCardsMarketplaceByCountry(request, streamObserver)
        }
        return observable
            .map { it.toSecurityCardMarketplace() }
            .subscribeOn(Schedulers.io())
    }

    fun fetchSecurityCardDetail(
        countryCode: MarketplaceCountryCode,
        cardUuid: String,
    ): Observable<SecurityCardDetail> {
        val request = SCMCardDetailRequest.newBuilder()
            .setCountry(
                SCMCountryInfo.newBuilder().setCode(countryCode.value).build()
            )
            .setSecurityCardUuid(cardUuid)
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            fetchSecurityCardDetail(request, streamObserver)
        }
        return observable
            .map { it.toSecurityCardDetail() }
            .subscribeOn(Schedulers.io())
    }

    fun fetchSecurityCardFullSpecs(
        countryCode: MarketplaceCountryCode,
        cardUuid: String,
    ): Observable<SecurityCardFullSpecs> {
        val request = SCMFullSpecsRequest.newBuilder()
            .setCountry(
                SCMCountryInfo.newBuilder().setCode(countryCode.value).build()
            )
            .setSecurityCardUuid(cardUuid)
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            fetchSecurityCardFullSpecs(request, streamObserver)
        }
        return observable
            .map { it.toSecurityCardFullSpecs() }
            .subscribeOn(Schedulers.io())
    }

    /**
     * Push the marketplace control plane into libwallet. Never throws: a
     * failed push leaves the marketplace on the last stored control plane
     * (or closed), and callers must not fail because of it. The error is
     * already reported by performSyncRequest, so a warning breadcrumb is
     * enough here.
     */
    fun saveMarketplaceControlPlane(controlPlane: SCMSaveControlPlaneRequest) {
        try {
            blockingStub.performSyncRequest {
                saveSecurityCardsMarketplaceControlPlane(controlPlane)
            }
        } catch (error: RuntimeException) {
            Timber.w(error, "Failed to push the marketplace control plane")
        }
    }

    fun fetchSecurityCardsCountries(): Observable<List<MarketplaceCountryCode>> {
        val observable = asyncStub.performAsyncRequest { streamObserver ->
            fetchSecurityCardsCountries(emptyMessage, streamObserver)
        }

        return observable
            .map { response ->
                response.countriesList.mapNotNull { it.code.toMarketplaceCountryCodeOrNull() }
            }
            .subscribeOn(Schedulers.io())
    }

    fun fetchSecurityCardsCountriesByProvider(
        providerUuid: String,
    ): Observable<List<MarketplaceCountryCode>> {
        val request = SCMCountriesByProviderRequest.newBuilder()
            .setProviderUuid(providerUuid)
            .build()

        val observable = asyncStub.performAsyncRequest { streamObserver ->
            fetchSecurityCardsCountriesByProvider(request, streamObserver)
        }

        return observable
            .map { response ->
                response.countriesList.mapNotNull { it.code.toMarketplaceCountryCodeOrNull() }
            }
            .subscribeOn(Schedulers.io())
    }

    private fun <T> withNfcBridge(nfcBridger: NfcBridger, block: () -> T): T {
        nfcBridger.setupBridge()
        try {
            return block()
        } finally {
            nfcBridger.tearDownBridge()
        }
    }

    private inline fun <T> WalletServiceBlockingStub.performSyncRequest(
        crossinline rpcCall: WalletServiceBlockingStub.() -> T,
    ): T {
        try {
            return rpcCall()
        } catch (e: StatusRuntimeException) {
            throw mapToMuunError(e)
        } catch (t: Throwable) {
            Timber.e(t)
            throw t
        }
    }

    private inline fun <T> WalletServiceStub.performAsyncRequest(
        crossinline rpcCall: WalletServiceStub.(StreamObserver<T>) -> Unit,
    ): Observable<T> {
        val handler = { emitter: Emitter<T> ->
            val observer = EventForwardingStreamObserver(emitter)
            try {
                rpcCall(observer)
            } catch (e: StatusRuntimeException) {
                val rpcError = mapToMuunError(e)
                emitter.onError(rpcError)
            } catch (t: Throwable) {
                Timber.e(t)
                emitter.onError(t)
            }
        }
        return Observable.create(handler, Emitter.BackpressureMode.BUFFER)
    }

    private fun mapToMuunError(statusException: StatusRuntimeException): RuntimeException {
        val grpcError = LibwalletGrpcError(statusException)
        Timber.e(grpcError)
        return toMuunError(grpcError)
    }

    private fun toMuunError(grpcError: LibwalletGrpcError): RuntimeException {
        return grpcError.errorDetail?.code
            ?.takeIf { grpcError.errorDetail.type == ErrorDetailType.HOUSTON }
            ?.let(MuunErrorMapper::map)
            ?: grpcError
    }


}

class EventForwardingStreamObserver<T>(private val emitter: Emitter<T>) : StreamObserver<T> {
    override fun onNext(value: T) {
        emitter.onNext(value)
    }

    override fun onError(t: Throwable) {
        emitter.onError(t)
    }

    override fun onCompleted() {
        emitter.onCompleted()
    }
}

internal fun ExchangeRateWindow.toRpc(): RpcExchangeRateWindow {
    return RpcExchangeRateWindow.newBuilder()
        .setWindowId(windowHid)
        .setFetchDateInMillis(fetchDate.toInstant().toEpochMilli())
        .putAllRates(rates)
        .build()
}

internal fun RpcExchangeRateWindow.toExchangeRateWindow(): ExchangeRateWindow {
    return ExchangeRateWindow(
        windowId,
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(fetchDateInMillis), ZoneOffset.UTC),
        ratesMap
    )
}

private fun SCMMarketplaceResponse.toSecurityCardMarketplace(): SecurityCardMarketplace {
    return SecurityCardMarketplace(
        providers = providersList.map { provider ->
            SecurityCardMarketplace.SecurityCardProvider(
                uuid = provider.provider.uuid,
                name = provider.provider.name,
                lightTheme = provider.provider.lightTheme.toSecurityCardProviderTheme(),
                darkTheme = provider.provider.darkTheme.toSecurityCardProviderTheme(),
                securityCards = provider.cardsList.map { card ->
                    SecurityCardMarketplace.SecurityCard(
                        uuid = card.uuid,
                        imageUrl = card.imageUrl,
                        hasStock = card.hasStock,
                        material = SecurityCardMaterial.valueOf(card.material.name),
                    )
                },
                cardPrice = provider.cardPrice.toPrimaryMonetaryAmount(),
                cardPriceInSats = provider.cardPrice.inSat,
                shippingPrice = provider.estimatedShippingPrice.toPrimaryMonetaryAmount(),
                shippingPriceInSats = provider.estimatedShippingPrice.inSat,
            )
        }
    )
}

private fun SCMBitcoinAmount.toPrimaryMonetaryAmount(): Money {
    return Money.of(BigDecimal(inPrimaryCurrency.amount), inPrimaryCurrency.currencyCode)
}

private fun SCMProviderTheme.toSecurityCardProviderTheme(): SecurityCardProviderTheme {
    return SecurityCardProviderTheme(
        primaryColorHex = primaryColorHex.withOpaqueAlphaIfMissing(),
        surfaceColorHex = surfaceColorHex.withOpaqueAlphaIfMissing(),
    )
}

// TODO: contract should be ARGB but mock currently returns RGB, this will change, don't review
private fun Int.withOpaqueAlphaIfMissing(): Int {
    val colorAlphaMask = 0xFF000000.toInt()

    return if (this and colorAlphaMask == 0) this or colorAlphaMask else this
}

private fun SCMCardDetailResponse.toSecurityCardDetail(): SecurityCardDetail {
    return SecurityCardDetail(
        provider = SecurityCardDetail.SecurityCardProvider(
            uuid = provider.provider.uuid,
            name = provider.provider.name,
            lightTheme = provider.provider.lightTheme.toSecurityCardProviderTheme(),
            darkTheme = provider.provider.darkTheme.toSecurityCardProviderTheme(),
            websiteUrl = provider.provider.siteUrl,
            shippingCountryCode = MarketplaceCountryCode(provider.shippingCountry.code),
            minShippingTimeDays = provider.minShippingTimeDays,
            maxShippingTimeDays = provider.maxShippingTimeDays,
            cardPrice = provider.cardPrice.toPrimaryMonetaryAmount(),
            cardPriceInSats = provider.cardPrice.inSat,
            shippingPrice = provider.estimatedShippingPrice.toPrimaryMonetaryAmount(),
            shippingPriceInSats = provider.estimatedShippingPrice.inSat,
        ),
        card = SecurityCardDetail.SecurityCard(
            uuid = card.uuid,
            imageUrl = card.imageUrl,
            hasStock = card.hasStock,
            material = SecurityCardMaterial.valueOf(card.material.name),
        ),
    )
}

private fun SCMFullSpecsResponse.toSecurityCardFullSpecs(): SecurityCardFullSpecs {
    return SecurityCardFullSpecs(
        provider = SecurityCardFullSpecs.SecurityCardProvider(
            uuid = provider.provider.uuid,
            name = provider.provider.name,
            lightTheme = provider.provider.lightTheme.toSecurityCardProviderTheme(),
            darkTheme = provider.provider.darkTheme.toSecurityCardProviderTheme(),
            shippingCountryCode = MarketplaceCountryCode(provider.shippingCountry.code),
            minShippingTimeDays = provider.minShippingTimeDays,
            maxShippingTimeDays = provider.maxShippingTimeDays,
        ),
        card = SecurityCardFullSpecs.SecurityCard(
            uuid = card.card.uuid,
            imageUrl = card.card.imageUrl,
            hasStock = card.card.hasStock,
            widthMm = card.widthMm,
            heightMm = card.heightMm,
            material = SecurityCardMaterial.valueOf(card.card.material.name),
            thicknessMm = card.thicknessMm,
            weightGrams = card.weightGrams,
            secureElement = SecurityCardSecureElement.valueOf(card.secureElement.name),
        ),
    )
}

/**
 * Build a country code from an untrusted backend value, or null for a code
 * this build doesn't know, reporting it: the backoffice (control plane) can
 * serve a code newer than this build, and every consumer downstream assumes
 * codes are renderable.
 */
private fun String.toMarketplaceCountryCodeOrNull(): MarketplaceCountryCode? {
    return try {
        MarketplaceCountryCode(this)
    } catch (error: IllegalArgumentException) {
        Timber.e(error, "Skipping unknown country code")
        null
    }
}

private fun ExecutionContext.toRpc(): RpcExecutionContext {
    return when (this) {
        ExecutionContext.FOREGROUND -> RpcExecutionContext.ANDROID_FOREGROUND
        ExecutionContext.BACKGROUND -> RpcExecutionContext.ANDROID_BACKGROUND
    }
}
