package io.muun.apollo.data.afs

import android.app.ApplicationExitInfo
import io.muun.common.Optional
import javax.inject.Inject

// open to make tests work with mockito. We should probably move to mockKAdd commentMore actions
open class InternalMetricsProvider @Inject constructor(
    private val metricsProvider: MetricsProvider
) {

    // open to make tests work with mockito. We should probably move to mockK
    open val region: Optional<String>
        get() = metricsProvider.telephonyNetworkRegion
}