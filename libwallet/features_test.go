package libwallet

import (
	"testing"
)

func Test_DetermineUserActivatedFeatureStatus(t *testing.T) {
	backendFeaturesWithTaproot := NewStringListWithElements([]string{
		BackendFeatureTaproot, BackendFeatureTaprootPreactivation,
	})
	backendFeaturesWithTaprootPreactivation := NewStringListWithElements([]string{
		BackendFeatureTaprootPreactivation,
	})
	backendFeaturesWithoutTaproot := NewStringListWithElements([]string{})
	neverExportedKit := newIntList([]int{})
	exportedPreviousKit := newIntList([]int{EKVersionDescriptors})
	exportedOnlyLatest := newIntList([]int{EKVersionMusig})
	exportedBoth := newIntList([]int{EKVersionDescriptors, EKVersionMusig})

	type args struct {
		feature         UserActivatedFeature
		height          int
		kitVersion      *IntList
		backendFeatures *StringList
		network         *Network
	}

	const (
		postTaprootActivationHeight = 709_650
		preTaprootActivationHeight  = 709_620
	)

	tests := []struct {
		name string
		args args
		want string
	}{
		{
			"taproot scheduled in regtest",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          99,
				kitVersion:      neverExportedKit,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Regtest(),
			},
			UserActivatedFeatureStatusScheduledActivation,
		},
		{
			"taproot off in regtest",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          100,
				kitVersion:      neverExportedKit,
				backendFeatures: backendFeaturesWithoutTaproot,
				network:         Regtest(),
			},
			UserActivatedFeatureStatusOff,
		},
		{
			"taproot live in mainnet with no kit",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          postTaprootActivationHeight,
				kitVersion:      neverExportedKit,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusActive,
		},
		{
			"taproot live in mainnet with new kit",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          postTaprootActivationHeight,
				kitVersion:      exportedBoth,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusActive,
		},
		{
			"taproot preactivated in mainnet with new kit",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          preTaprootActivationHeight,
				kitVersion:      exportedBoth,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusPreactivated,
		},
		{
			"taproot needs activation in mainnet",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          postTaprootActivationHeight,
				kitVersion:      exportedPreviousKit,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusCanActivate,
		},
		{
			"taproot can preactivate in mainnet",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          preTaprootActivationHeight,
				kitVersion:      exportedPreviousKit,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusCanPreactivate,
		},
		{
			"taproot scheduled in mainnet",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          preTaprootActivationHeight,
				kitVersion:      neverExportedKit,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusScheduledActivation,
		},
		{
			"scheduled activation in mainnet",
			args{
				feature:         UserActivatedFeatureTaproot,
				height:          preTaprootActivationHeight,
				kitVersion:      exportedOnlyLatest,
				backendFeatures: backendFeaturesWithTaproot,
				network:         Mainnet(),
			},
			UserActivatedFeatureStatusScheduledActivation,
		},
		{
			"backend only preactivation for pre-activated user",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: exportedBoth,
				backendFeatures: backendFeaturesWithTaprootPreactivation,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusPreactivated,
		},
		{
			"backend only preactivation for scheduled activated user with no kit",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: neverExportedKit,
				backendFeatures: backendFeaturesWithTaprootPreactivation,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusOff,
		},
		{
			"backend only preactivation for scheduled activated user with latest kit",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: exportedOnlyLatest,
				backendFeatures: backendFeaturesWithTaprootPreactivation,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusScheduledActivation,
		},
		{
			"backend turned off for pre-activated user",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: exportedBoth,
				backendFeatures: backendFeaturesWithoutTaproot,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusOff,
		},
		{
			"backend turned off for scheduled activated user with no kit",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: neverExportedKit,
				backendFeatures: backendFeaturesWithoutTaproot,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusOff,
		},
		{
			"backend turned off for scheduled activated user with latest kit",
			args{
				feature: UserActivatedFeatureTaproot,
				height: postTaprootActivationHeight,
				kitVersion: exportedOnlyLatest,
				backendFeatures: backendFeaturesWithoutTaproot,
				network: Mainnet(),
			},
			UserActivatedFeatureStatusOff,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := DetermineUserActivatedFeatureStatus(tt.args.feature, tt.args.height, tt.args.kitVersion, tt.args.backendFeatures, tt.args.network); got != tt.want {
				t.Errorf("DetermineUserActivatedFeatureStatus() = %v, want %v", got, tt.want)
			}
		})
	}
}
