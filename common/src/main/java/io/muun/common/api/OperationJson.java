package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    @NotNull
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

    @NotNull
    public BitcoinAmount amount;

    @NotNull
    public BitcoinAmount fee;

    @Null
    @Nullable
    public Long confirmations;

    @NotNull
    public Long exchangeRatesWindowId;

    @NotNull
    @Nullable // to null or not to null, that's the question
    public String description;

    @NotNull
    public OperationStatus status;

    @Null
    @Nullable
    public Transaction transaction;

    @NotNull
    public MuunZonedDateTime creationDate;

    /**
     * Json constructor.
     */
    public OperationJson() {
    }

    /**
     * Apollo constructor.
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
                         BitcoinAmount amount,
                         BitcoinAmount fee,
                         Long exchangeRatesWindowId,
                         @Nullable String description,
                         OperationStatus status,
                         MuunZonedDateTime creationDate) {

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
        this.exchangeRatesWindowId = exchangeRatesWindowId;
        this.description = description;
        this.status = status;
        this.creationDate = creationDate;
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
                         BitcoinAmount amount,
                         BitcoinAmount fee,
                         Long confirmations,
                         Long exchangeRatesWindowId,
                         @Nullable String description,
                         OperationStatus status,
                         Transaction transaction,
                         MuunZonedDateTime creationDate) {

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
        this.amount = amount;
        this.fee = fee;
        this.confirmations = confirmations;
        this.exchangeRatesWindowId = exchangeRatesWindowId;
        this.description = description;
        this.status = status;
        this.transaction = transaction;
        this.creationDate = creationDate;
    }
}
