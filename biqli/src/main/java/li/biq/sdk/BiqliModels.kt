package li.biq.sdk

import android.content.Context

data class BiqliConfiguration @JvmOverloads constructor(
    val appId: String,
    val publishableKey: String,
    val apiBaseUrl: String = "https://biq.li/api/v1",
    val consentRequired: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    val diagnosticListener: BiqliDiagnosticListener? = null,
)

data class BiqliAttributionResult(
    val open: Open,
    val click: Click?,
    val link: Link?,
    val attribution: Attribution?,
    val attributionReceipt: String?,
    val requestId: String,
) {
    data class Open(
        val id: String?,
        val firstOpen: Boolean,
        val matchedBy: MatchType,
        val confidence: Confidence,
    )

    data class Click(val id: String)

    data class Link(
        val id: String,
        val shortUrl: String,
        val route: String,
    )

    data class Attribution(
        val referralCode: String?,
        val metadata: Map<String, Any?>,
        val dynamic: Map<String, String>,
    )
}

enum class MatchType(val wireValue: String) {
    EXACT_APP_LINK("exact_app_link"),
    EXACT_INSTALL_REFERRER("exact_install_referrer"),
    EXACT_HANDOFF("exact_handoff"),
    PROBABILISTIC("probabilistic"),
    NONE("none");

    companion object {
        internal fun fromWire(value: String): MatchType =
            entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

enum class Confidence(val wireValue: String) {
    EXACT("exact"),
    PROBABILISTIC("probabilistic"),
    NONE("none");

    companion object {
        internal fun fromWire(value: String): Confidence =
            entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

enum class BiqliDiagnostic {
    APP_LINK_CAPTURED,
    INSTALL_REFERRER_FOUND,
    INSTALL_REFERRER_EMPTY,
    INSTALL_REFERRER_UNAVAILABLE,
    INSTALL_REFERRER_RETRYABLE,
    RESOLVER_RETRY,
    RESOLVER_MATCHED,
    RESOLVER_NO_MATCH,
}

fun interface BiqliDiagnosticListener {
    fun onDiagnostic(event: BiqliDiagnostic)
}

interface BiqliAttributionCallback {
    fun onSuccess(result: BiqliAttributionResult)
    fun onError(error: BiqliException)
}

sealed class BiqliException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured : BiqliException("Call Biqli.configure before using the SDK.")
    class ConsentRequired : BiqliException("Attribution consent is required.")
    class InvalidConfiguration(message: String) : BiqliException(message)
    class InvalidResponse(cause: Throwable? = null) : BiqliException("The resolver returned an invalid response.", cause)
    class Http(val status: Int, val code: String?) : BiqliException("The resolver returned HTTP $status${code?.let { " ($it)" } ?: ""}.")
    class Transport(cause: Throwable? = null) : BiqliException("The attribution request could not be completed.", cause)
}

internal data class MobileOpenRequest(
    val eventId: String,
    val requestId: String,
    val appId: String,
    val appInstanceId: String,
    val firstOpen: Boolean,
    val probabilisticAllowed: Boolean = false,
    val handoffToken: String?,
    val deepLink: String?,
    val domain: String?,
    val appVersion: String?,
    val osVersion: String,
    val sdkVersion: String,
    val occurredAt: String,
)

internal data class SdkEnvironment(
    val context: Context,
    val configuration: BiqliConfiguration,
)
