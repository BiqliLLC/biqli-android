package li.biq.sdk

import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal class BiqliClient(private val environment: SdkEnvironment) {
    private val configuration = environment.configuration
    private val state = BiqliStateStore(environment.context, configuration.appId)
    private val api = BiqliApiClient(configuration)
    private val installReferrer = BiqliInstallReferrer(environment.context)
    private val resolutionMutex = Mutex()

    fun handleAppLink(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val url = intent.data ?: return false
        if (!url.scheme.equals("https", ignoreCase = true) || url.host.isNullOrBlank()) return false
        state.savePendingDeepLink(url.toString())
        diagnostic(BiqliDiagnostic.APP_LINK_CAPTURED)
        return true
    }

    suspend fun resolve(intent: Intent?, consentGranted: Boolean): BiqliAttributionResult =
        resolutionMutex.withLock {
            if (configuration.consentRequired && !consentGranted) {
                throw BiqliException.ConsentRequired()
            }
            handleAppLink(intent)

            state.pendingRequest()?.let { pending ->
                if (pending.appId == configuration.appId) {
                    return@withLock resolveRequest(pending)
                }
            }

            val deepLink = state.pendingDeepLink()
            val firstOpen = state.isFirstOpenPending()
            if (!firstOpen && deepLink == null) {
                state.cachedFirstResult()?.let { return@withLock BiqliJson.decodeResult(it) }
            }

            var token = state.pendingToken()
            val referrerAlreadyRead = state.hasReadInstallReferrer()
            if (firstOpen && deepLink == null && token == null && !referrerAlreadyRead) {
                when (val result = installReferrer.read()) {
                    is InstallReferrerResult.Found -> {
                        token = result.token
                        state.markInstallReferrerRead(result.token)
                        diagnostic(BiqliDiagnostic.INSTALL_REFERRER_FOUND)
                    }
                    InstallReferrerResult.Empty -> {
                        state.markInstallReferrerRead(null)
                        diagnostic(BiqliDiagnostic.INSTALL_REFERRER_EMPTY)
                    }
                    InstallReferrerResult.TerminalUnavailable -> {
                        state.markInstallReferrerRead(null)
                        diagnostic(BiqliDiagnostic.INSTALL_REFERRER_UNAVAILABLE)
                    }
                    InstallReferrerResult.Retryable ->
                        diagnostic(BiqliDiagnostic.INSTALL_REFERRER_RETRYABLE)
                }
            }

            val request = newRequest(firstOpen, token, deepLink)
            state.savePendingRequest(request)
            resolveRequest(request)
        }

    private suspend fun resolveRequest(request: MobileOpenRequest): BiqliAttributionResult {
        try {
            val (result, rawResult) = api.resolve(request)
            state.rememberLastResult(rawResult)
            state.clearPendingRequest(request.eventId)
            state.clearPendingDeepLink(request.deepLink)
            if (request.handoffToken != null && result.open.matchedBy == MatchType.NONE) {
                state.clearPendingToken()
            }
            if (
                request.firstOpen &&
                (
                    result.open.matchedBy != MatchType.NONE ||
                        (request.deepLink == null && state.hasReadInstallReferrer())
                    )
            ) {
                state.completeFirstOpen(rawResult)
            }
            diagnostic(
                if (result.open.matchedBy == MatchType.NONE) {
                    BiqliDiagnostic.RESOLVER_NO_MATCH
                } else {
                    BiqliDiagnostic.RESOLVER_MATCHED
                },
            )
            return result
        } catch (error: BiqliException.Http) {
            if (error.status !in listOf(408, 425, 429) && error.status < 500) {
                state.clearPendingRequest(request.eventId)
                if (request.handoffToken != null && error.code == "resource_not_found") {
                    state.clearPendingToken()
                }
                if (error.code == "handoff_mismatch") {
                    state.clearPendingDeepLink(request.deepLink)
                }
            }
            throw error
        }
    }

    private fun newRequest(
        firstOpen: Boolean,
        token: String?,
        deepLink: String?,
    ): MobileOpenRequest {
        val context = environment.context
        @Suppress("DEPRECATION")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return MobileOpenRequest(
            eventId = "open_${compactUuid()}",
            requestId = "req_${compactUuid()}",
            appId = configuration.appId,
            appInstanceId = state.appInstanceId(),
            firstOpen = firstOpen,
            handoffToken = token,
            deepLink = deepLink,
            domain = deepLink?.let { Uri.parse(it).host },
            appVersion = appVersion,
            osVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkVersion = Biqli.SDK_VERSION,
            occurredAt = iso8601Now(),
        )
    }

    private fun diagnostic(event: BiqliDiagnostic) {
        if (configuration.diagnosticsEnabled) {
            runCatching { configuration.diagnosticListener?.onDiagnostic(event) }
        }
    }

    companion object {
        private fun compactUuid(): String = UUID.randomUUID().toString().replace("-", "")

        private fun iso8601Now(): String = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US,
        ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    }
}
