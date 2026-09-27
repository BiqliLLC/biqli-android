package li.biq.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicReference

object Biqli {
    const val SDK_VERSION = "1.0.0"

    private val client = AtomicReference<BiqliClient?>()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun configure(context: Context, configuration: BiqliConfiguration) {
        validate(configuration)
        client.set(
            BiqliClient(
                SdkEnvironment(
                    context = context.applicationContext,
                    configuration = configuration,
                ),
            ),
        )
    }

    @JvmStatic
    @JvmOverloads
    fun configure(
        context: Context,
        appId: String,
        publishableKey: String,
        apiBaseUrl: String = "https://biq.li/api/v1",
        consentRequired: Boolean = false,
        diagnosticsEnabled: Boolean = false,
    ) {
        configure(
            context,
            BiqliConfiguration(
                appId = appId,
                publishableKey = publishableKey,
                apiBaseUrl = apiBaseUrl,
                consentRequired = consentRequired,
                diagnosticsEnabled = diagnosticsEnabled,
            ),
        )
    }

    @JvmStatic
    fun handleAppLink(intent: Intent?): Boolean = requireClient().handleAppLink(intent)

    @JvmStatic
    @JvmOverloads
    suspend fun resolveAttribution(
        intent: Intent? = null,
        consentGranted: Boolean = true,
    ): BiqliAttributionResult = requireClient().resolve(intent, consentGranted)

    @JvmStatic
    @JvmOverloads
    fun attributionFlow(
        intent: Intent? = null,
        consentGranted: Boolean = true,
    ): Flow<BiqliAttributionResult> = flow {
        emit(requireClient().resolve(intent, consentGranted))
    }

    @JvmStatic
    @JvmOverloads
    fun resolveAttribution(
        intent: Intent?,
        consentGranted: Boolean = true,
        callback: BiqliAttributionCallback,
    ) {
        val configuredClient = requireClient()
        callbackScope.launch {
            try {
                val result = configuredClient.resolve(intent, consentGranted)
                Handler(Looper.getMainLooper()).post {
                    callback.onSuccess(result)
                }
            } catch (error: BiqliException) {
                Handler(Looper.getMainLooper()).post {
                    callback.onError(error)
                }
            } catch (error: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    callback.onError(BiqliException.Transport(error))
                }
            }
        }
    }

    private fun requireClient(): BiqliClient = client.get() ?: throw BiqliException.NotConfigured()

    private fun validate(configuration: BiqliConfiguration) {
        val api = runCatching { Uri.parse(configuration.apiBaseUrl) }.getOrNull()
        if (!MOBILE_APP_ID.matches(configuration.appId)) {
            throw BiqliException.InvalidConfiguration("appId must be a valid Biqli mobile app ID.")
        }
        if (!MOBILE_KEY.matches(configuration.publishableKey)) {
            throw BiqliException.InvalidConfiguration("publishableKey must be a Biqli mobile key.")
        }
        if (
            api?.scheme?.equals("https", ignoreCase = true) != true ||
            api.host.isNullOrBlank() ||
            api.userInfo != null ||
            api.port !in listOf(-1, 443) ||
            api.query != null ||
            api.fragment != null
        ) {
            throw BiqliException.InvalidConfiguration("apiBaseUrl must be an HTTPS base URL without a query or fragment.")
        }
    }

    private val MOBILE_APP_ID = Regex("^biq_mapp_[0-9A-HJKMNP-TV-Z]{26}$")
    private val MOBILE_KEY = Regex("^biqli_mobile_pk_[A-Za-z0-9_-]{64}$")
}
