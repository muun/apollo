package io.muun.apollo.domain.libwallet

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import io.muun.apollo.data.libwallet.UtxoScanUpdate
import io.muun.apollo.data.nfc.NfcBridger
import io.muun.apollo.domain.errors.MuunErrorMapper
import io.muun.apollo.domain.libwallet.errors.ErrorDetailType
import io.muun.apollo.domain.libwallet.errors.LibwalletGrpcError
import rpc.WalletServiceGrpc
import rpc.WalletServiceGrpc.WalletServiceBlockingStub
import rpc.WalletServiceGrpc.WalletServiceStub
import rpc.WalletServiceOuterClass.DeleteRequest
import rpc.WalletServiceOuterClass.DiagnosticSessionDescriptor
import rpc.WalletServiceOuterClass.GetRequest
import rpc.WalletServiceOuterClass.NullValue
import rpc.WalletServiceOuterClass.SaveRequest
import rpc.WalletServiceOuterClass.SignMessageSecurityCardRequest
import rpc.WalletServiceOuterClass.Value
import rx.Emitter
import rx.Observable
import timber.log.Timber

class LibwalletClient(private val channel: ManagedChannel) {
    private val asyncStub = WalletServiceGrpc.newStub(channel)
    private val blockingStub = WalletServiceGrpc.newBlockingStub(channel)

    private val emptyMessage = Empty.getDefaultInstance()

    fun setUpSecurityCard(nfcBridger: NfcBridger) {

        nfcBridger.setupBridge()
        val response = blockingStub.performSyncRequest {
            setupSecurityCardV2(emptyMessage)
        }
        nfcBridger.tearDownBridge()

        Timber.d("Paired Security Card - isKnownProvider: ${response.isKnownProvider}")
        Timber.d("Paired Security Card - isCardAlreadyUsed: ${response.isCardAlreadyUsed}")
    }

    fun resetSecurityCard(nfcBridger: NfcBridger) {

        nfcBridger.setupBridge()
        blockingStub.performSyncRequest {
            resetSecurityCard(emptyMessage)
        }
        nfcBridger.tearDownBridge()

        Timber.d("Reset Security Card")
    }

    fun securityCardSignMessage(nfcBridger: NfcBridger, message: String): ByteArray {

        val request = SignMessageSecurityCardRequest.newBuilder()
            .setMessageHex(message)
            .build()

        nfcBridger.setupBridge()
        val signMessageNfcCardResponse = blockingStub.performSyncRequest {
            signMessageSecurityCard(request)
        }
        nfcBridger.tearDownBridge()

        return signMessageNfcCardResponse.signedMessageHex.toByteArray()
    }

    fun securityCardV2SignMessage(nfcBridger: NfcBridger) {

        nfcBridger.setupBridge()
        blockingStub.performSyncRequest {
            signMessageSecurityCardV2(emptyMessage)
        }
        nfcBridger.tearDownBridge()
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

    fun delete(key: String) {
        val deleteRequest = DeleteRequest.newBuilder()
            .setKey(key)
            .build()

        blockingStub.performSyncRequest {
            delete(deleteRequest)
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
