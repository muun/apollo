package repository

import (
	"fmt"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/base/data/storage"
	"github.com/muun/libwallet/subsystems/security_card/internal/domain/model"
)

// The keys the paired card is recorded under: the version it reported and,
// from the firmware that reports one, its short device id.
const (
	keySecurityCardPairedVersion       = "securityCardPairedVersion"
	keySecurityCardPairedShortDeviceID = "securityCardPairedShortDeviceId"
)

// ProtocolRepository owns the client-side state of the Security Cards
// protocol.
// Backed by KeyValueStorage; callers never touch the raw KV API.
type ProtocolRepository struct {
	kv *storage.KeyValueStorage
}

func NewProtocolRepository(kv *storage.KeyValueStorage) *ProtocolRepository {
	return &ProtocolRepository{kv: kv}
}

// SavePendingPairChallenge persists the challenge atomically. The
// timestamp is captured here so callers don't need to coordinate it.
func (r *ProtocolRepository) SavePendingPairChallenge(serverPubKeyInHex string) error {
	var items = make(map[string]any)
	items[storage.KeyPendingPairChallengeServerPubKeyInHex] = serverPubKeyInHex
	items[storage.KeyPendingPairChallengeReceivedAtUnixMillis] = time.Now().UnixMilli()
	if err := r.kv.SaveBatch(items); err != nil {
		return fmt.Errorf("saving pending pair challenge: %w", err) //nolint:forbidigo // TODO: use errors.Errorf from go-errors for stack traces
	}
	return nil
}

// LoadPendingPairChallenge returns the persisted challenge, or nil if it
// is absent. The two backing keys are written together via SaveBatch in
// SavePendingPairChallenge, so they are either both present or both
// absent. A nil return means "no pending challenge" — callers must
// trigger a refresh before proceeding.
func (r *ProtocolRepository) LoadPendingPairChallenge() (*model.PendingPairChallenge, error) { //nolint:lll // TODO: line too long
	keys := []string{
		storage.KeyPendingPairChallengeServerPubKeyInHex,
		storage.KeyPendingPairChallengeReceivedAtUnixMillis,
	}
	items, err := r.kv.GetBatch(keys)
	if err != nil {
		return nil, fmt.Errorf("loading pending pair challenge: %w", err) //nolint:forbidigo // TODO: use errors.Errorf from go-errors for stack traces
	}

	serverPubKeyHex := items[storage.KeyPendingPairChallengeServerPubKeyInHex]
	receivedAtMillis := items[storage.KeyPendingPairChallengeReceivedAtUnixMillis]

	if serverPubKeyHex == nil || receivedAtMillis == nil {
		return nil, nil
	}
	return &model.PendingPairChallenge{ //nolint:muun_model_constructor // TODO: use NewPendingPairChallenge instead of struct literal
		ServerPubKeyInHex:  serverPubKeyHex.(string),
		ReceivedAtInMillis: receivedAtMillis.(int64),
	}, nil
}

// ClearPendingPairChallenge wipes the persisted challenge once it has
// been consumed (success) or the attempt failed. Errors are ignored on
// purpose: if Delete fails, the next SavePendingPairChallenge call will
// overwrite the keys anyway.
func (r *ProtocolRepository) ClearPendingPairChallenge() {
	// TODO: DeleteBatch or a transaction should be used here to
	// delete all items at the same time. It is not a bug now.
	_ = r.kv.Delete(storage.KeyPendingPairChallengeServerPubKeyInHex)
	_ = r.kv.Delete(storage.KeyPendingPairChallengeReceivedAtUnixMillis)
}

// SavePairedCard records the card the sign flow paired with, in place of
// whichever card was recorded before.
func (r *ProtocolRepository) SavePairedCard(card *model.PairedCard) error {
	items := map[string]any{
		keySecurityCardPairedVersion:       card.Version,
		keySecurityCardPairedShortDeviceID: card.ShortDeviceIDInHex,
	}
	if err := r.kv.SaveBatch(items); err != nil {
		return errors.Errorf("saving paired card: %w", err)
	}
	return nil
}

// LoadPairedCard returns the card the sign flow last paired with, or nil
// when none is recorded.
//
// A card recorded before the short device id was, has a version and no id.
// It comes back with an empty id, so a card that reports one pairs once
// more and is recorded whole.
func (r *ProtocolRepository) LoadPairedCard() (*model.PairedCard, error) {
	items, err := r.kv.GetBatch([]string{
		keySecurityCardPairedVersion,
		keySecurityCardPairedShortDeviceID,
	})
	if err != nil {
		return nil, errors.Errorf("loading paired card: %w", err)
	}

	version, ok := items[keySecurityCardPairedVersion].(string)
	if !ok {
		return nil, nil
	}
	shortDeviceIDInHex, _ := items[keySecurityCardPairedShortDeviceID].(string)
	return model.NewPairedCard(version, shortDeviceIDInHex), nil
}
