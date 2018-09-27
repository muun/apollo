package io.muun.apollo.domain.model;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;


public class PaymentRequest {

    public enum Type {
        TO_CONTACT,
        TO_ADDRESS
    }

    /**
     * Create a PaymentRequest to send money to a contact.
     */
    public static PaymentRequest toContact(Contact contact,
                                           MonetaryAmount amount,
                                           String description) {

        return new PaymentRequest(
                Type.TO_CONTACT,
                amount,
                description,
                contact,
                null
        );
    }

    /**
     * Create a PaymentRequest to send money to an address.
     */
    public static PaymentRequest toAddress(String address,
                                           MonetaryAmount amount,
                                           String description) {
        return new PaymentRequest(
                Type.TO_ADDRESS,
                amount,
                description,
                null,
                address
        );
    }

    @NotNull
    public final Type type;

    @Nullable
    public final MonetaryAmount amount;

    @Nullable
    public final String description;

    @Nullable
    public final Contact contact;

    @Nullable
    public final String address;


    private PaymentRequest(Type type,
                           MonetaryAmount amount,
                           String description,
                           Contact contact,
                           String address) {
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.contact = contact;
        this.address = address;
    }

    public PaymentRequest withChanges(MonetaryAmount newAmount, String newDescription) {
        return new PaymentRequest(type, newAmount, newDescription, contact, address);
    }
}
