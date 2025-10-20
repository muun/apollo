package io.muun.apollo.domain.libwallet.errors

import com.google.rpc.Status
import io.grpc.Metadata
import io.grpc.StatusRuntimeException
import io.grpc.protobuf.lite.ProtoLiteUtils
import io.muun.apollo.domain.errors.MuunError
import rpc.WalletServiceOuterClass
import rpc.WalletServiceOuterClass.ErrorType
import timber.log.Timber

class LibwalletGrpcError(cause: StatusRuntimeException) : MuunError(cause) {

    val errorDetail = mapToErrorDetail(parseErrorDetail(cause))

    init {
        metadata["status.code"] = cause.status.code.name
        metadata["status.description"] = cause.status.description ?: "null"
        metadata["type"] = errorDetail?.type ?: "null"
        metadata["code"] = errorDetail?.code ?: "null"
        metadata["message"] = errorDetail?.message ?: "null"
        metadata["developerMessage"] = errorDetail?.developerMessage ?: "null"
    }

    override fun toString(): String {
        return "${errorDetail.toString()}\n${super.toString()}"
    }

    private fun parseErrorDetail(e: StatusRuntimeException): WalletServiceOuterClass.ErrorDetail? {

        try {
            val statusDetailsKey = Metadata.Key.of(
                "grpc-status-details-bin",
                ProtoLiteUtils.metadataMarshaller(Status.getDefaultInstance())
            )

            e.trailers
                ?.get(statusDetailsKey)
                ?.detailsList
                ?.firstOrNull {
                    // typeUrl follows the format "type.googleapis.com/[package].[MessageName]"
                    // where [package] and [MessageName] are defined in the .proto file of the msg
                    it.typeUrl == "type.googleapis.com/rpc.ErrorDetail"
                }
                ?.let {
                    return WalletServiceOuterClass.ErrorDetail.parseFrom(it.value.toByteArray())
                }

            return null
        } catch (e: Exception) {
            Timber.e("cannot extract ErrorDetail from StatusRuntimeException")
            return null
        }
    }

    private fun mapToErrorDetail(grpcErrorDetail: WalletServiceOuterClass.ErrorDetail?): ErrorDetail? {
        if (grpcErrorDetail == null) {
            return null
        }

        return ErrorDetail(
            mapToErrorDetailType(grpcErrorDetail.type),
            grpcErrorDetail.code,
            grpcErrorDetail.message,
            grpcErrorDetail.developerMessage
        )
    }

    private fun mapToErrorDetailType(grpcErrorType: ErrorType): ErrorDetailType {
        return when (grpcErrorType) {
            ErrorType.CLIENT -> ErrorDetailType.CLIENT
            ErrorType.LIBWALLET -> ErrorDetailType.LIBWALLET
            ErrorType.HOUSTON -> ErrorDetailType.HOUSTON
            ErrorType.UNRECOGNIZED -> ErrorDetailType.UNKNOWN
        }
    }
}