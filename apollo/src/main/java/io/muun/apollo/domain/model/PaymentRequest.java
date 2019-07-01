package io.muun.apollo.domain.model;

import io.muun.common.utils.LnInvoice;
import io.muun.common.utils.Preconditions;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;


public class PaymentRequest {

    public enum Type {
        TO_CONTACT,
        TO_ADDRESS,
        TO_LN_INVOICE,
        TO_HARDWARE_WALLET,
        FROM_HARDWARE_WALLET
    }

    /**
     * Create a PaymentRequest to send money to a contact.
     */
    public static PaymentRequest toContact(Contact contact,
                                           MonetaryAmount amount,
                                           String description) {

        Preconditions.checkNotNull(contact);

        return new PaymentRequest(
                Type.TO_CONTACT,
                amount,
                description,
                contact,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Create a PaymentRequest to send money to an address.
     */
    public static PaymentRequest toAddress(String address,
                                           @Nullable MonetaryAmount amount,
                                           @Nullable String description) {

        Preconditions.checkNotNull(address);

        return new PaymentRequest(
                Type.TO_ADDRESS,
                amount,
                description,
                null,
                address,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Create a PaymentRequest to send money to an address.
     */
    public static PaymentRequest toHardwareWallet(HardwareWallet hardwareWallet,
                                                  MonetaryAmount amount,
                                                  String description) {

        Preconditions.checkNotNull(hardwareWallet);

        return new PaymentRequest(
                Type.TO_HARDWARE_WALLET,
                amount,
                description,
                null,
                null,
                hardwareWallet,
                null,
                null,
                null
        );
    }

    /**
     * Create a PaymentRequest to send money from a hardware wallet.
     */
    public static PaymentRequest fromHardwareWallet(HardwareWallet hardwareWallet,
                                                    MonetaryAmount amount,
                                                    String description) {

        Preconditions.checkNotNull(hardwareWallet);

        return new PaymentRequest(
                Type.FROM_HARDWARE_WALLET,
                amount,
                description,
                null,
                null,
                hardwareWallet,
                null,
                null,
                null
        );
    }

    /**
     * Create a PaymentRequest to send money to an Invoice.
     */
    public static PaymentRequest toLnInvoice(LnInvoice invoice,
                                             MonetaryAmount amount,
                                             String description,
                                             SubmarineSwap submarineSwap) {

        Preconditions.checkNotNull(invoice);

        return new PaymentRequest(
                Type.TO_LN_INVOICE,
                amount,
                description,
                null,
                null,
                null,
                invoice,
                submarineSwap,
                null
        );
    }

    @NotNull
    public final Type type;

    @Nullable
    public final MonetaryAmount amount;

    @Nullable
    public final CustomFeeRate customFeeRate;

    @Nullable
    public final String description;

    @Nullable
    public final Contact contact;

    @Nullable
    public final String address;

    @Nullable
    public final HardwareWallet hardwareWallet;

    @Nullable
    public final LnInvoice invoice;

    @Nullable
    public final SubmarineSwap swap;

    private PaymentRequest(Type type,
                           @Nullable MonetaryAmount amount,
                           @Nullable String description,
                           @Nullable Contact contact,
                           @Nullable String address,
                           @Nullable HardwareWallet hardwareWallet,
                           @Nullable LnInvoice invoice,
                           @Nullable SubmarineSwap swap,
                           @Nullable CustomFeeRate customFeeRate) {

        this.type = type;
        this.amount = amount;
        this.description = description;
        this.contact = contact;
        this.address = address;
        this.hardwareWallet = hardwareWallet;
        this.invoice = invoice;
        this.swap = swap;
        this.customFeeRate = customFeeRate;
    }

    /**
     * Return a cloned PaymentRequest with a new amount.
     */
    public PaymentRequest withChanges(MonetaryAmount newAmount) {
        return withChanges(newAmount, description);
    }

    /**
     * Return a cloned PaymentRequest with a new amount and a new description.
     */
    public PaymentRequest withChanges(MonetaryAmount newAmount, String newDesc) {
        return new PaymentRequest(
                type,
                newAmount,
                newDesc,
                contact,
                address,
                hardwareWallet,
                invoice,
                swap,
                customFeeRate
        );
    }
}
