package io.muun.apollo.presentation.ui.security_cards.utils

import android.content.Context
import io.muun.apollo.domain.model.marketplace.SecurityCardDetail
import io.muun.apollo.domain.model.marketplace.SecurityCardFullSpecs
import io.muun.apollo.domain.model.marketplace.SecurityCardMarketplace
import io.muun.apollo.domain.model.marketplace.SecurityCardProviderTheme
import io.muun.apollo.presentation.ui.utils.isInNightMode

fun SecurityCardMarketplace.SecurityCardProvider.theme(
    context: Context,
): SecurityCardProviderTheme {
    return if (context.isInNightMode()) {
        darkTheme
    } else {
        lightTheme
    }
}

fun SecurityCardDetail.SecurityCardProvider.theme(context: Context): SecurityCardProviderTheme {
    return if (context.isInNightMode()) {
        darkTheme
    } else {
        lightTheme
    }
}

fun SecurityCardFullSpecs.SecurityCardProvider.theme(context: Context): SecurityCardProviderTheme {
    return if (context.isInNightMode()) {
        darkTheme
    } else {
        lightTheme
    }
}
