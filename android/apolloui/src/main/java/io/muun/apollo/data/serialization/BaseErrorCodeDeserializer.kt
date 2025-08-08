package io.muun.apollo.data.serialization

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import io.muun.common.api.error.BaseErrorCode


class BaseErrorCodeDeserializer : JsonDeserializer<BaseErrorCode>() {

    @Suppress("FoldInitializerAndIfToElvis")
    override fun deserialize(parser: JsonParser, context: DeserializationContext): BaseErrorCode {

        val errorCodeValue: Int = parser.intValue

        val baseErrorCode = BaseErrorCode.fromValue(errorCodeValue)

        if (baseErrorCode == null) {
            throw JsonParseException(parser, "Unknown BaseErrorCode value: $errorCodeValue")
        }

        return baseErrorCode
    }
}