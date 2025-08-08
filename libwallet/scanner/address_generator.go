package scanner

import (
	"fmt"
	"github.com/muun/libwallet"
	"log/slog"
)

type AddressGenerator struct {
	addressCount     int
	userKey          *libwallet.HDPublicKey
	muunKey          *libwallet.HDPublicKey
	generateContacts bool
}

func NewAddressGenerator(userKey, muunKey *libwallet.HDPublicKey, generateContacts bool) *AddressGenerator {
	return &AddressGenerator{
		addressCount:     0,
		userKey:          userKey,
		muunKey:          muunKey,
		generateContacts: generateContacts,
	}
}

// Stream returns a channel that emits all addresses generated.
func (g *AddressGenerator) Stream(countPerDerivationTree int64) chan libwallet.MuunAddress {
	ch := make(chan libwallet.MuunAddress)

	go func() {
		g.generate(ch, countPerDerivationTree)
		close(ch)
	}()

	return ch
}

func (g *AddressGenerator) generate(consumer chan libwallet.MuunAddress, countPerDerivationTree int64) {
	g.generateChangeAddrs(consumer, countPerDerivationTree)
	g.generateExternalAddrs(consumer, countPerDerivationTree)
	if g.generateContacts {
		g.generateContactAddrs(consumer, 100)
	}
}

func (g *AddressGenerator) generateChangeAddrs(consumer chan libwallet.MuunAddress, countPerDerivationTree int64) {
	const changePath = "m/1'/1'/0"
	changeUserKey, _ := g.userKey.DeriveTo(changePath)
	changeMuunKey, _ := g.muunKey.DeriveTo(changePath)

	g.deriveTree(consumer, changeUserKey, changeMuunKey, countPerDerivationTree, "change")
}

func (g *AddressGenerator) generateExternalAddrs(consumer chan libwallet.MuunAddress, countPerDerivationTree int64) {
	const externalPath = "m/1'/1'/1"
	externalUserKey, _ := g.userKey.DeriveTo(externalPath)
	externalMuunKey, _ := g.muunKey.DeriveTo(externalPath)

	g.deriveTree(consumer, externalUserKey, externalMuunKey, countPerDerivationTree, "external")
}

func (g *AddressGenerator) generateContactAddrs(consumer chan libwallet.MuunAddress, numContacts int64) {
	const addressPath = "m/1'/1'/2"
	contactUserKey, _ := g.userKey.DeriveTo(addressPath)
	contactMuunKey, _ := g.muunKey.DeriveTo(addressPath)
	for i := int64(0); i <= numContacts; i++ {
		partialContactUserKey, _ := contactUserKey.DerivedAt(i)
		partialMuunUserKey, _ := contactMuunKey.DerivedAt(i)

		branch := fmt.Sprintf("contacts-%v", i)
		g.deriveTree(consumer, partialContactUserKey, partialMuunUserKey, 200, branch)
	}
}

func (g *AddressGenerator) deriveTree(
	consumer chan libwallet.MuunAddress,
	rootUserKey, rootMuunKey *libwallet.HDPublicKey,
	countPerDerivationTree int64,
	name string,
) {
	for i := int64(0); i <= countPerDerivationTree; i++ {
		userKey, err := rootUserKey.DerivedAt(i)
		if err != nil {
			slog.Warn(fmt.Sprintf("skipping child %v for %v due to %v", i, name, err))
			continue
		}
		muunKey, err := rootMuunKey.DerivedAt(i)
		if err != nil {
			slog.Warn(fmt.Sprintf("skipping child %v for %v due to %v", i, name, err))
			continue
		}

		addrV2, err := libwallet.CreateAddressV2(userKey, muunKey)
		if err == nil {
			consumer <- addrV2
			g.addressCount++
		} else {
			slog.Warn(fmt.Sprintf("failed to generate %v v2 for %v due to %v", name, i, err))
		}

		addrV3, err := libwallet.CreateAddressV3(userKey, muunKey)
		if err == nil {
			consumer <- addrV3
			g.addressCount++
		} else {
			slog.Warn(fmt.Sprintf("failed to generate %v v3 for %v due to %v", name, i, err))
		}

		addrV4, err := libwallet.CreateAddressV4(userKey, muunKey)
		if err == nil {
			consumer <- addrV4
			g.addressCount++
		} else {
			slog.Warn(fmt.Sprintf("failed to generate %v v4 for %v due to %v", name, i, err))
		}

		addrV5, err := libwallet.CreateAddressV5(userKey, muunKey)
		if err == nil {
			consumer <- addrV5
			g.addressCount++
		} else {
			slog.Warn(fmt.Sprintf("failed to generate %v v5 for %v due to %v", name, i, err))
		}

		addrV6, err := libwallet.CreateAddressV6(userKey, muunKey)
		if err == nil {
			consumer <- addrV6
			g.addressCount++
		} else {
			slog.Warn(fmt.Sprintf("failed to generate %v v6 for %v due to %v", name, i, err))
		}
	}
}
