package me.rerere.rikkahub.data.ai

import okhttp3.Interceptor
import okhttp3.Response

class AIRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

//        if (host == "api.siliconflow.cn") {
//            request = processSiliconCloudRequest(request)
//        }

        return chain.proceed(request)
    }
}