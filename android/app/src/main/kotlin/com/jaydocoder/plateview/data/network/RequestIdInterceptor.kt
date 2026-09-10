package com.jaydocoder.plateview.data.network

import java.io.IOException
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response

class RequestIdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestId = chain.request().header(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()
        val request = chain.request().newBuilder()
            .header(REQUEST_ID_HEADER, requestId)
            .build()
        return try {
            chain.proceed(request)
        } catch (exception: IOException) {
            throw NetworkRequestException(requestId, exception)
        }
    }

    private companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
    }
}
