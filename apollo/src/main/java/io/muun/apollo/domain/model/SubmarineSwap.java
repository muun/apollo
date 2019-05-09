package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.HoustonUuidModel;

import org.threeten.bp.ZonedDateTime;

import javax.annotation.Nullable;

public class SubmarineSwap extends HoustonUuidModel {

    public final String invoice;
    public final SubmarineSwapReceiver receiver;
    public final SubmarineSwapFundingOutput fundingOutput;

    public final Long sweepFeeInSatoshis;
    public final Long lightningFeeInSatoshis;

    public final ZonedDateTime expiresAt;

    @Nullable // may not be payed yet
    public ZonedDateTime payedAt;

    @Nullable
    public String preimageInHex;

    /**
     * Constructor.
     */
    public SubmarineSwap(@Nullable Long id,
                         String houstonUuid,
                         String invoice,
                         SubmarineSwapReceiver receiver,
                         SubmarineSwapFundingOutput fundingOutput,
                         Long sweepFeeInSatoshis,
                         Long lightningFeeInSatoshis,
                         ZonedDateTime expiresAt,
                         @Nullable ZonedDateTime payedAt,
                         @Nullable String preimageInHex) {

        super(id, houstonUuid);

        this.invoice = invoice;
        this.receiver = receiver;
        this.fundingOutput = fundingOutput;
        this.sweepFeeInSatoshis = sweepFeeInSatoshis;
        this.lightningFeeInSatoshis = lightningFeeInSatoshis;
        this.expiresAt = expiresAt;
        this.payedAt = payedAt;
        this.preimageInHex = preimageInHex;
    }
}
