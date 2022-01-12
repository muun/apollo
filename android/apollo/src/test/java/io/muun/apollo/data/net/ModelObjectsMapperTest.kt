package io.muun.apollo.data.net

import io.muun.apollo.BaseTest
import io.muun.apollo.data.secure_storage.SecureStorageProviderTest
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.common.MuunFeatureJson
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.Test

class ModelObjectsMapperTest : BaseTest() {

    @Test
    fun `allow unknown values in features enum`() {

        val taproot = MuunFeatureJson.TAPROOT
        val taprootPreactivation = MuunFeatureJson.TAPROOT_PREACTIVATION
        val json = "[\"$taproot\", \"$taprootPreactivation\", \"NEW_FEATURE\"]"

        val features = SerializationUtils.deserializeList(MuunFeatureJson::class.java, json)

        assertThat(features[0]).isEqualTo(taproot)
        assertThat(features[1]).isEqualTo(taprootPreactivation)
        assertThat(features[2]).isEqualTo(MuunFeatureJson.UNSUPPORTED_FEATURE)
    }
}