package li.biq.sdk

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

internal class BiqliApiClient(private val configuration: BiqliConfiguration) {
    suspend fun resolve(request: MobileOpenRequest): Pair<BiqliAttributionResult, String> {
        val body = BiqliJson.encodeRequest(request)
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return send(request, body)
            } catch (error: RetryableHttpException) {
                lastError = error
            } catch (error: IOException) {
                lastError = error
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                diagnostic(BiqliDiagnostic.RESOLVER_RETRY)
                delay(
                    BASE_RETRY_MILLIS * (1L shl attempt) +
                        Random.nextLong(0, MAX_JITTER_MILLIS + 1),
                )
            }
        }
        throw BiqliException.Transport(lastError)
    }

    private fun send(
        request: MobileOpenRequest,
        body: String,
    ): Pair<BiqliAttributionResult, String> {
        val endpoint = configuration.apiBaseUrl.trimEnd('/') + "/track/open"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${configuration.publishableKey}")
            setRequestProperty("Idempotency-Key", request.eventId)
            setRequestProperty("X-Biq-SDK", "android")
            setRequestProperty("X-Biq-SDK-Version", request.sdkVersion)
            setRequestProperty("X-Biq-Request-Id", request.requestId)
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (status in 200..299) {
                return BiqliJson.decodeResult(response) to response
            }
            if (status == 429 || status >= 500) {
                throw RetryableHttpException(status)
            }
            throw BiqliException.Http(status, BiqliJson.decodeErrorCode(response))
        } finally {
            connection.disconnect()
        }
    }

    private fun diagnostic(event: BiqliDiagnostic) {
        if (configuration.diagnosticsEnabled) {
            runCatching { configuration.diagnosticListener?.onDiagnostic(event) }
        }
    }

    private class RetryableHttpException(status: Int) : IOException("HTTP $status")

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BASE_RETRY_MILLIS = 400L
        private const val MAX_JITTER_MILLIS = 250L
    }
}
