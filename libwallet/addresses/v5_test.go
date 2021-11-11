package addresses

import (
	"reflect"
	"testing"
)

func TestCreateAddressV5(t *testing.T) {
	const (
		addressPath = "m/schema:1'/recovery:1'/external:1/17"

		v5Address       = "bcrt1pvqngr85tm8hmsv2hjyrejlpsy7u65f7vke8mmrxnyuj3aj3xsapqvh8yrf"
		basePK          = "tpubDBf5wCeqg3KrLJiXaveDzD5JtFJ1ss9NVvFMx4RYS73SjwPEEawcAQ7V1B5DGM4gunWDeYNrnkc49sUaf7mS1wUKiJJQD6WEctExUQoLvrg"
		baseCosigningPK = "tpubDB22PFkUaHoB7sgxh7exCivV5rAevVSzbB8WkFCCdbHq39r8xnYexiot4NGbi8PM6E1ySVeaHsoDeMYb6EMndpFrzVmuX8iQNExzwNpU61B"
		basePath        = "m/schema:1'/recovery:1'"
	)

	baseMuunKey := parseKey(baseCosigningPK)
	muunKey := derive(baseMuunKey, basePath, addressPath)

	baseUserKey := parseKey(basePK)
	userKey := derive(baseUserKey, basePath, addressPath)

	expectedAddr := &WalletAddress{address: v5Address, derivationPath: addressPath, version: V5}

	actualAddr, err := CreateAddressV5(userKey, muunKey, addressPath, network)
	if err != nil {
		t.Fatal(err)
	}

	if !reflect.DeepEqual(actualAddr, expectedAddr) {
		t.Errorf("Created v5 address %v, expected %v", actualAddr, expectedAddr)
	}
}
