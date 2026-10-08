package com.example.android.data.remote.client

import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.interceptor.AuthInterceptor
import com.example.android.domain.repository.AuthRepository
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private fun isEmulator(): Boolean {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")
    }

    private val defaultBaseUrl: String
        get() = if (isEmulator()) "http://10.0.2.2:8000/" else "http://127.0.0.1:8000/"

    fun createBackendService(
        authRepository: AuthRepository,
        baseUrl: String = defaultBaseUrl
    ): BackendApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }

        val authInterceptor = AuthInterceptor(authRepository)

        // Resilient fallback interceptor: if 10.0.2.2 fails, try 127.0.0.1 (or vice versa)
        val hostFallbackInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            try {
                chain.proceed(request)
            } catch (e: java.io.IOException) {
                val currentHost = request.url.host
                val fallbackHost = if (currentHost == "10.0.2.2") "127.0.0.1" else if (currentHost == "127.0.0.1") "10.0.2.2" else null
                if (fallbackHost != null) {
                    val newUrl = request.url.newBuilder().host(fallbackHost).build()
                    val newRequest = request.newBuilder().url(newUrl).build()
                    chain.proceed(newRequest)
                } else {
                    throw e
                }
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(hostFallbackInterceptor)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()

        val gson = GsonBuilder()
            .setLenient()
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(BackendApiService::class.java)
    }
}
