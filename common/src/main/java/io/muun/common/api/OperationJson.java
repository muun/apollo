package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;
import io.muun.common.utils.Deprecated;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationJson {

    @Null
    @Nullable
    public Long id;

    @NotNull
    public String requestId;

    @Nullable // Apollo sends null
    public OperationDirection direction;

    @NotNull
    public Boolean isExternal;

    @Nullable
    public PublicProfileJson senderProfile;

    @NotNull
    public Boolean senderIsExternal;

    @Nullable
    public PublicProfileJson receiverProfile;

    @NotNull
    public Boolean receiverIsExternal;

    @NotNull
    @Nullable
    public String receiverAddress;

    @Nullable
    public String receiverAddressDerivationPath;

    @Deprecated(atApolloVersion = 76)
    @Nullable
    public Long hardwareWalletHid;

    @NotNull
    public BitcoinAmountJson amount;

    @NotNull
    public BitcoinAmountJson fee;

    @Since(apolloVersion = 35)
    @Nullable // For retro-compat, for new clients should be NOTNULL
    public Long outputAmountInSatoshis;

    @Null
    @Nullable
    public Long confirmations;

    @NotNull
    public Long exchangeRatesWindowId;

    @Nullable // required for older clients, but not sent by newer
    @Deprecated
    public String description;

    @NotNull
    public OperationStatus status;

    @Null
    @Nullable
    public Transaction transaction;

    @NotNull
    public MuunZonedDateTime creationDate;

    @Nullable
    public String swapUuid;

    @Nullable
    public SubmarineSwapJson swap;

    @Since(apolloVersion = 63, falconVersion = 42)
    @Nullable // For retro compat, for new clients should be not null
    public String senderMetadata;
    
    @Since(apolloVersion = 63)
    @Nullable // This is set when using p2p only
    public String receiverMetadata;

    @Nullable // For old clients, and HW withdrawals.
    public List<String> outpoints;  // The complete utxoSet, sorted as used for fee computation

    @Nullable
    public Boolean markItAsReplaceableByFee;

    @Nullable
    public IncomingSwapJson incomingSwap;

    // TODO: This is not integrated into apollo or falcon yet.
    @Since(apolloVersion = Supports.Taproot.APOLLO, falconVersion = Supports.Taproot.FALCON)
    @Nullable
    // For old clients, never set.
    // Also, only valid for musig ops and lifetime spread from newOperation to tx broadcast.
    public List<String> userPublicNoncesHex;

    /**
     * Json constructor.
     */
    public OperationJson() {
    }

    /**
     * Apollo constructor.
     * TODO: used by Houston tests too...
     */
    public OperationJson(String requestId,
                         OperationDirection direction,
                         Boolean isExternal,
                         @Nullable PublicProfileJson senderProfile,
                         Boolean senderIsExternal,
                         @Nullable PublicProfileJson receiverProfile,
                         Boolean receiverIsExternal,
                         @Nullable String receiverAddress,
                         @Nullable String receiverAddressDerivationPath,
                         BitcoinAmountJson amount,
                         BitcoinAmountJson fee,
                         Long outputAmountInSatoshis,
                         Long exchangeRatesWindowId,
                         @Nullable String description,
                         OperationStatus status,
                         MuunZonedDateTime creationDate,
                         @Nullable String swapUuid,
                         @Nullable String senderMetadata,
                         @Nullable String receiverMetadata,
                         @Nullable List<String> outpoints,
                         @Nullable Boolean markItAsReplaceableByFee,
                         @Nullable List<String> userPublicNoncesHex) {

        this.requestId = requestId;
        this.direction = direction;
        this.isExternal = isExternal;
        this.senderProfile = senderProfile;
        this.senderIsExternal = senderIsExternal;
        this.receiverProfile = receiverProfile;
        this.receiverIsExternal = receiverIsExternal;
        this.receiverAddress = receiverAddress;
        this.receiverAddressDerivationPath = receiverAddressDerivationPath;
        this.amount = amount;
        this.fee = fee;
        this.outputAmountInSatoshis = outputAmountInSatoshis;
        this.exchangeRatesWindowId = exchangeRatesWindowId;
        this.description = description;
        this.status = status;
        this.creationDate = creationDate;
        this.swapUuid = swapUuid;
        this.senderMetadata = senderMetadata;
        this.receiverMetadata = receiverMetadata;
        this.outpoints = outpoints;
        this.markItAsReplaceableByFee = markItAsReplaceableByFee;
        // TODO: assign to property (leaving it for now as its already in other branch/commit)
    }

    /**
     * Houston constructor.
     */
    public OperationJson(Long id,
                         String requestId,
                         OperationDirection direction,
                         Boolean isExternal,
                         @Nullable PublicProfileJson senderProfile,
                         Boolean senderIsExternal,
                         @Nullable PublicProfileJson receiverProfile,
                         Boolean receiverIsExternal,
                         @Nullable String receiverAddress,
                         @Nullable String receiverAddressDerivationPath,
                         @Nullable Long hardwareWalletHid,
                         BitcoinAmountJson amount,
                         BitcoinAmountJson fee,
                         Long outputAmountInSatoshis,
                         Long confirmations,
                         Long exchangeRatesWindowId,
                         @Nullable String description,
                         OperationStatus status,
                         @Nullable Transaction transaction,
                         MuunZonedDateTime creationDate,
                         SubmarineSwapJson swap,
                         IncomingSwapJson incomingSwap,
                         @Nullable String senderMetadata,
                         @Nullable String receiverMetadata) {

        this.id = id;
        this.requestId = requestId;
        this.direction = direction;
        this.isExternal = isExternal;
        this.senderProfile = senderProfile;
        this.senderIsExternal = senderIsExternal;
        this.receiverProfile = receiverProfile;
        this.receiverIsExternal = receiverIsExternal;
        this.receiverAddress = receiverAddress;
        this.receiverAddressDerivationPath = receiverAddressDerivationPath;
        this.hardwareWalletHid = hardwareWalletHid;
        this.amount = amount;
        this.fee = fee;
        this.outputAmountInSatoshis = outputAmountInSatoshis;
        this.confirmations = confirmations;
        this.exchangeRatesWindowId = exchangeRatesWindowId;
        this.description = description;
        this.status = status;
        this.transaction = transaction;
        this.creationDate = creationDate;
        this.swapUuid = swap != null ? swap.swapUuid : null;
        this.swap = swap;
        this.incomingSwap = incomingSwap;
        this.senderMetadata = senderMetadata;
        this.receiverMetadata = receiverMetadata;
    }
}
