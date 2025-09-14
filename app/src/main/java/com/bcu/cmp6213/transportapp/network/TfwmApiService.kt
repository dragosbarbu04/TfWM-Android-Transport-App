package com.bcu.cmp6213.transportapp.network

// Import BuildConfig to access API keys stored there securely.
import com.bcu.cmp6213.transportapp.BuildConfig
// Import OkHttp classes for network client configuration and response handling.
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
// Import Retrofit classes for defining the API interface and making requests.
import retrofit2.Response
import retrofit2.Retrofit
// Import Retrofit HTTP method annotations.
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
// Import TimeUnit for specifying timeout durations.
import java.util.concurrent.TimeUnit

/**
 * Retrofit service interface for interacting with the Transport for West Midlands (TfWM) API.
 * This interface defines the HTTP operations (like GET requests) and their parameters
 * for accessing specific API endpoints.
 */
interface TfwmApiService {

    /**
     * Defines a suspend function to download the GTFS (General Transit Feed Specification) zip file from the TfWM API.
     * This function will be called from a coroutine.
     *
     * The `@Streaming` annotation is crucial here. It indicates that the response body should be
     * streamed directly to the file system rather than being loaded entirely into memory.
     * This is essential for handling large files efficiently and preventing OutOfMemoryErrors.
     *
     * The `@GET` annotation specifies that this is an HTTP GET request and provides the relative URL
     * path for the GTFS zip file endpoint on the TfWM API.
     *
     * @param appId The application ID required for authenticating with the TfWM API.
     * Defaults to the value stored in `BuildConfig.TFWM_APP_ID`.
     * @param appKey The application key required for authenticating with the TfWM API.
     * Defaults to the value stored in `BuildConfig.TFWM_APP_KEY`.
     * @return A Retrofit `Response` object containing the `ResponseBody`. The `ResponseBody`
     * provides access to the raw byte stream of the downloaded file, which can then be
     * written to disk.
     */
    @Streaming // Ensures the response body is streamed, vital for large file downloads.
    @GET("gtfs/tfwm_gtfs.zip") // The specific endpoint path for the TfWM GTFS zip file.
    suspend fun downloadGtfsZip(
        // @Query annotation adds these parameters to the URL query string.
        // Default values are pulled from BuildConfig, keeping keys out of version control.
        @Query("app_id") appId: String = BuildConfig.TFWM_APP_ID,
        @Query("app_key") appKey: String = BuildConfig.TFWM_APP_KEY
    ): Response<ResponseBody>

    /**
     * Companion object to provide a factory method for creating instances of [TfwmApiService].
     * This follows a common pattern for setting up Retrofit services.
     */
    companion object {
        // The base URL for all requests made by this TfWM API service.
        // Note: The initial project setup used HTTP. Network Security Configuration was added
        // to allow cleartext traffic specifically for this domain.
        private const val BASE_URL = "http://api.tfwm.org.uk/"

        // Configures a custom OkHttpClient instance.
        // OkHttp is the underlying HTTP client used by Retrofit.
        // Custom configuration is necessary here to set longer timeouts,
        // which are often required for downloading large files like GTFS feeds.
        private val okHttpClient = OkHttpClient.Builder()
            // Sets the connection timeout. If a connection isn't established within this period, it times out.
            .connectTimeout(1, TimeUnit.MINUTES)
            // Sets the read timeout. If data isn't received from the server within this period after connection, it times out.
            // This is increased significantly (5 minutes) because GTFS zip files can be large.
            .readTimeout(5, TimeUnit.MINUTES)
            // Sets the write timeout. If data isn't sent to the server within this period, it times out.
            .writeTimeout(1, TimeUnit.MINUTES)
            .build() // Builds the OkHttpClient instance with the specified configurations.

        /**
         * Factory method to create and return an instance of [TfwmApiService].
         * It configures Retrofit with the base URL and the custom OkHttpClient.
         *
         * @return A new instance of [TfwmApiService].
         */
        fun create(): TfwmApiService {
            // Build a Retrofit instance.
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL) // Set the base URL for the API.
                .client(okHttpClient) // Use the custom OkHttpClient with extended timeouts.
                // No converter factory (e.g., GsonConverterFactory or MoshiConverterFactory) is added here.
                // This is because this service is designed to download a raw file (ResponseBody for streaming)
                // and not to parse JSON/XML responses into Kotlin objects directly via Retrofit converters.
                .build()

            // Create and return an implementation of the TfwmApiService interface.
            return retrofit.create(TfwmApiService::class.java)
        }
    }
}