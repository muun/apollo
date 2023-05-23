package io.muun.apollo.presentation.ui.debug

import android.os.AsyncTask
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import rx.subjects.BehaviorSubject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

open class SimpleHttpClient {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        // Big timeout to avoid flakiness in CI (remote kube regtest env)
        .readTimeout(3, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            val request = chain
                .request()
                .newBuilder()
                .addHeader("Authorization", getAuthHeader())
                .build()

            chain.proceed(request)
        }
        .build()

    protected open fun getAuthHeader(): String {
        return "none" // override to set own auth
    }

    protected fun post(
        url: String,
        requestJsonBody: String,
        mediaType: String = "text/plain",
    ): Observable<Response> {

        val subject: BehaviorSubject<Response> = BehaviorSubject.create()

        val requestBody = RequestBody.create(
            MediaType.parse(mediaType),
            requestJsonBody
        )

        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        doInBackground(httpRequest, subject)

        return subject
    }

    protected fun get(url: String): BehaviorSubject<Response> {

        val subject: BehaviorSubject<Response> = BehaviorSubject.create()

        val httpRequest = Request.Builder()
            .url(url)
            .build()

        doInBackground(httpRequest, subject)

        return subject
    }

    private fun doInBackground(
        httpRequest: Request,
        subject: BehaviorSubject<Response>,
    ) {
        AsyncTask.execute {

            Timber.d("HTTP Request: %s", httpRequest.toString())

            try {
                val response = httpClient.newCall(httpRequest).execute()

                Timber.d("HTTP Response: %s", response.toString())

                subject.onNext(response)

            } catch (e: IOException) {
                Timber.e(e)
                subject.onError(e)
            }
        }
    }
}