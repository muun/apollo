package io.muun.common.crypto.hwallet;

import io.muun.common.crypto.hd.HardwareWalletAddress;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.model.SizeForAmount;

import java.util.List;

public class HardwareWalletState {

    protected List<HardwareWalletOutput> sortedOutputs;
    protected List<SizeForAmount> sizeForAmounts;
    protected HardwareWalletAddress changeAddress;
    protected HardwareWalletAddress nextAddress;

    /**
     * Constructor.
     */
    public HardwareWalletState(List<HardwareWalletOutput> sortedOutputs,
                               List<SizeForAmount> sizeForAmounts,
                               HardwareWalletAddress changeAddress,
                               HardwareWalletAddress nextAddress) {
        this.sortedOutputs = sortedOutputs;
        this.sizeForAmounts = sizeForAmounts;
        this.changeAddress = changeAddress;
        this.nextAddress = nextAddress;
    }

    public List<HardwareWalletOutput> getSortedOutputs() {
        return sortedOutputs;
    }

    public List<SizeForAmount> getSizeForAmounts() {
        return sizeForAmounts;
    }

    public HardwareWalletAddress getChangeAddress() {
        return changeAddress;
    }

    public HardwareWalletAddress getNextAddress() {
        return nextAddress;
    }

    /**
     * Get the total wallet balance, in satoshis.
     */
    public long getBalanceInSatoshis() {
        if (sizeForAmounts.size() == 0) {
            return 0;
        } else {
            return sizeForAmounts.get(sizeForAmounts.size() - 1).amountInSatoshis;
        }
    }
}
