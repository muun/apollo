package io.muun.apollo.data.net.base;

import io.muun.apollo.data.net.base.interceptor.AuthHeaderInterceptor;
import io.muun.apollo.data.net.base.interceptor.DuplicatingInterceptor;
import io.muun.apollo.data.net.base.interceptor.IdempotencyKeyInterceptor;
import io.muun.apollo.data.net.base.interceptor.LanguageHeaderInterceptor;
import io.muun.apollo.data.net.base.interceptor.VersionHeaderInterceptor;
import io.muun.apollo.data.os.Configuration;
import io.muun.apollo.data.preferences.AuthRepository;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.external.Globals;
import io.muun.apollo.external.HoustonConfig;
import io.muun.common.Optional;

import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class BaseClient<ServiceT> {

    private final Class<ServiceT> serviceClass;

    private ServiceT service;

    private Optional<String> serverJwt;

    @Inject
    CallAdapterFactory callAdapterFactory;

    @Inject
    AuthRepository authRepository;

    @Inject
    AuthHeaderInterceptor authHeaderInterceptor;

    @Inject
    VersionHeaderInterceptor versionHeaderInterceptor;

    @Inject
    LanguageHeaderInterceptor languageHeaderInterceptor;

    @Inject
    IdempotencyKeyInterceptor idempotencyKeyInterceptor;

    @Inject
    Configuration config;

    @Inject
    HoustonConfig houstonConfig;

    /**
     * Creates a client.
     *
     * @param serviceClass The class of the service that is used by the client.
     */
    protected BaseClient(Class<ServiceT> serviceClass) {
        this.serviceClass = serviceClass;
    }

    /**
     * Creates a retrofit instance of a service.
     */
    protected ServiceT getService() {

        final ObjectMapper jsonMapper = SerializationUtils.JSON_MAPPER;

        // TODO: the second clause is probably not necessary any more
        if (service == null || !authRepository.getServerJwt().equals(serverJwt)) {

            serverJwt = authRepository.getServerJwt();

            service = new Retrofit.Builder()
                    .baseUrl(houstonConfig.getUrl())
                    .client(getHttpClient())
                    .addConverterFactory(ScalarsConverterFactory.create()) // note: before jackson!
                    .addConverterFactory(JacksonConverterFactory.create(jsonMapper))
                    .addCallAdapterFactory(callAdapterFactory)
                    .build()
                    .create(serviceClass);
        }

        return service;
    }

    /**
     * Returns a fully-configured HttpClient.
     */
    @NotNull
    private OkHttpClient getHttpClient() {

        final CertificatePinner certificatePinner = new CertificatePinner.Builder()
                // CN=HOUSTON_DOMAIN
                .add(houstonConfig.getDomain(), houstonConfig.getCertificatePin())
                // CN=Amazon, OU=Server CA 1B
                .add(houstonConfig.getDomain(),
                        "sha256/JSMzqOOrtyOT1kmau6zKhgT676hGgczD5VMdRMyJZFA="
                )
                .build();

        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .certificatePinner(certificatePinner)
                .readTimeout(config.getLong("net.timeoutInSec"), TimeUnit.SECONDS)
                .addInterceptor(versionHeaderInterceptor)
                .addInterceptor(languageHeaderInterceptor)
                .addInterceptor(authHeaderInterceptor)
                .addInterceptor(idempotencyKeyInterceptor);

        if (!isReleaseBuild()) {
            final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY);

            builder.addInterceptor(loggingInterceptor);
        }

        if (!isReleaseBuild() && config.getBoolean("net.interceptors.stetho")) {
            builder.addNetworkInterceptor(new StethoInterceptor());
        }

        if (config.getBoolean("net.interceptors.idempotencyTester")) {
            builder.addInterceptor(new DuplicatingInterceptor());
        }

        return builder.build();
    }

    private boolean isReleaseBuild() {
        return Globals.INSTANCE.getBuildType().equals("release");
    }
}
