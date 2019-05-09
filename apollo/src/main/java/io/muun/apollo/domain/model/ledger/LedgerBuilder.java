package io.muun.apollo.domain.model.ledger;

import io.muun.apollo.domain.model.trezor.HardwareWalletWithdrawal;
import io.muun.common.bitcoinj.MainNetParamsY;
import io.muun.common.crypto.hd.HardwareWalletOutput;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.schemes.TransactionSchemeV1;
import io.muun.common.crypto.schemes.TransactionSchemeV2;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.crypto.schemes.TransactionSchemeYpub;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.VarInt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class LedgerBuilder {

    /**
     * Build Ledger transaction (json) from transaction draft.
     */
    public static LedgerTransaction buildLedgerTransaction(HardwareWalletWithdrawal draft) {
        final List<List<Object>> inputs = new ArrayList<>();
        final List<String> associatedKeysets = new ArrayList<>();

        for (HardwareWalletOutput output : draft.getInputs()) {
            final List<Object> augmentedInput = new ArrayList<>();

            final Transaction transaction = new Transaction(
                    output.getPublicKey().getNetworkParameters(),
                    Encodings.hexToBytes(output.getRawPreviousTransaction())
            );

            augmentedInput.add(buildPreviousTransaction(transaction));
            augmentedInput.add(output.getIndex());

            if (output.getPublicKey().getNetworkParameters().equals(MainNetParamsY.get())) {
                augmentedInput.add(TransactionSchemeYpub.createRedeemScript(output.getPublicKey()));
            } else {
                augmentedInput.add(null);
            }
            inputs.add(augmentedInput);

            associatedKeysets.add(output.getPublicKey().getAbsoluteDerivationPath());
        }

        final String changePath;
        if (draft.hasChange()) {
            changePath = draft.getChangeAddress().getDerivationPath();
        } else {
            changePath = null;
        }

        final NetworkParameters params = draft.getInputs().get(0)
                .getPublicKey()
                .getNetworkParameters();

        final boolean segwit = params.equals(MainNetParamsY.get());

        throw new RuntimeException("TODO: implement Ledger withdrawal, remove this class");
        // final String outputScriptHex =
        //        Encodings.bytesToHex(getOutputScript(draft.getPaymentAddress()).getProgram());

        // return new LedgerTransaction(
        //       inputs,
        //        associatedKeysets,
        //        changePath,
        //        segwit,
        //        outputScriptHex
        //);
    }

    private static org.bitcoinj.script.Script getOutputScript(MuunAddress address) {
        switch (address.getVersion()) {
            case TransactionSchemeV1.ADDRESS_VERSION:
                return TransactionSchemeV1.createOutputScript(address);

            case TransactionSchemeV2.ADDRESS_VERSION:
                return TransactionSchemeV2.createOutputScript(address);

            case TransactionSchemeV3.ADDRESS_VERSION:
                return TransactionSchemeV3.createOutputScript(address);

            default:
                throw new MissingCaseError(address.getVersion(), "ADDRESS_VERSION");
        }
    }

    /**
     * Build Ledger transaction input from bitcoinj TransactionInput.
     */
    private static LedgerTransactionInput buildTransactionInput(TransactionInput input) {
        final TransactionOutPoint outpoint = input.getOutpoint();

        return new LedgerTransactionInput(
                Encodings.bytesToHex(outpoint.bitcoinSerialize()),
                Encodings.bytesToHex(input.getScriptBytes()),
                Long.toHexString(input.getSequenceNumber())
        );
    }

    /**
     * Build Ledger transaction output from bitcoinj TransactionOutput.
     */
    private static LedgerTransactionOutput buildTransactionOutput(TransactionOutput output) {
        return new LedgerTransactionOutput(
                Long.toHexString(output.getValue().getValue()),
                Encodings.bytesToHex(output.getScriptBytes())
        );
    }

    /**
     * Build previous Ledger transaction from bitcoinj Transaction.
     */
    private static LedgerPreviousTransaction buildPreviousTransaction(Transaction transaction) {
        final List<LedgerTransactionInput> inputs = new ArrayList<>();
        final List<LedgerTransactionOutput> outputs = new ArrayList<>();

        for (TransactionInput transactionInput : transaction.getInputs()) {
            inputs.add(buildTransactionInput(transactionInput));
        }

        for (TransactionOutput transactionOutput : transaction.getOutputs()) {
            outputs.add(buildTransactionOutput(transactionOutput));
        }

        final LedgerPreviousTransaction previousTransaction = new LedgerPreviousTransaction(
                Long.toHexString(transaction.getVersion()),
                inputs,
                outputs
        );

        if (transaction.isTimeLocked()) {
            previousTransaction.setLocktime(Long.toHexString(transaction.getLockTime()));
        }

        if (transaction.hasWitness()) {
            previousTransaction.setWitness(getWitness(transaction));
        }

        return previousTransaction;
    }

    private static String getWitness(Transaction transaction) {
        final ByteBuffer stream = ByteBuffer.allocate(transaction.getMessageSize());

        for (int i = 0; i < transaction.getInputs().size(); i++) {
            final TransactionWitness witness = transaction.getWitness(i);
            stream.put(new VarInt(witness.getPushCount()).encode());
            for (int y = 0; y < witness.getPushCount(); y++) {
                final byte[] push = witness.getPush(y);
                stream.put(new VarInt(push.length).encode());
                stream.put(push);
            }
        }

        final byte[] output = new byte[stream.position()];
        stream.rewind();
        stream.get(output);

        return Encodings.bytesToHex(output);
    }
}
