package io.muun.apollo.domain.satellite.withdrawal;

import io.muun.apollo.domain.model.trezor.HardwareWalletWithdrawal;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.crypto.hd.PublicKey;

import androidx.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SatelliteWithdrawalJson {

    private static String TREZOR_PAY_TO_ADDRESS = "PAYTOADDRESS";
    private static String TREZOR_PAY_TO_WRAPPED_WITNESS = "PAYTOP2SHWITNESS";

    private static String TREZOR_SPEND_TO_WRAPPED_WITNESS = "SPENDP2SHWITNESS";
    private static String TREZOR_SPEND_TO_ADDRESS = "SPENDADDRESS";


    // NOTE: as of this moment, this only supports Trezor. To support ledger (presumably, the docs
    // are a mess), we would need:
    // - Serialized previous transaction of all inputs
    // - Serialized redeem script of all P2SH inputs
    // - Serialized outputs (unclear if this includes the change output)

    public static class InputJson {
        public String txId;
        public Integer index;
        public Long amount;

        public String derivationPath;
        public String scriptType;
    }

    public static class ExternalOutputJson {
        public String address;
        public Long amount;
    }


    public static class InternalOutputJson {
        public String derivationPath;
        public long amount;
        public String scriptType;
    }


    @NotNull
    public List<InputJson> inputs;

    @NotNull
    public ExternalOutputJson externalOutput;

    @Nullable
    public InternalOutputJson internalOutput;


    /**
     * Create a SatelliteWithdrawalJson pulling data from a HardwareWalletWithdrawal.
     */
    public static SatelliteWithdrawalJson fromData(HardwareWalletWithdrawal withdrawal) {
        final List<InputJson> inputs = new ArrayList<>(withdrawal.getInputs().size());

        // 1. Build input data from UTXOs:
        for (final HardwareWalletOutput utxo: withdrawal.getInputs()) {
            final InputJson input = new InputJson();

            input.txId = utxo.getTxId();
            input.index = utxo.getIndex();
            input.derivationPath = utxo.getPublicKey().getAbsoluteDerivationPath();
            input.amount = utxo.getAmount();
            input.scriptType = utxo.getPublicKey().isHardwareWalletWrappedP2Wpkh()
                    ? TREZOR_SPEND_TO_WRAPPED_WITNESS
                    : TREZOR_SPEND_TO_ADDRESS;
            inputs.add(input);
        }

        // 2. Build external (to Muun) output:
        final ExternalOutputJson externalOutput = new ExternalOutputJson();
        externalOutput.address = withdrawal.getPaymentAddress();
        externalOutput.amount = withdrawal.getPaymentAmount();

        // 3. Build internal (change) output, if any:
        final InternalOutputJson internalOutput;

        if (withdrawal.hasChange()) {
            internalOutput = new InternalOutputJson();
            internalOutput.derivationPath = withdrawal.getChangeAddress().getDerivationPath();
            internalOutput.amount = withdrawal.getChangeAmount();

            // We need a public key in order to check the network params for the weird trezor
            // notation (ypub, zpub, etc) and decide which output type we are going to use. Since
            // trezor doesn't mix different address types for the same master key, it's same to use
            // a pubkey from an input.
            final PublicKey publicKey = withdrawal.getInputs().get(0).getPublicKey();
            internalOutput.scriptType =
                    publicKey.isHardwareWalletWrappedP2Wpkh()
                            ? TREZOR_PAY_TO_WRAPPED_WITNESS
                            : TREZOR_PAY_TO_ADDRESS;

        } else {
            internalOutput = null;
        }

        // 4. Profit, I guess:
        return new SatelliteWithdrawalJson(inputs, externalOutput, internalOutput);
    }

    /**
     * Json constructor.
     */
    public SatelliteWithdrawalJson() {
    }

    private SatelliteWithdrawalJson(List<InputJson> inputs,
                                    ExternalOutputJson externalOutput,
                                    @Nullable InternalOutputJson internalOutputJson) {

        this.inputs = inputs;
        this.externalOutput = externalOutput;
        this.internalOutput = internalOutputJson;
    }
}
