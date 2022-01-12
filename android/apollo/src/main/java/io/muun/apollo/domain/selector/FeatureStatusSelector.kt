package io.muun.apollo.domain.selector

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.BlockchainHeightRepository
import io.muun.apollo.data.preferences.FeaturesRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.libwallet.UAF_TAPROOT
import io.muun.apollo.domain.libwallet.isEqualTo
import io.muun.apollo.domain.libwallet.toLibwallet
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.domain.model.user.User
import io.muun.apollo.domain.utils.toLibwalletIntList
import io.muun.apollo.domain.utils.toLibwalletModel
import libwallet.Libwallet
import libwallet.UserActivatedFeature
import rx.Observable
import javax.inject.Inject

class FeatureStatusSelector @Inject constructor(
    private val userRepository: UserRepository,
    private val blockchainHeightRepository: BlockchainHeightRepository,
    private val featuresRepository: FeaturesRepository
) {

    companion object {
        var DEBUG_TAPROOT_STATUS: UserActivatedFeatureStatus? = null
    }

    fun watch(feature: UserActivatedFeature): Observable<UserActivatedFeatureStatus> {
        return Observable.combineLatest(
            userRepository.fetch(),
            blockchainHeightRepository.fetch(),
            featuresRepository.fetch(),
            Observable.just(feature),
            this::combineState
        )
    }

    private fun combineState(
        user: User,
        blockHeight: Int,
        backendFeatures: List<MuunFeature>,
        wantedFeature: UserActivatedFeature
    ): UserActivatedFeatureStatus {

        if (wantedFeature.isEqualTo(UAF_TAPROOT) && DEBUG_TAPROOT_STATUS != null) {
            return DEBUG_TAPROOT_STATUS!!
        }

        val uafStatus = Libwallet.determineUserActivatedFeatureStatus(
            wantedFeature,
            blockHeight.toLong(),
            user.emergencyKitVersions.toLibwalletIntList(),
            backendFeatures.map { it.toLibwalletModel() }.toLibwalletModel(),
            Globals.INSTANCE.network.toLibwallet()
        )

        return UserActivatedFeatureStatus.fromLibwalletModel(uafStatus)
    }

    fun get(feature: UserActivatedFeature): UserActivatedFeatureStatus =
        watch(feature).toBlocking().first()

}