package io.muun.apollo.presentation.ui.fragments.error

import android.content.Context
import androidx.annotation.StringRes
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.utils.StyledStringRes
import io.muun.apollo.presentation.ui.utils.StyledStringRes.StringResWithArgs
import io.muun.common.utils.Preconditions
import javax.annotation.CheckReturnValue

interface ErrorViewModel {

    @Suppress("ArrayInDataClass")
    data class Builder(
        var loggingName: AnalyticsEvent.ERROR_TYPE? = null,
        var title: String? = null,
        var descRes: Int? = null,
        var descArgs: Array<String> = arrayOf(),
        var kind: ErrorViewKind = ErrorViewKind.FINAL,
        var canGoBack: Boolean = false,
    ) {

        @CheckReturnValue
        fun loggingName(loggingName: AnalyticsEvent.ERROR_TYPE) = apply {
            this.loggingName = loggingName
        }

        @CheckReturnValue
        fun title(title: String) = apply { this.title = title }

        @CheckReturnValue
        fun descriptionRes(@StringRes descRes: Int) = apply { this.descRes = descRes }

        @CheckReturnValue
        fun descriptionArgs(vararg descArgs: String) = apply { this.descArgs = arrayOf(*descArgs) }

        @CheckReturnValue
        fun kind(kind: ErrorViewKind) = apply { this.kind = kind }

        @CheckReturnValue
        fun canGoBack(canGoBack: Boolean) = apply { this.canGoBack = canGoBack }

        @CheckReturnValue
        fun build(): ErrorViewModel {

            checkNotNull(loggingName)
            checkNotNull(title)
            checkNotNull(descRes)

            return object : ErrorViewModel {
                override val description: StringResWithArgs
                    get() = StringResWithArgs(descRes!!, descArgs)

                override fun loggingName(): AnalyticsEvent.ERROR_TYPE =
                    loggingName!!

                override fun title(): String =
                    title!!

                override fun kind(): ErrorViewKind =
                    kind

                override fun canGoBack(): Boolean =
                    canGoBack
            }
        }
    }

    enum class ErrorViewKind {
        RETRYABLE,
        REPORTABLE,
        FINAL
    }

    // This imposes the limitation that description ALWAYS will be a stringRes (using directly a
    // string isn't allowed). Its ok, errors msgs should always be i18nalised, so this is no biggie.
    val description: StringResWithArgs

    // NOTE: dear maintainer, I'm sorry. This is the best I could do. When modifying this class
    // please have a look at the de/serialize functions. Specially when adding or removing fields.

    fun loggingName(): AnalyticsEvent.ERROR_TYPE

    fun title(): String

    fun description(context: Context): CharSequence =
        StyledStringRes(context, description.resId).toCharSequence(*description.args)

    fun kind(): ErrorViewKind =
        ErrorViewKind.FINAL

    fun canGoBack(): Boolean

    fun serialize(): String =
        loggingName().name +
            "$separator${title()}" +
            "$separator${serializeDesc()}" +
            "$separator${kind().name}" +
            "$separator${canGoBack()}"

    private fun serializeDesc(): String =
        description.serialize()

    companion object {
        const val separator = "!@#$"

        private const val numberOfFields = 5

        fun deserialize(serialization: String): ErrorViewModel {

            val chunks = serialization.split(separator)
            Preconditions.checkArgument(chunks.size == numberOfFields)

            val loggingName = AnalyticsEvent.ERROR_TYPE.valueOf(chunks[0])
            val title = chunks[1]
            val description = StringResWithArgs.deserialize(chunks[2])
            val kind = ErrorViewKind.valueOf(chunks[3])
            val canGoBack = chunks[4].toBoolean()

            return object : ErrorViewModel {

                override val description: StringResWithArgs
                    get() = description

                override fun loggingName(): AnalyticsEvent.ERROR_TYPE =
                    loggingName

                override fun title(): String =
                    title

                override fun kind(): ErrorViewKind =
                    kind

                override fun canGoBack(): Boolean =
                    canGoBack
            }
        }
    }
}