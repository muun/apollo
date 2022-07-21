package libwallet

import (
	"fmt"
)

const (
	BackendFeatureTaproot = "TAPROOT"
	BackendFeatureTaprootPreactivation = "TAPROOT_PREACTIVATION"
	BackendFeatureApolloBiometrics = "APOLLO_BIOMETRICS"

	UserActivatedFeatureStatusOff                 = "off"
	UserActivatedFeatureStatusCanPreactivate      = "can_preactivate"
	UserActivatedFeatureStatusCanActivate         = "can_activate"
	UserActivatedFeatureStatusPreactivated        = "preactivated"
	UserActivatedFeatureStatusScheduledActivation = "scheduled_activation"
	UserActivatedFeatureStatusActive              = "active"
)

var UserActivatedFeatureTaproot UserActivatedFeature = &taprootUserActivatedFeature{}

type UserActivatedFeature interface {
	Blockheight(*Network) int
	RequiredKitVersion() int
	BackendFeature() string
	BackendPreactivationFeature() string
}

type taprootUserActivatedFeature struct {}

func (t *taprootUserActivatedFeature) Blockheight(network *Network) int {

	switch network.Name() {
	case Mainnet().Name():
		// 709_632 is defined in the BIP and we use a 6 block safety margin
		return 709_632 + 6

	case Regtest().Name():
		// A nice low value for testing
		return 100

	case Testnet().Name():
		// A nice low value for testing
		return 100
	}

	panic(fmt.Sprintf("Unexpected network: %v", network.Name()))
}

func (t *taprootUserActivatedFeature) RequiredKitVersion() int {
	return EKVersionMusig
}

func (t *taprootUserActivatedFeature) BackendFeature() string {
	return BackendFeatureTaproot
}

func (t *taprootUserActivatedFeature) BackendPreactivationFeature() string {
	return BackendFeatureTaprootPreactivation
}

func DetermineUserActivatedFeatureStatus(
	feature UserActivatedFeature,
	blockHeight int,
	exportedKitVersions *IntList,
	backendFeatures *StringList,
	network *Network,
) string {

	// If the feature is turned off by houston, two things can happen:
	// 1. The (pre)activation event is not enabled: ie kill switch
	// 2. Activation is held-off and the status is frozen as if the network
	//   never activated.: ie backend feature toggle

	if len(feature.BackendFeature()) > 0 &&
		!backendFeatures.Contains(feature.BackendPreactivationFeature()) {
		return UserActivatedFeatureStatusOff
	}

	activatedByHouston := len(feature.BackendFeature()) > 0 &&
		backendFeatures.Contains(feature.BackendFeature())

	activatedByNetwork := blockHeight >= feature.Blockheight(network)

	// If the user never exported a kit, they have the feature implicitly active
	if exportedKitVersions.Length() == 0 {

		if activatedByNetwork && activatedByHouston {
			return UserActivatedFeatureStatusActive

		} else if activatedByHouston {
			return UserActivatedFeatureStatusScheduledActivation

		} else {
			return UserActivatedFeatureStatusOff
		}
	}

	var maxKitVersion int
	for i := 0; i < exportedKitVersions.Length(); i++ {
		if exportedKitVersions.Get(i) > maxKitVersion {
			maxKitVersion = exportedKitVersions.Get(i)
		}
	}

	if maxKitVersion >= feature.RequiredKitVersion() {

		// If the user activated already, it's up to the network

		if activatedByNetwork && activatedByHouston {
			return UserActivatedFeatureStatusActive

		} else if exportedKitVersions.Length() > 1 {
			// If the user had pre-existing kits, then they updated
			return UserActivatedFeatureStatusPreactivated

		} else {
			// Otherwise they just happened to export during the activation
			return UserActivatedFeatureStatusScheduledActivation
		}

	} else {

		// Otherwise it's up to the user

		if !activatedByHouston {
			return UserActivatedFeatureStatusOff

		} else if activatedByNetwork {
			return UserActivatedFeatureStatusCanActivate

		} else {
			return UserActivatedFeatureStatusCanPreactivate
		}
	}

}



