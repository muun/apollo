package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.libwallet.LibwalletBridge;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.OperationWithMetadata;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.common.api.OperationMetadataJson;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Schema;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.RandomGenerator;

import org.bitcoinj.core.NetworkParameters;

import javax.inject.Inject;

public class OperationMetadataMapper {

    private final KeysRepository keysRepository;
    private final NetworkParameters networkParameters;

    @Inject
    public OperationMetadataMapper(final KeysRepository keysRepository,
                                   final NetworkParameters networkParameters) {
        this.keysRepository = keysRepository;
        this.networkParameters = networkParameters;
    }

    /**
     * Map an OperationWithMetadata to an Operation decrypting the metadata.
     */
    public Operation mapFromMetadata(final OperationWithMetadata operation) {
        final OperationMetadataJson metadata = decryptMetadata(operation);

        final String description;
        if (metadata != null && metadata.description != null) {
            description = metadata.description;
        } else {
            description = operation.getDescription();
        }

        return new Operation(
                null,
                operation.getId(),
                operation.getDirection(),
                operation.isExternal(),
                operation.getSenderProfile(),
                operation.getSenderIsExternal(),
                operation.getReceiverProfile(),
                operation.getReceiverIsExternal(),
                operation.getReceiverAddress(),
                operation.getReceiverAddressDerivationPath(),
                operation.getAmount(),
                operation.getFee(),
                operation.getConfirmations(),
                operation.getHash(),
                description,
                metadata,
                operation.getStatus(),
                operation.getCreationDate(),
                operation.getExchangeRateWindowHid(),
                operation.getSwap(),
                operation.getIncomingSwap(),
                operation.isRbf()
        );
    }

    private OperationMetadataJson decryptMetadata(final OperationWithMetadata operation) {
        final byte[] payload;

        if (!ExtensionsKt.isEmpty(operation.getReceiverMetadata())) {
            // TODO: we need to extract the sender public key from somewhere to verify the message
            payload = LibwalletBridge.decryptPayloadFromPeer(
                    getUserPrivateKey(),
                    operation.getReceiverMetadata(),
                    networkParameters
            );
        } else if (!ExtensionsKt.isEmpty(operation.getSenderMetadata())) {
            payload = LibwalletBridge.decryptPayload(
                    getUserPrivateKey(),
                    operation.getSenderMetadata(),
                    networkParameters
            );
        } else {
            return null;
        }

        return SerializationUtils.deserializeJson(
                OperationMetadataJson.class,
                Encodings.bytesToString(payload)
        );
    }

    /**
     * Map an Operation to include encrypted metadata for non p2p operation.
     */
    public OperationWithMetadata mapWithMetadata(final Operation operation) {

        final OperationMetadataJson metadata = new OperationMetadataJson(operation.description);

        return buildOperationWithMetadata(
                operation,
                encryptMetadata(metadata),
                null
        );
    }

    /**
     * Map an Operation to include encrypted metadata for a p2p operation.
     */
    public OperationWithMetadata mapWithMetadataForContact(
            final Operation operation,
            final Contact contact) {

        final OperationMetadataJson metadata = new OperationMetadataJson(operation.description);

        return buildOperationWithMetadata(
                operation,
                encryptMetadata(metadata),
                encryptMetadataForContact(metadata, contact)
        );
    }

    private String encryptMetadata(OperationMetadataJson metadata) {
        final String payload = SerializationUtils.serializeJson(
                OperationMetadataJson.class, metadata
        );

        // We need to derive an unique key for encryption.
        // The first derivation might not be enough to ensure uniqueness due to the birthday
        // paradox, so we derive two random indexes.
        final PrivateKey encryptionKey = getUserPrivateKey()
                .deriveFromAbsolutePath(Schema.getMetadataKeyPath())
                .deriveNextValidChild(RandomGenerator.getPositiveInt())
                .deriveNextValidChild(RandomGenerator.getPositiveInt());

        return LibwalletBridge.encryptPayload(
                encryptionKey,
                Encodings.stringToBytes(payload),
                networkParameters
        );
    }

    private String encryptMetadataForContact(OperationMetadataJson metadata, Contact contact) {
        final String payload = SerializationUtils.serializeJson(
                OperationMetadataJson.class, metadata
        );

        // Derive a new random key for the peer to use for encryption
        // The first derivation might not be enough to ensure uniqueness due to the birthday
        // paradox, so we derive two random indexes.
        final PublicKey encryptionKey = contact.publicKey
                .deriveNextValidChild(RandomGenerator.getPositiveInt())
                .deriveNextValidChild(RandomGenerator.getPositiveInt());

        return LibwalletBridge.encryptPayloadToPeer(
                getUserPrivateKey(),
                encryptionKey,
                Encodings.stringToBytes(payload),
                networkParameters
        );
    }

    /**
     * Build an OperationWithMetadata from an Operation and metadata.
     */
    public OperationWithMetadata buildOperationWithMetadata(final Operation operation,
                                                            final String senderMetadata,
                                                            final String receiverMetadata) {
        return new OperationWithMetadata(
                operation.getHid(),
                operation.direction,
                operation.isExternal,
                operation.senderProfile,
                operation.senderIsExternal,
                operation.receiverProfile,
                operation.receiverIsExternal,
                operation.receiverAddress,
                operation.receiverAddressDerivationPath,
                null,
                operation.amount,
                operation.fee,
                operation.confirmations,
                operation.hash,
                null,
                operation.status,
                operation.creationDate,
                operation.exchangeRateWindowHid,
                operation.swap,
                receiverMetadata,
                senderMetadata,
                operation.incomingSwap,
                operation.isRbf
        );
    }

    private PrivateKey getUserPrivateKey() {
        return keysRepository.getBasePrivateKey().toBlocking().first();
    }
}
