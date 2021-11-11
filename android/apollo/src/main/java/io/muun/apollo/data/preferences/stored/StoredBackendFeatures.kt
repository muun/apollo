package io.muun.apollo.data.preferences.stored

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class StoredBackendFeatures {
    // Needs to be var for Jackson de/serialization
    var features = listOf<String>()
}