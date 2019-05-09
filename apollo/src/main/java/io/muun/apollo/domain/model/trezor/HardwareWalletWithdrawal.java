package io.muun.apollo.domain.model.trezor;

import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.common.crypto.hd.HardwareWalletAddress;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.crypto.hwallet.HardwareWalletState;
import io.muun.common.utils.BitcoinUtils;

import org.bitcoinj.core.NetworkParameters;

import java.util.ArrayList;
import java.util.List;

public class HardwareWalletWithdrawal {

    private List<HardwareWalletOutput> inputs;

    private long paymentAmount;

    private long changeAmount;

    private long inputAmount;

    private HardwareWalletAddress changeAddress;

    private String paymentAddress;

    /**
     * Transaction draft.
     */
    public HardwareWalletWithdrawal() {
        inputs = new ArrayList<>();
        changeAmount = 0;
        inputAmount = 0;
    }

    public void addInput(HardwareWalletOutput output) {
        inputs.add(output);
        inputAmount += output.getAmount();
    }

    public long getInputAmount() {
        return inputAmount;
    }

    /**
     * Get fee.
     */
    public long getFee() {
        return inputAmount - paymentAmount - changeAmount;
    }

    public void setPaymentAmount(long paymentAmount) {
        this.paymentAmount = paymentAmount;
    }

    public void setChange(HardwareWalletAddress changeAddress, long changeAmount) {
        this.changeAddress = changeAddress;
        this.changeAmount = changeAmount;
    }

    public String getPaymentAddress() {
        return paymentAddress;
    }

    public void setPaymentAddress(String paymentAddress) {
        this.paymentAddress = paymentAddress;
    }

    public List<HardwareWalletOutput> getInputs() {
        return inputs;
    }

    public long getPaymentAmount() {
        return paymentAmount;
    }

    public long getChangeAmount() {
        return changeAmount;
    }

    public HardwareWalletAddress getChangeAddress() {
        return changeAddress;
    }

    public boolean hasChange() {
        return changeAddress != null;
    }

    /**
     * Get network params.
     */
    public NetworkParameters getNetworkParameters() {
        if (inputs.isEmpty()) {
            throw new RuntimeException("At least one inout is needed to obtain parameters");
        }

        return inputs.get(0).getPublicKey().getNetworkParameters();
    }

    /**
     * Build draft.
     */
    public static HardwareWalletWithdrawal buildDraft(HardwareWalletState state,
                                                      long amountInSatoshis,
                                                      long feeInSatoshis,
                                                      String paymentAddress) {

        final HardwareWalletWithdrawal draft = new HardwareWalletWithdrawal();
        draft.setPaymentAmount(amountInSatoshis);
        draft.setPaymentAddress(paymentAddress);

        final long target = amountInSatoshis + feeInSatoshis;

        int outputIndex = 0;
        while (draft.getInputAmount() < target) {
            final HardwareWalletOutput output = state.getSortedOutputs().get(outputIndex);

            draft.addInput(output);
            outputIndex += 1;
        }

        if (draft.getInputAmount() < target) {
            throw new InsufficientFundsError();

        } else if (draft.getInputAmount() - target > BitcoinUtils.DUST_IN_SATOSHIS) {
            draft.setChange(state.getChangeAddress(), draft.getInputAmount() - target);
        }

        return draft;
    }
}
