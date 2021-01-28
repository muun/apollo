package io.muun.apollo.data.preferences.stored

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class StoredEkVerificationCodes(
    /**
     * The latest Emergency Kit verification codes, where the first item is the most recent.
     */
    var fromNewestToOldest: MutableList<String> = mutableListOf()
) {

    companion object {
        const val MAX_CODES = 10
    }

    @JsonIgnore
    fun getNewest(): String? =
        fromNewestToOldest.firstOrNull()

    fun containsOld(code: String) =
        fromNewestToOldest.contains(code) && getNewest() != code

    fun addNewest(code: String) {
        fromNewestToOldest.add(0, code)

        if (fromNewestToOldest.size > MAX_CODES) {
            fromNewestToOldest.removeLast()
        }
    }
}