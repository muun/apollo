package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.HoustonModel;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import android.support.annotation.Nullable;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;

import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

public class Operation extends HoustonModel {

    @Nullable
    public OperationDirection direction;

    @NotNull
    public final Boolean isExternal;

    @Nullable
    public final PublicProfile senderProfile;

    @NotNull
    public final Boolean senderIsExternal;

    @Nullable
    public PublicProfile receiverProfile;

    @NotNull
    public Boolean receiverIsExternal;

    @Nullable
    public String receiverAddress;

    @Nullable
    public String receiverAddressDerivationPath;

    @NotNull
    public final BitcoinAmount amount;

    @NotNull
    public final BitcoinAmount fee;

    @NotNull
    public Long confirmations;

    @Nullable
    public String hash;

    @Nullable
    public final String description;

    @NotNull
    public OperationStatus status;

    @NotNull
    public final ZonedDateTime creationDate;

    @NotNull
    public final Long exchangeRateWindowHid;

    /**
     * Constructor.
     */
    public Operation(
            @Nullable Long id,
            @NotNull Long hid,
            @NotNull OperationDirection direction,
            @NotNull Boolean isExternal,
            @Nullable PublicProfile senderProfile,
            @NotNull Boolean senderIsExternal,
            @Nullable PublicProfile receiverProfile,
            @NotNull Boolean receiverIsExternal,
            @Nullable String receiverAddress,
            @Nullable String receiverAddressDerivationPath,
            @NotNull BitcoinAmount amount,
            @NotNull BitcoinAmount fee,
            @NotNull Long confirmations,
            @Nullable String hash,
            @Nullable String description,
            @NotNull OperationStatus status,
            @NotNull ZonedDateTime creationDate,
            @NotNull Long exchangeRateWindowHid) {

        super(id, hid);
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
        this.hash = hash;
        this.description = description;
        this.status = status;
        this.creationDate = creationDate;
        this.exchangeRateWindowHid = exchangeRateWindowHid;
    }

    /**
     * Get the total amount sent or received in this operation to/from a given user.
     */
    public long getBalanceInSatoshisForUser() {
        switch (direction) {
            case INCOMING:
                return +(amount.inSatoshis);

            case OUTGOING:
                return -(amount.inSatoshis + fee.inSatoshis);

            case CYCLICAL:
                return -(fee.inSatoshis);

            default:
                throw new MissingCaseError(direction);
        }
    }

    /**
     * Whether this operation was sent and received by the same User.
     */
    public boolean isCyclical() {
        return direction == OperationDirection.CYCLICAL;
    }

    public boolean isFailed() {

        return status.equals(OperationStatus.DROPPED) || status.equals(OperationStatus.FAILED);
    }

    /**
     * Whether this operation was created today.
     */
    public boolean isFromToday() {

        final ZonedDateTime now = getLocalizedNow();
        final ZonedDateTime creationDate = getLocalizedCreationDate();

        return creationDate.getYear() == now.getYear()
                && creationDate.getDayOfYear() == now.getDayOfYear();
    }

    /**
     * Whether this operation was created this year.
     */
    public boolean isFromThisYear() {

        return getLocalizedCreationDate().getYear() == getLocalizedNow().getYear();
    }

    /**
     * Get the creation date of the operation, localized to the system timezone.
     */
    public ZonedDateTime getLocalizedCreationDate() {

        return creationDate.withZoneSameInstant(ZoneId.systemDefault());
    }

    private ZonedDateTime getLocalizedNow() {

        return ZonedDateTime.now().withZoneSameInstant(ZoneId.systemDefault());
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public MonetaryAmount getTotal() {
        return this.amount.inInputCurrency.add(this.fee.inInputCurrency);
    }
}
