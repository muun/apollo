package io.muun.apollo.data.net.base

import android.net.TrafficStats
import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.external.HoustonConfig
import io.muun.apollo.data.net.base.interceptor.AuthHeaderInterceptor
import io.muun.apollo.data.net.base.interceptor.BackgroundExecutionMetricsInterceptor
import io.muun.apollo.data.net.base.interceptor.IdempotencyKeyInterceptor
import io.muun.apollo.data.net.base.interceptor.LanguageHeaderInterceptor
import io.muun.apollo.data.net.base.interceptor.VersionHeaderInterceptor
import io.muun.apollo.data.os.Configuration
import io.muun.apollo.data.serialization.SerializationUtils
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.validation.constraints.NotNull

/**
 * Creates a client.
 *
 * @param serviceClass The class of the service that is used by the client.
 */
open class BaseClient<ServiceT> protected constructor(
    private val serviceClass: Class<ServiceT>,
) {

    /**
     * Creates a retrofit instance of a service.
     */
    protected val service: ServiceT by lazy {
        val jsonMapper = SerializationUtils.JSON_MAPPER

        return@lazy Retrofit.Builder()
            .baseUrl(houstonConfig.url)
            .client(httpClient)
            .addConverterFactory(ScalarsConverterFactory.create()) // note: before jackson!
            .addConverterFactory(JacksonConverterFactory.create(jsonMapper))
            .addCallAdapterFactory(callAdapterFactory)
            .build()
            .create(serviceClass)
    }

    @Inject
    lateinit var callAdapterFactory: CallAdapterFactory

    @Inject
    lateinit var authHeaderInterceptor: AuthHeaderInterceptor

    @Inject
    lateinit var versionHeaderInterceptor: VersionHeaderInterceptor

    @Inject
    lateinit var languageHeaderInterceptor: LanguageHeaderInterceptor

    @Inject
    lateinit var idempotencyKeyInterceptor: IdempotencyKeyInterceptor

    @Inject
    lateinit var backgroundExecutionMetricsInterceptor: BackgroundExecutionMetricsInterceptor

    @Inject
    lateinit var config: Configuration

    @Inject
    lateinit var houstonConfig: HoustonConfig

    /**
     * Returns a fully-configured HttpClient.
     */
    @get:NotNull
    private val httpClient: OkHttpClient
        get() {
            /* Bring this back when we're sure how
            val certificatePinner = CertificatePinner.Builder()
                // CN=HOUSTON_DOMAIN
                .add(
                    houstonConfig.domain,
                    houstonConfig.certificatePin
                )
                // CN=Amazon, OU=Server CA 1B
                .add(
                    houstonConfig.domain,
                    "sha256/JSMzqOOrtyOT1kmau6zKhgT676hGgczD5VMdRMyJZFA="
                )
                .build()
             */

            val threadTaggingListener = getThreadTaggingListener()

            val builder = OkHttpClient.Builder()
                //.certificatePinner(certificatePinner)
                .eventListener(threadTaggingListener)
                .readTimeout(config.getLong("net.timeoutInSec"), TimeUnit.SECONDS)
                .addInterceptor(versionHeaderInterceptor)
                .addInterceptor(languageHeaderInterceptor)
                .addInterceptor(authHeaderInterceptor)
                .addInterceptor(idempotencyKeyInterceptor)
                .addInterceptor(backgroundExecutionMetricsInterceptor)

            if (!Globals.INSTANCE.isRelease || Globals.INSTANCE.isDogfood) {
                val loggingInterceptor = HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY)
                builder.addInterceptor(loggingInterceptor)
            }
            return builder.build()
        }

    /**
     * Since android O sockets should be tagged for the OS to track network usage.
     */
    private fun getThreadTaggingListener(): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
        }

        override fun callEnd(call: Call) {
            TrafficStats.clearThreadStatsTag()
        }
    }
}