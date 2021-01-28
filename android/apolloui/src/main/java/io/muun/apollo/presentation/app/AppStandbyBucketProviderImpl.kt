package io.muun.apollo.presentation.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import io.muun.apollo.data.external.AppStandbyBucketProvider
import io.muun.apollo.data.external.AppStandbyBucketProvider.Bucket

internal class AppStandbyBucketProviderImpl(val context: Context) : AppStandbyBucketProvider {

    override fun current(): Bucket {

        val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val stats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            stats.appStandbyBucket
        } else {
            0 // They start at 10 with ACTIVE and only go up from there so this will be UNKNOWN
        }

        return when (bucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> Bucket.ACTIVE
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> Bucket.FREQUENT
            UsageStatsManager.STANDBY_BUCKET_RARE -> Bucket.RARE
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> Bucket.WORKING_SET
            else -> Bucket.UNKNOWN
        }
    }
}