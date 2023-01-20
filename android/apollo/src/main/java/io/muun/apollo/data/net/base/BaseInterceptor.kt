package io.muun.apollo.data.net.base

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

open class BaseInterceptor : Interceptor {

    protected open fun processRequest(originalRequest: Request): Request {
        return originalRequest
    }

    protected open fun processResponse(originalResponse: Response): Response {
        return originalResponse
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = processRequest(chain.request())
        return processResponse(chain.proceed(request))
    }
}