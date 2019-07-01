package io.muun.apollo.domain.model;

public class CustomFeeRate {

    public final int confirmationTarget;
    public final double satoshisPerByte;

    public CustomFeeRate(int confirmationTarget, double satoshisPerByte) {
        this.confirmationTarget = confirmationTarget;
        this.satoshisPerByte = satoshisPerByte;
    }
}
