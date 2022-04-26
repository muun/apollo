package io.muun.apollo.domain.model

import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.hd.PublicKeyPair

/**
 * Class that represent contacts created with multisig bitcoin script. This means:
 * - Contacts with addressVersion +V2
 * - CosigningPublicKey is always NOT NULL (unlike single sig contacts, with V1 address)
 */
class MultisigContact(
    id: Long?,
    hid: Long,
    publicProfile: PublicProfile,
    maxAddressVersion: Int,
    publicKey: PublicKey,
    cosigningPublicKey: PublicKey,
    lastDerivationIndex: Long?
) : Contact(
    id,
    hid,
    publicProfile,
    maxAddressVersion,
    publicKey,
    cosigningPublicKey,
    lastDerivationIndex
) {

    constructor(contact: Contact) : this(
        contact.id,
        contact.hid,
        contact.publicProfile,
        contact.maxAddressVersion,
        contact.publicKey,
        checkNotNull<PublicKey>(contact.cosigningPublicKey),
        contact.lastDerivationIndex
    )

    fun getPublicKeyPair(): PublicKeyPair {
        return PublicKeyPair(publicKey, cosigningPublicKey)
    }
}