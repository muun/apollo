package io.muun.common.api.error;

import io.muun.common.net.HttpStatus;
import io.muun.common.utils.Deprecated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

import static io.muun.common.utils.Preconditions.checkState;

public enum ErrorCode {

    // http errors (do not use these, they are just a fallback)
    HTTP_NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE, StatusCode.CLIENT_FAILURE, "Not acceptable"
    ),
    HTTP_TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS, StatusCode.CLIENT_FAILURE, "Too many requests"
    ),
    HTTP_FORBIDDEN(
            HttpStatus.FORBIDDEN, StatusCode.CLIENT_FAILURE, "Forbidden"
    ),
    HTTP_UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED, StatusCode.CLIENT_FAILURE, "Unauthorized"
    ),
    HTTP_BAD_REQUEST(
            HttpStatus.BAD_REQUEST, StatusCode.CLIENT_FAILURE, "Bad request"
    ),
    HTTP_METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED, StatusCode.CLIENT_FAILURE, "Method not allowed"
    ),
    HTTP_NOT_FOUND(
            HttpStatus.NOT_FOUND, StatusCode.CLIENT_FAILURE, "Not found"
    ),
    HTTP_UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, StatusCode.CLIENT_FAILURE, "Unsupported media type"
    ),
    HTTP_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, StatusCode.CLIENT_FAILURE, "Service unavailable"
    ),
    HTTP_INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, StatusCode.SERVER_FAILURE, "Internal server error"
    ),

    // application protocol errors
    INVALID_CLIENT_VERSION(
            4001, StatusCode.CLIENT_FAILURE, "X-Client-Version header should hold a version number"
    ),
    DEPRECATED_CLIENT_VERSION(
            4002, StatusCode.CLIENT_FAILURE, "Deprecated client version"
    ),
    INVALID_IDEMPOTENCY_KEY(
            4003, StatusCode.CLIENT_FAILURE, "X-Idempotency-Key header is the same but body changed"
    ),
    INVALID_CLIENT_TYPE(
            4004, StatusCode.CLIENT_FAILURE, "X-Client-Type header should hold a valid client type"
    ),
    PREVIOUS_IN_FLIGHT_CACHE_EXPIRED(
            4005, StatusCode.SERVER_FAILURE, "The previous retry with the same idempotency key hung"
    ),

    // validation errors
    JSON_CONSTRAINT_VIOLATION(
            1001, StatusCode.CLIENT_FAILURE, "JSON constraint violation"
    ),
    JSON_PROCESSING_EXCEPTION(
            1004, StatusCode.CLIENT_FAILURE, "Unable to process JSON"
    ),
    INVALID_PHONE_NUMBER(
            2000, StatusCode.CLIENT_FAILURE, "Invalid phone number"
    ),
    INVALID_VERIFICATION_CODE(
            2002, StatusCode.CLIENT_FAILURE, "Invalid phone verification code"
    ),
    INVALID_HD_PUBLIC_KEY(
            2005, StatusCode.CLIENT_FAILURE, "Invalid base 58 extended public key"
    ),
    INVALID_BUILD_TYPE(
            2006, StatusCode.CLIENT_FAILURE, "Invalid build type"
    ),
    INVALID_GCM_TOKEN(
            2007, StatusCode.CLIENT_FAILURE, "Invalid GCM registration token"
    ),
    INVALID_EMAIL(
            2052, StatusCode.CLIENT_FAILURE, "Invalid email address"
    ),
    ONLY_PAYMENTS_ALLOWED(
            2008, StatusCode.CLIENT_FAILURE, "Only payments allowed"
    ),
    AMOUNT_SMALLER_THAN_DUST(
            2009, StatusCode.CLIENT_FAILURE, "Amount is smaller than dust"
    ),
    INVALID_RECEIVER(
            2010, StatusCode.CLIENT_FAILURE, "Invalid receiver"
    ),
    INVALID_OPERATION(
            2012, StatusCode.CLIENT_FAILURE, "Invalid operation"
    ),
    WITHDRAWAL_FAILED(
            2068, StatusCode.CLIENT_FAILURE, "Withdrawal failed due to a transaction error"
    ),
    @Deprecated
    INVALID_SESSION(
            2013, StatusCode.CLIENT_FAILURE, "Invalid session"
    ),
    NO_USER_ASSOCIATED_TO_SESSION(
            2014, StatusCode.CLIENT_FAILURE, "No user associated with current session"
    ),
    TOO_MANY_WRONG_VERIFICATION_CODES(
            2015,
            StatusCode.CLIENT_FAILURE,
            "Too many failed attempts for current verification code"
    ),
    NOT_AUTHORIZED(
            2016, StatusCode.CLIENT_FAILURE, "Not authorized"
    ),
    ALREADY_SIGNED_IN(
            2017, StatusCode.CLIENT_FAILURE, "User already associated to this session"
    ),
    ILLEGAL_COMPAT_LOGIN(
            2060, StatusCode.CLIENT_FAILURE, "Compatibility login not allowed"
    ),
    EMAIL_ALREADY_USED(
            2018, StatusCode.CLIENT_FAILURE, "Email already used"
    ),
    EMAIL_ALREADY_SET_UP(
            2061, StatusCode.CLIENT_FAILURE, "User already verified an email"
    ),
    INVALID_FEEDBACK_LENGTH(
            2050, StatusCode.CLIENT_FAILURE, "Invalid feedback content length"
    ),
    @Deprecated
    INVALID_CURRENCY(
            2019, StatusCode.CLIENT_FAILURE, "The currency code is invalid or not supported"
    ),
    INVALID_EXCHANGE_RATE_WINDOW(
            2020, StatusCode.CLIENT_FAILURE, "Invalid exchange rate window"
    ),
    EXCHANGE_RATE_WINDOW_TOO_OLD(
            2021, StatusCode.CLIENT_FAILURE, "Exchange rate window too old"
    ),
    INVALID_ADDRESS(
            2022, StatusCode.CLIENT_FAILURE, "Invalid address"
    ),
    INVALID_TRANSACTION(
            2023, StatusCode.CLIENT_FAILURE, "Invalid transaction"
    ),
    PHONE_NUMBER_ALREADY_USED(
            2024, StatusCode.CLIENT_FAILURE, "Phone number already used"
    ),
    INVALID_SIGNUP_DATA(
            2025, StatusCode.CLIENT_FAILURE, "Invalid signup data"
    ),
    INVALID_EXTERNAL_ADDRESS_RECORD(
            2026, StatusCode.CLIENT_FAILURE, "Invalid max used index"
    ),
    INVALID_CONTACT_KEYS(
            2027, StatusCode.CLIENT_FAILURE, "Invalid contact keys"
    ),
    INVALID_CONTACT_ID(
            2042, StatusCode.CLIENT_FAILURE, "Invalid contact id"
    ),
    INVALID_ID(
            2040, StatusCode.CLIENT_FAILURE, "Invalid id"
    ),
    INVALID_PASSWORD(
            2041, StatusCode.CLIENT_FAILURE, "Invalid password"
    ),
    CHALLENGE_NOT_FOUND(
            2043, StatusCode.CLIENT_FAILURE, "Requested challenge not found"
    ),
    INVALID_CHALLENGE_TYPE(
            2044, StatusCode.CLIENT_FAILURE, "Invalid challenge type"
    ),
    INVALID_CHALLENGE_SIGNATURE(
            2045, StatusCode.CLIENT_FAILURE, "Invalid challenge signature"
    ),
    INVALID_CHALLENGE_SETUP(
            2046, StatusCode.CLIENT_FAILURE, "Invalid challenge setup"
    ),
    CHALLENGE_UPDATE_FORBIDDEN(
            2047, StatusCode.CLIENT_FAILURE, "Challenge already set up"
    ),
    REVOKED_VERIFICATION_CODE(
            2048,
            StatusCode.CLIENT_FAILURE,
            "This verification code was revoked. Please use the latest one received"
    ),
    STALE_CHALLENGE_KEY(
            2063,
            StatusCode.CLIENT_FAILURE,
            "This recovery code is stale. You should have and use your latest one."
    ),
    CREDENTIALS_DONT_MATCH(
            2064,
            StatusCode.CLIENT_FAILURE,
            "This recovery code doesn't belong to this account."
    ),

    PUBLIC_PROFILE_NOT_SET_UP(
            2051, StatusCode.CLIENT_FAILURE, "Public profile is not set up"
    ),
    RECOVERY_CODE_NOT_SET_UP(
            2059, StatusCode.CLIENT_FAILURE, "Recovery code is not set up"
    ),
    P2P_NOT_ENABLED(
            2055, StatusCode.CLIENT_FAILURE, "Peer to Peer payments not enabled"
    ),
    FIRST_NAME_EMPTY(
            2056, StatusCode.CLIENT_FAILURE, "First Name cannot be empty"
    ),
    LAST_NAME_EMPTY(
            2057, StatusCode.CLIENT_FAILURE, "Last Name cannot be empty"
    ),
    @Deprecated
    INVALID_INVOICE_AMOUNT(
            2058, StatusCode.CLIENT_FAILURE, "Invoice amount is invalid"
    ),
    RECOVERY_CODE_V2_NOT_SET_UP(
            2062, StatusCode.CLIENT_FAILURE, "Recovery code v2 is not set up"
    ),
    INCOMING_SWAP_ALREADY_FULFILLED(
            2074, StatusCode.CLIENT_FAILURE, "The incoming swap is already fulfilled"
    ),
    WALLET_NOT_EMPTY(
            2086, StatusCode.CLIENT_FAILURE, "Cannot delete wallet with funds"
    ),
    UNSETTLED_OPERATIONS(
            2087,
            StatusCode.CLIENT_FAILURE,
            "Cannot delete wallet with unsettled operations"
    ),

    // error responses
    @Deprecated
    KEY_SET_NOT_FOUND(
            2004, StatusCode.CLIENT_FAILURE, "Key set not found"
    ),
    INSUFFICIENT_CLIENT_FUNDS(
            2028, StatusCode.CLIENT_FAILURE, "Not enough funds"
    ),
    ESTIMATED_FEE_NOT_FOUND(
            2030, StatusCode.SERVER_FAILURE, "Didn't found any fee estimation"
    ),
    EXCHANGE_RATE_WINDOW_NOT_FOUND(
            2031, StatusCode.SERVER_FAILURE, "Didn't found any exchange rate window"
    ),
    ILLEGAL_PUBLIC_KEY_UPDATE(
            2049, StatusCode.CLIENT_FAILURE, "You cannot update your PublicKey this way"
    ),
    ILLEGAL_PHONE_NUMBER_UPDATE(
            2053, StatusCode.CLIENT_FAILURE, "You cannot update your Phone Number this way"
    ),
    ILLEGAL_PROFILE_UPDATE(
            2054, StatusCode.CLIENT_FAILURE, "You cannot update your Profile this way"
    ),
    @Deprecated
    INSUFFICIENT_SERVER_FUNDS(
            2032, StatusCode.SERVER_FAILURE, "Not enough funds in the server to pay for the fee"
    ),
    @Deprecated
    FUNDING_USER_NOT_FOUND(
            2033, StatusCode.SERVER_FAILURE, "Couldn't find any fee funding users"
    ),
    EXPIRED_UTXOS(
            2034, StatusCode.CLIENT_FAILURE, "Some UTXOs in this transaction have expired"
    ),
    EXPIRED_VERIFICATION_CODE(
            2035, StatusCode.CLIENT_FAILURE, "The verification code has expired"
    ),
    ILLEGAL_SESSION_STATE(
            2036, StatusCode.CLIENT_FAILURE, "The session is invalid or is in an invalid state"
    ),
    ILLEGAL_RETRY(
            2037,
            StatusCode.CLIENT_FAILURE,
            "Invalid retry attempt, either because it wasn't allowed or the request wasn't the same"
    ),
    EXPIRED_SESSION(
            2038, StatusCode.CLIENT_FAILURE, "The session has expired"
    ),
    COUNTRY_NOT_SUPPORTED(
            2039, StatusCode.CLIENT_FAILURE, "Muun isn't yet available for this country"
    ),
    EXPIRED_SATELLITE_SESSION(
            2073, StatusCode.CLIENT_FAILURE, "Satellite session has expired"
    ),
    TRANSACTION_NOT_RBF(
            2080, StatusCode.CLIENT_FAILURE, "Transaction is not RBF"
    ),
    INVALID_REPLACEMENT(
            2075, StatusCode.CLIENT_FAILURE, "Transaction doesn't follow the replacing rules"
    ),
    CONFIRMED_TRANSACTION(
            2076, StatusCode.CLIENT_FAILURE, "Transaction is already confirmed"
    ),
    DROPPED_TRANSACTION(
            2077, StatusCode.CLIENT_FAILURE, "Dropped transaction"
    ),
    FAILED_TRANSACTION(
            2078, StatusCode.CLIENT_FAILURE, "Failed transaction"
    ),
    INVALID_STATUS_FOR_REPLACEMENT(
            2079, StatusCode.CLIENT_FAILURE, "Invalid status for replacement"
    ),
    MIN_FEE_RATE_INCREMENT_NOT_FOUND(
            2081, StatusCode.SERVER_FAILURE, "Didn't found any min fee rate increment"
    ),
    NO_CLIENT_FOUND(
            2082, StatusCode.CLIENT_FAILURE, "No client found for owner"
    ),
    SWAP_FAILED(
            2084, StatusCode.CLIENT_FAILURE, "Swap failed"
    ),

    // email errors
    EMAIL_LINK_INVALID(
            5001, StatusCode.CLIENT_FAILURE, "Invalid link"
    ),
    EMAIL_LINK_EXPIRED(
            5002, StatusCode.CLIENT_FAILURE, "Link expired"
    ),
    EMAIL_NOT_REGISTERED(
            5003, StatusCode.CLIENT_FAILURE, "Email not registered"
    ),

    // swapper errors
    INVALID_INVOICE(
            8100, StatusCode.CLIENT_FAILURE, "Couldn't parse the lightning invoice"
    ),
    INVOICE_EXPIRED(
            8112, StatusCode.CLIENT_FAILURE, "Invoice provided is already expired"
    ),
    INVOICE_EXPIRES_TOO_SOON(
            8101, StatusCode.CLIENT_FAILURE, "Invoice provided will expire too soon"
    ),
    INVOICE_ALREADY_USED(
            8102, StatusCode.CLIENT_FAILURE, "Invoice already used in another swap"
    ),
    LIGHTNING_NODE_ERROR(
            8103, StatusCode.SERVER_FAILURE, "There was a problem with the lighting node"
    ),
    SWAP_NOT_FOUND(
            8104, StatusCode.CLIENT_FAILURE, "There wasn't any swap with that matches your query"
    ),
    NO_PAYMENT_ROUTE(
            8105,
            StatusCode.CLIENT_FAILURE,
            "There isn't any route to target destination with enough capacity"
    ),
    FUNDING_TRANSACTION_NOT_FOUND(
            8106, StatusCode.CLIENT_FAILURE, "The funding transaction wasn't found in the chain"
    ),
    INVALID_FUNDING_TRANSACTION(
            8107, StatusCode.CLIENT_FAILURE, "The funding transaction is invalid"
    ),
    INVALID_SWAP_EXPIRATION(
            8108, StatusCode.CLIENT_FAILURE, "The proposed swap expiration is too low"
    ),
    SWAP_NOT_FAILED(
            8109, StatusCode.CLIENT_FAILURE, "Won't sign a refund for a swap that hasn't failed"
    ),
    INVALID_REFUND_TRANSACTION(
            8110, StatusCode.CLIENT_FAILURE, "The refund transaction is invalid"
    ),
    SCRIPT_VERSION_TOO_OLD(
            8111,
            StatusCode.CLIENT_FAILURE,
            "The funding output script version is too old for the requested operation"
    ),
    INVALID_DEBT_TYPE(
            8113, StatusCode.CLIENT_FAILURE, "The debt type is invalid"
    ),
    SWAP_ALREADY_EXECUTED(
            8114, StatusCode.CLIENT_FAILURE, "Swap has already been executed"
    ),
    INVALID_UTXO_BALANCE(
            8115, StatusCode.CLIENT_FAILURE, "The expected utxo balance is invalid"
    ),
    INVALID_DEFAULT_UPDATE(
            8116, StatusCode.CLIENT_FAILURE, "The default is invalid"
    ),
    INVALID_DEBT_STATE_OWNER(
            8117, StatusCode.CLIENT_FAILURE, "The debt owner is invalid"
    ),
    CONFLICTING_INVOICE(
            8118,
            StatusCode.CLIENT_FAILURE,
            "The invoice is already registered with conflicting information"
    ),
    UNREACHABLE_NODE(
            8119,
            StatusCode.CLIENT_FAILURE,
            "Invoice generated by private node without RoutHints so it can't be reached"
    ),
    INCOMING_SWAP_NOT_FOUND(
            8120,
            StatusCode.CLIENT_FAILURE,
            "There wasn't any incoming swap that matches your query"
    ),
    INVALID_AMOUNT(
            8121, StatusCode.CLIENT_FAILURE, "Invalid payment amount or funding output amount"
    ),
    INVALID_DEBT_AMOUNT(
            8122, StatusCode.CLIENT_FAILURE, "The debt amount is no longer valid"
    ),
    CYCLICAL_SWAP(
            8123, StatusCode.CLIENT_FAILURE, "Cyclical swaps are not allowed"
    ),
    AMOUNTLESS_INVOICES_NOT_SUPPORTED(
            8124, StatusCode.CLIENT_FAILURE, "Amount less invoices not supported"
    ),
    INVALID_DERIVATION_PATH(
            2125, StatusCode.CLIENT_FAILURE, "Derivation path is invalid"
    );

    private static Map<Integer, ErrorCode> errorCodeMap = new HashMap<>();

    static {
        for (ErrorCode errorCode : values()) {

            checkState(
                    !errorCodeMap.containsKey(errorCode.code),
                    "Codes must be unique (" + errorCode.getCode() + ")"
            );

            errorCodeMap.put(errorCode.code, errorCode);
        }
    }

    private final int code;

    private final StatusCode status;

    private final String description;

    ErrorCode(int code, StatusCode status, String description) {

        this.code = code;
        this.status = status;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public StatusCode getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static ErrorCode fromValue(int value) {

        if (!errorCodeMap.containsKey(value)) {
            throw new IllegalArgumentException("Invalid error code: " + value);
        }

        return errorCodeMap.get(value);
    }

    @JsonValue
    public int toValue() {
        return this.code;
    }
}
