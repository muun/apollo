package io.muun.apollo.domain.libwallet

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import io.muun.apollo.data.libwallet.UtxoScanUpdate
import io.muun.apollo.data.nfc.NfcBridger
import rpc.WalletServiceGrpc
import rpc.WalletServiceOuterClass
import rpc.WalletServiceOuterClass.DiagnosticSessionDescriptor
import rpc.WalletServiceOuterClass.GetRequest
import rpc.WalletServiceOuterClass.NullValue
import rpc.WalletServiceOuterClass.OperationStatus
import rpc.WalletServiceOuterClass.SignMessageSecurityCardRequest
import rpc.WalletServiceOuterClass.SaveRequest
import rpc.WalletServiceOuterClass.Value
import rx.Emitter
import rx.Observable
import timber.log.Timber

class WalletClient(private val channel: ManagedChannel) {
    private val asyncStub = WalletServiceGrpc.newStub(channel)
    private val blockingStub = WalletServiceGrpc.newBlockingStub(channel)

    private val emptyMessage = Empty.getDefaultInstance()

    fun deleteWallet(): Observable<OperationStatus>? {
        val handler = { emitter: Emitter<OperationStatus> ->
            val observer = EventForwardingStreamObserver(emitter)
            asyncStub.deleteWallet(emptyMessage, observer)
        }

        return Observable.create(handler, Emitter.BackpressureMode.BUFFER)
    }

    fun deleteWalletBlocking(): OperationStatus? {
        return blockingStub.deleteWallet(emptyMessage)
    }

    fun setUpSecurityCard(nfcBridger: NfcBridger) {

        nfcBridger.setupBridge()
        val xpubResponse = blockingStub.setupSecurityCard(emptyMessage)
        nfcBridger.tearDownBridge()

        Timber.d("Paired Security Card: ${xpubResponse.base58Xpub}")
    }

    fun resetSecurityCard(nfcBridger: NfcBridger) {

        nfcBridger.setupBridge()
        blockingStub.resetSecurityCard(emptyMessage)
        nfcBridger.tearDownBridge()

        Timber.d("Reset Security Card")
    }

    fun securityCardSignMessage(nfcBridger: NfcBridger, message: String): ByteArray {

        val request = SignMessageSecurityCardRequest.newBuilder()
            .setMessageHex(message)
            .build()

        nfcBridger.setupBridge()
        val signMessageNfcCardResponse = blockingStub.signMessageSecurityCard(request)
        nfcBridger.tearDownBridge()

        return signMessageNfcCardResponse.signedMessageHex.toByteArray()
    }

    fun startDiagnosticSession(): Observable<String> {
        val handler = { emitter: Emitter<DiagnosticSessionDescriptor> ->
            val observer = EventForwardingStreamObserver(emitter)
            asyncStub.startDiagnosticSession(emptyMessage, observer)
        }

        return Observable.create(handler, Emitter.BackpressureMode.BUFFER)
            .map { it.sessionId }
    }

    fun scanForUtxos(sessionId: String): Observable<UtxoScanUpdate> {
        val scanParams = DiagnosticSessionDescriptor.newBuilder()
            .setSessionId(sessionId)
            .build()
        val handler = { emitter: Emitter<WalletServiceOuterClass.ScanProgressUpdate> ->
            val observer = EventForwardingStreamObserver(emitter)
            asyncStub.performDiagnosticScanForUtxos(scanParams, observer)
        }

        return Observable.create(handler, Emitter.BackpressureMode.BUFFER)
            .map {
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

        val handler = { emitter: Emitter<WalletServiceOuterClass.DiagnosticSubmitStatus> ->
            val observer = EventForwardingStreamObserver(emitter)
            asyncStub.submitDiagnosticLog(scanParams, observer)
        }

        return Observable.create(handler, Emitter.BackpressureMode.BUFFER)
            .map {
                it.statusMessage
            }
    }

    fun shutdown() {
        channel.shutdownNow()
    }

    private fun save(key: String, rpcValue: Value) {
        try {
            val saveRequest = SaveRequest.newBuilder()
                .setKey(key)
                .setValue(rpcValue)
                .build()

            blockingStub.save(saveRequest)
        } catch(e: Exception) {
            Timber.e(e)
            throw e
        }
    }

    private fun get(key: String): Value {
        val getRequest = GetRequest.newBuilder()
            .setKey(key)
            .build()

        try {
            val getResponse = blockingStub.get(getRequest)
            return getResponse.value
        } catch (e: Exception) {
            Timber.e(e)
            throw e
        }
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
            return rpcValue.getStringValue()
        } else {
            if (rpcValue.hasNullValue()) {
                return null
            }
            throw Exception("Value for key " + key + "is not of type String")
        }
    }

    fun getString(key: String, defaultValue: String) : String =
        getString(key) ?: defaultValue

    fun <T : Enum<T>> saveEnum(key: String, value: T?) {
        saveString(key, value?.name)
    }

    inline fun <reified T : Enum<T>> getEnum(key: String): T? =
        getString(key) ?.let { enumValueOf<T>(it) }

    inline fun <reified T : Enum<T>> getEnum(key: String, defaultValue: T): T =
        getEnum<T>(key) ?: defaultValue

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
