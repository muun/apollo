package io.muun.apollo.data.net

import io.muun.apollo.BaseTest
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.common.api.MuunFeatureJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ModelObjectsMapperTest : BaseTest() {

    @Test
    fun `allow unknown values in features enum`() {

        val taproot = MuunFeatureJson.TAPROOT
        val taprootPreactivation = MuunFeatureJson.TAPROOT_PREACTIVATION
        val biometrics = MuunFeatureJson.APOLLO_BIOMETRICS
        val json = "[\"$taproot\", \"$taprootPreactivation\", \"$biometrics\", \"NEW_FEATURE\"]"

        val features = SerializationUtils.deserializeList(MuunFeatureJson::class.java, json)

        assertThat(features[0]).isEqualTo(taproot)
        assertThat(features[1]).isEqualTo(taprootPreactivation)
        assertThat(features[2]).isEqualTo(biometrics)
        assertThat(features[3]).isEqualTo(MuunFeatureJson.UNSUPPORTED_FEATURE)
    }
}