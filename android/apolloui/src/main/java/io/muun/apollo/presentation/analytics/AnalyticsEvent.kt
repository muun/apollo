package io.muun.apollo.presentation.analytics

import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.model.NightMode
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.presentation.ui.new_operation.NewOperationOrigin
import io.muun.common.model.OperationDirection

/**
 * AnalyticsEvent list, shared with Falcon. Names are lower-cased into FBA event IDs, do not rename.
 */
sealed class AnalyticsEvent(metadataKeyValues: List<Pair<String, Any>> = listOf()) {

    val eventId = javaClass.simpleName.toLowerCase()
    val metadata = metadataKeyValues.toMap()

    // Screen navigation events:
    class S_GET_STARTED: AnalyticsEvent()
    class S_SIGN_IN_EMAIL: AnalyticsEvent()
    class S_SIGN_UP_EMAIL: AnalyticsEvent() // Legacy naming convention, not exactly appropiate.
    class S_SIGN_IN_PASSWORD: AnalyticsEvent()
    class S_SIGN_UP_PASSWORD: AnalyticsEvent() // Legacy naming convention, not exactly appropiate.
    class S_INPUT_RECOVERY_CODE: AnalyticsEvent()
    class S_VERIFY_EMAIL: AnalyticsEvent()
    class S_AUTHORIZE_EMAIL: AnalyticsEvent()
    class S_PIN_CHOOSE: AnalyticsEvent()
    class S_PIN_REPEAT: AnalyticsEvent()
    class S_PIN_LOCKED: AnalyticsEvent()
    class S_SIGN_IN_WITH_RC: AnalyticsEvent()
    class S_SIGN_IN_WITH_RC_AUTHORIZE_EMAIL: AnalyticsEvent()

    enum class S_HOME_TYPE {
        ANON_USER_WITHOUT_OPERATIONS,
        ANON_USER_WITH_OPERATIONS,
        USER_SET_UP_WITHOUT_OPERATIONS,
        USER_SET_UP_WITH_OPERATIONS
    }
    class S_HOME(type: S_HOME_TYPE): AnalyticsEvent(listOf(
            "type" to type.name.toLowerCase()
    ))

    enum class RECEIVE_ORIGIN {
        RECEIVE_BUTTON,
        TX_EMPTY_STATE

    }

    class S_RECEIVE(type: S_RECEIVE_TYPE, origin: RECEIVE_ORIGIN): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase(),
        "origin" to origin.name.toLowerCase()
    ))
    class S_AMOUNT_PICKER: AnalyticsEvent()

    class S_SCAN_QR: AnalyticsEvent()
    class S_NEW_OP_LOADING: AnalyticsEvent()
    class S_NEW_OP_AMOUNT: AnalyticsEvent()
    class S_NEW_OP_DESCRIPTION: AnalyticsEvent()
    class S_NEW_OP_CONFIRMATION: AnalyticsEvent()
    class S_SETTINGS: AnalyticsEvent()
    class S_CURRENCY_PICKER: AnalyticsEvent()
    class S_SET_UP_RECOVERY_CODE_GENERATE: AnalyticsEvent()
    class S_SET_UP_RECOVERY_CODE_VERIFY: AnalyticsEvent()
    class S_FINISH_RECOVERY_CODE_CONFIRM: AnalyticsEvent()

    enum class FEEDBACK_TYPE {
        EMAIL_SETUP_SUCCESS,
        RECOVERY_CODE_SUCCESS,
        EMERGENCY_KIT_SUCCESS,
        DELETE_WALLET
    }

    class S_FEEDBACK(type: FEEDBACK_TYPE): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase()
    ))

    class S_OPERATION_DETAIL(operationId: Int, direction: OperationDirection):
            AnalyticsEvent(listOf(
                    "operation_id" to operationId,
                    "direction" to direction.name.toLowerCase()
            ))

    class S_LOG_OUT: AnalyticsEvent()
    class S_SESSION_EXPIRED: AnalyticsEvent()
    class S_UPDATE_APP: AnalyticsEvent()
    class S_SYNC: AnalyticsEvent()
    class S_P2P_SETUP_PHONE: AnalyticsEvent()
    class S_P2P_SETUP_VERIFICATION_CODE: AnalyticsEvent()
    class S_P2P_SETUP_PROFILE: AnalyticsEvent()
    class S_P2P_SETUP_ENABLE_CONTACTS: AnalyticsEvent()
    class S_EDIT_USERNAME: AnalyticsEvent()
    class S_PASSWORD_CHANGE_START: AnalyticsEvent()
    class S_PASSWORD_CHANGE_END: AnalyticsEvent()
    class S_SEND: AnalyticsEvent()
    class S_SELECT_FEE: AnalyticsEvent()
    class S_MANUALLY_ENTER_FEE: AnalyticsEvent()
    class S_EMERGENCY_KIT_SLIDES(step: Int): AnalyticsEvent(listOf("step" to step))
    class S_EMERGENCY_KIT_SAVE: AnalyticsEvent()
    class S_EMERGENCY_KIT_VERIFY: AnalyticsEvent()
    class S_EMERGENCY_KIT_CLOUD_VERIFY: AnalyticsEvent()
    class S_EMERGENCY_KIT_HELP: AnalyticsEvent()
    class S_EXPORT_KEYS_RECOVERY_TOOL: AnalyticsEvent()
    class S_BITCOIN_UNIT_PICKER: AnalyticsEvent()
    class S_NIGHT_MODE_PICKER: AnalyticsEvent()

    enum class S_RECEIVE_TYPE {
        SEGWIT_ADDRESS,
        LEGACY_ADDRESS,
        LN_INVOICE
    }

    enum class S_NEW_OP_ORIGIN {
        SEND_CLIPBOARD_PASTE,
        SEND_MANUAL_INPUT,
        SEND_CONTACT,
        SCAN_QR,
        EXTERNAL_LINK;

        companion object {
            @JvmStatic
            fun fromModel(origin: NewOperationOrigin) = when (origin) {
                NewOperationOrigin.SEND_CLIPBOARD_PASTE -> SEND_CLIPBOARD_PASTE
                NewOperationOrigin.SEND_MANUAL_INPUT -> SEND_MANUAL_INPUT
                NewOperationOrigin.SEND_CONTACT -> SEND_CONTACT
                NewOperationOrigin.SCAN_QR -> SCAN_QR
                NewOperationOrigin.EXTERNAL_LINK -> EXTERNAL_LINK
            }
        }
    }

    enum class S_NEW_OP_ERROR_TYPE {
        INVALID_ADDRESS,
        EXPIRED_INVOICE,
        INVALID_INVOICE,
        INVOICE_EXPIRES_TOO_SOON,
        INVOICE_ALREADY_USED,
        INVOICE_MISSING_AMOUNT,
        NO_PAYMENT_ROUTE,
        INSUFFICIENT_FUNDS,
        AMOUNT_BELOW_DUST,
        EXCHANGE_RATE_WINDOW_TOO_OLD,
        INVALID_SWAP,
        OTHER
    }

    class S_NEW_OP_ERROR(val origin: S_NEW_OP_ORIGIN,
                         val type: S_NEW_OP_ERROR_TYPE,
                         vararg extras: Any): AnalyticsEvent(listOf(

        "origin" to origin.name.toLowerCase(),
        "type" to type.name.toLowerCase(),
        *extras.mapIndexed { index: Int, extra: Any -> Pair("extra$index", extra) }.toTypedArray()
    ))

    enum class S_MORE_INFO_TYPE {
        PASSWORD,
        SELECT_FEE,
        MANUAL_FEE,
        PHONE_NUMBER,
        PROFILE,
        CONFIRMATION_NEEDED,
        NEW_OP_DESTINATION,
        READ_CONTACTS
    }

    class S_MORE_INFO(val type: S_MORE_INFO_TYPE): AnalyticsEvent(listOf(
            "type" to type.name.toLowerCase()
    ))


    enum class S_SUPPORT_TYPE {
        FEEDBACK,
        ERROR,
        ANON_SUPPORT
    }

    class S_SUPPORT(val type: S_SUPPORT_TYPE): AnalyticsEvent(listOf(
            "type" to type.name.toLowerCase()
    ))

    enum class SECURITY_CENTER_ORIGIN {
        SHIELD_BUTTON,
        EMPTY_HOME_ANON_USER,
    }

    enum class S_SECURITY_CENTER_NEXT_STEP {
        EMAIL,
        RECOVERY_CODE,
        EMERGENCY_KIT,
        FULLY_SET
    }

    enum class S_SECURITY_CENTER_EMAIL_STATUS {
        NOT_SET,
        SKIPPED,
        COMPLETED,
    }

    class S_SECURITY_CENTER(nextStep: S_SECURITY_CENTER_NEXT_STEP,
                            origin: SECURITY_CENTER_ORIGIN,
                            emailStatus: S_SECURITY_CENTER_EMAIL_STATUS): AnalyticsEvent(listOf(
        "next_step" to nextStep.name.toLowerCase(),
        "origin" to origin.name.toLowerCase(),
        "email_status" to emailStatus.name.toLowerCase()
    ))

    class S_EMAIL_PRIMING: AnalyticsEvent()
    class S_EMAIL_ALREADY_USED : AnalyticsEvent()
    class S_RECOVERY_CODE_PRIMING: AnalyticsEvent()
    class S_FINISH_EMAIL_SETUP : AnalyticsEvent()
    class S_LNURL_FIRST_TIME: AnalyticsEvent()
    class S_LNURL_FROM_SEND: AnalyticsEvent()
    class S_LNURL_WITHDRAW: AnalyticsEvent()
    class S_LNURL_SCAN_QR: AnalyticsEvent()

    // User interaction events:

    enum class E_NEW_OP_TYPE {
        TO_ADDRESS,
        SUBMARINE_SWAP,
        TO_CONTACT;

        companion object {
            @JvmStatic
            fun fromModel(type: PaymentRequest.Type) = when(type) {
                PaymentRequest.Type.TO_CONTACT -> TO_CONTACT
                PaymentRequest.Type.TO_ADDRESS -> TO_ADDRESS
                PaymentRequest.Type.TO_LN_INVOICE -> SUBMARINE_SWAP
            }
        }
    }

    class E_NEW_OP_STARTED(vararg params: Pair<String, Any>): AnalyticsEvent(listOf(*params))

    class E_NEW_OP_COMPLETED(vararg params: Pair<String, Any>): AnalyticsEvent(listOf(*params))

    enum class E_FEE_OPTION_TYPE {
        FAST,
        MEDIUM,
        SLOW,
        CUSTOM
    }

    class E_NEW_OP_SUBMITTED(vararg extras: Pair<String, Any>): AnalyticsEvent(listOf(*extras))

    class E_NEW_OP_ERROR: AnalyticsEvent()

    class E_SCAN_QR_ASK_CAMERA_PERMISSION: AnalyticsEvent()
    class E_SCAN_QR_CAMERA_PERMISSION_GRANTED: AnalyticsEvent()
    class E_RECOVERY_CODE_SET_UP: AnalyticsEvent()
    class E_LOG_OUT: AnalyticsEvent()
    class E_WALLET_CREATED: AnalyticsEvent()
    class E_WALLET_DELETED: AnalyticsEvent()
    class E_SIGN_IN_ABORTED: AnalyticsEvent()

    class E_SIGN_IN_SUCCESSFUL(val type: LoginType): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase()
    ))

    enum class LoginType {
        PASSWORD,
        EMAIL_AND_RECOVERY_CODE,
        RECOVERY_CODE,
        RECOVERY_CODE_AND_EMAIL
    }

    class E_P2P_SETUP_SUCCESSFUL: AnalyticsEvent()
    class E_PASSWORD_CHANGED: AnalyticsEvent()
    class E_PROFILE_CHANGED: AnalyticsEvent()
    class E_EMERGENCY_KIT_EXPORTED(option: String): AnalyticsEvent(listOf("share_option" to option))
    class E_EMERGENCY_KIT_ABORTED: AnalyticsEvent()

    class E_OPEN_WEB(val name: String, val url: String): AnalyticsEvent(listOf(
        "name" to name,
        "url" to url
    ))

    class E_DID_SELECT_BITCOIN_UNIT(val type: CurrencyDisplayMode): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase()
    ))

    class E_DID_SELECT_NIGHT_MODE(val type: NightMode): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase()
    ))

    class E_EMAIL_SETUP_SUCCESSFUL: AnalyticsEvent()
    class E_EMAIL_SETUP_ABORTED: AnalyticsEvent()
    class E_EMAIL_SETUP_SKIPPED: AnalyticsEvent()

    enum class PIN_TYPE {
        CREATED,
        CORRECT,
        INCORRECT,
        DID_NOT_MATCH // Only during pin setup
    }

    class E_PIN(type: PIN_TYPE): AnalyticsEvent( listOf("type" to type.name.toLowerCase()))

    class E_APP_WILL_GO_TO_BACKGROUND: AnalyticsEvent()
    class E_APP_WILL_ENTER_FOREGROUND: AnalyticsEvent()
    class E_APP_WILL_TERMINATE: AnalyticsEvent()

    enum class ADDRESS_ORIGIN {
        QR,
        COPY_BUTTON
    }

    class E_ADDRESS_COPIED(origin: ADDRESS_ORIGIN): AnalyticsEvent(listOf(
        "origin" to origin.name.toLowerCase()
    ))

    class E_ADDRESS_SHARE_TOUCHED: AnalyticsEvent()
    class E_MENU_TAP: AnalyticsEvent()
    class E_BALANCE_TAP: AnalyticsEvent()

    enum class PASSWORD_ERROR {
        INCORRECT,
        DID_NOT_MATCH // Only during password setup
    }

    class E_PASSWORD(error: PASSWORD_ERROR): AnalyticsEvent(listOf("error" to error.name.toLowerCase()))

    enum class RC_ERROR {
        DID_NOT_MATCH
    }

    class E_RECOVERY_CODE(error: RC_ERROR): AnalyticsEvent(listOf(
            "error" to error.name.toLowerCase()
    ))

    enum class EMAIL_TYPE {
        ALREADY_USED
    }

    class E_EMAIL(type: EMAIL_TYPE): AnalyticsEvent(listOf(
            "type" to type.name.toLowerCase()
    ))

    enum class E_EK_SAVE_OPTION {
        DRIVE,
        EMAIL,
        MANUAL,
        ICLOUD; // not gonna happen in Apollo, just documenting
    }

    class E_EK_SAVE_SELECT(selection: E_EK_SAVE_OPTION):
        AnalyticsEvent(listOf("option" to selection.name.toLowerCase()))

    enum class E_DRIVE_TYPE {
        SIGN_IN_START,
        SIGN_IN_ERROR,
        SIGN_IN_FINISH,
        UPLOAD_START,
        UPLOAD_ERROR,
        UPLOAD_FINISH
    }

    class E_EK_DRIVE(type: E_DRIVE_TYPE, error: String = ""):
        AnalyticsEvent(listOf("type" to type.name.toLowerCase(), "error" to error))

    class E_EK_SHARE(app: String):
        AnalyticsEvent(listOf("app" to app))

    class E_EK_EMAIL(app: String):
        AnalyticsEvent(listOf("app" to app))

    enum class ERROR_TYPE {
        INVALID_QR, // A little bit duplicated with NEW_OP_ERROR(INVALID_ADDRESS)
        EMERGENCY_KIT_CHALLENGE_KEY_MIGRATION_ERROR,
        LNURL_INVALID_CODE,
        LNURL_INVALID_TAG, // E.g Not a withdraw request
        LNURL_UNRESPONSIVE,
        LNURL_UNKNOWN_ERROR,
        LNURL_EXPIRED_INVOICE
    }

    class E_ERROR(val type: ERROR_TYPE, vararg extras: Any): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase(),
        *extras.mapIndexed { index: Int, extra: Any -> Pair("extra$index", extra) }.toTypedArray()
    ))

    enum class LNURL_WITHDRAW_STATE_TYPE {
        CONTACTING,
        INVOICE_CREATED,
        RECEIVING,
        TAKING_TOO_LONG,
        FAILED,
        SUCCESS
    }

    class E_LNURL_WITHDRAW_STATE(val type: LNURL_WITHDRAW_STATE_TYPE): AnalyticsEvent(listOf(
        "type" to type.name.toLowerCase()
    ))
}