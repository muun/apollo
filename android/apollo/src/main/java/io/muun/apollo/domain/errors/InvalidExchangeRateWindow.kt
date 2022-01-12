package io.muun.apollo.domain.errors

class InvalidExchangeRateWindow(
    windowId: Long,
    latestWindowId: Long,
    fixedWindowId: Long?
) : MuunError("Unknown rate window id $windowId. Latest: $latestWindowId, Fixed: $fixedWindowId")