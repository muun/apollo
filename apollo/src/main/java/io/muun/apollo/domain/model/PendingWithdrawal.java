package io.muun.apollo.domain.model;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.threeten.bp.ZonedDateTime;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingWithdrawal {

    @NotNull
    public String uuid;

    @NotNull
    public Long hardwareWalletHid;

    @NotNull
    public String receiverAddress;

    @NotNull
    public String receiverAddressPath;

    @NotNull
    public BitcoinAmount amount;

    @NotNull
    public BitcoinAmount fee;

    @NotNull
    public long exchangeRateWindowHid;

    @NotNull
    public String description;

    @NotNull
    public ZonedDateTime createdAt;

    /**
     * Only available after Satellite signs.
     */
    @Nullable
    public String signedSerializedTransaction;
    
    public boolean isSigned() {
        return signedSerializedTransaction != null;
    }

    /**
     * Manual constructor.
     */
    public PendingWithdrawal(String uuid,
                             Long hardwareWalletHid,
                             String receiverAddress,
                             String receiverAddressPath,
                             BitcoinAmount amount,
                             BitcoinAmount fee,
                             long exchangeRateWindowHid,
                             String description,
                             ZonedDateTime createdAt,
                             @Nullable String signedSerializedTransaction) {
        this.uuid = uuid;
        this.hardwareWalletHid = hardwareWalletHid;
        this.receiverAddress = receiverAddress;
        this.receiverAddressPath = receiverAddressPath;
        this.amount = amount;
        this.fee = fee;
        this.exchangeRateWindowHid = exchangeRateWindowHid;
        this.description = description;
        this.createdAt = createdAt;
        this.signedSerializedTransaction = signedSerializedTransaction;
    }

    /**
     * JSON Constructor.
     */
    public PendingWithdrawal() {
    }
}
