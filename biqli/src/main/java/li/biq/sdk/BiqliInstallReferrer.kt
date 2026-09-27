package li.biq.sdk

import android.content.Context
import android.net.Uri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal sealed interface InstallReferrerResult {
    data class Found(val token: String) : InstallReferrerResult
    data object Empty : InstallReferrerResult
    data object TerminalUnavailable : InstallReferrerResult
    data object Retryable : InstallReferrerResult
}

internal class BiqliInstallReferrer(private val context: Context) {
    suspend fun read(): InstallReferrerResult = withTimeoutOrNull(TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val client = InstallReferrerClient.newBuilder(context).build()
            val completed = AtomicBoolean(false)

            fun finish(result: InstallReferrerResult) {
                if (completed.compareAndSet(false, true)) {
                    runCatching { client.endConnection() }
                    continuation.resume(result)
                }
            }

            continuation.invokeOnCancellation {
                if (completed.compareAndSet(false, true)) {
                    runCatching { client.endConnection() }
                }
            }

            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                val raw = runCatching { client.installReferrer.installReferrer }
                                    .getOrNull()
                                val token = raw?.let(::extractToken)
                                finish(token?.let { InstallReferrerResult.Found(it) } ?: InstallReferrerResult.Empty)
                            }
                            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE ->
                                finish(InstallReferrerResult.Retryable)
                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                            InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
                            InstallReferrerClient.InstallReferrerResponse.PERMISSION_ERROR ->
                                finish(InstallReferrerResult.TerminalUnavailable)
                            else -> finish(InstallReferrerResult.Retryable)
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        finish(InstallReferrerResult.Retryable)
                    }
                })
            } catch (_: SecurityException) {
                finish(InstallReferrerResult.TerminalUnavailable)
            } catch (_: RuntimeException) {
                finish(InstallReferrerResult.Retryable)
            }
        }
    } ?: InstallReferrerResult.Retryable

    companion object {
        private const val TIMEOUT_MILLIS = 3_500L
        private val TOKEN_PATTERN = Regex("^bqmh_[A-Za-z0-9_-]{43}$")

        internal fun extractToken(raw: String): String? {
            val token = runCatching {
                Uri.parse("https://localhost/?$raw").getQueryParameter("biqli_token")
            }.getOrNull()
            return token?.takeIf { TOKEN_PATTERN.matches(it) }
        }
    }
}
