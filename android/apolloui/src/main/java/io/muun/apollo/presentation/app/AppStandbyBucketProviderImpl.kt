package io.muun.apollo.presentation.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import io.muun.apollo.data.external.AppStandbyBucketProvider
import io.muun.apollo.data.external.AppStandbyBucketProvider.Bucket
import io.muun.apollo.presentation.ui.utils.OS

internal class AppStandbyBucketProviderImpl(val context: Context) : AppStandbyBucketProvider {

    override fun current(): Bucket {

        if (!OS.supportsStandByBuckets()) {
            return Bucket.UNAVAILABLE
        }

        val stats = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

        @Suppress("FoldInitializerAndIfToElvis") // Prefer if for readability (familiarity)
        if (stats == null) {
            // Report a special bucket if we have no access
            return Bucket.UNAVAILABLE
        }

        return when (stats.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> Bucket.ACTIVE
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> Bucket.WORKING_SET
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> Bucket.FREQUENT
            UsageStatsManager.STANDBY_BUCKET_RARE -> Bucket.RARE
            else -> Bucket.UNKNOWN
        }
    }
}