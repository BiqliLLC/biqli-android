package li.biq.sdk

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal object BiqliJson {
    fun encodeRequest(request: MobileOpenRequest): String = JSONObject().apply {
        put("eventId", request.eventId)
        put("appId", request.appId)
        put("appInstanceId", request.appInstanceId)
        put("platform", "android")
        put("firstOpen", request.firstOpen)
        put("probabilisticAllowed", request.probabilisticAllowed)
        putNullable("handoffToken", request.handoffToken)
        putNullable("deepLink", request.deepLink)
        putNullable("domain", request.domain)
        putNullable("appVersion", request.appVersion)
        put("osVersion", request.osVersion)
        put("sdkVersion", request.sdkVersion)
        put("occurredAt", request.occurredAt)
    }.toString()

    fun encodePendingRequest(request: MobileOpenRequest): String = JSONObject().apply {
        put("eventId", request.eventId)
        put("requestId", request.requestId)
        put("appId", request.appId)
        put("appInstanceId", request.appInstanceId)
        put("firstOpen", request.firstOpen)
        put("probabilisticAllowed", request.probabilisticAllowed)
        putNullable("handoffToken", request.handoffToken)
        putNullable("deepLink", request.deepLink)
        putNullable("domain", request.domain)
        putNullable("appVersion", request.appVersion)
        put("osVersion", request.osVersion)
        put("sdkVersion", request.sdkVersion)
        put("occurredAt", request.occurredAt)
    }.toString()

    fun decodePendingRequest(value: String): MobileOpenRequest? = try {
        val json = JSONObject(value)
        MobileOpenRequest(
            eventId = json.getString("eventId"),
            requestId = json.getString("requestId"),
            appId = json.getString("appId"),
            appInstanceId = json.getString("appInstanceId"),
            firstOpen = json.getBoolean("firstOpen"),
            probabilisticAllowed = json.optBoolean("probabilisticAllowed", false),
            handoffToken = json.optionalString("handoffToken"),
            deepLink = json.optionalString("deepLink"),
            domain = json.optionalString("domain"),
            appVersion = json.optionalString("appVersion"),
            osVersion = json.getString("osVersion"),
            sdkVersion = json.getString("sdkVersion"),
            occurredAt = json.getString("occurredAt"),
        )
    } catch (_: JSONException) {
        null
    }

    fun decodeResult(value: String): BiqliAttributionResult {
        try {
            val json = JSONObject(value)
            val open = json.getJSONObject("open")
            val click = json.optJSONObject("click")
            val link = json.optJSONObject("link")
            val attribution = json.optJSONObject("attribution")
            return BiqliAttributionResult(
                open = BiqliAttributionResult.Open(
                    id = open.optionalString("id"),
                    firstOpen = open.getBoolean("firstOpen"),
                    matchedBy = MatchType.fromWire(open.getString("matchedBy")),
                    confidence = Confidence.fromWire(open.getString("confidence")),
                ),
                click = click?.let { BiqliAttributionResult.Click(it.getString("id")) },
                link = link?.let {
                    BiqliAttributionResult.Link(
                        id = it.getString("id"),
                        shortUrl = it.getString("shortUrl"),
                        route = it.getString("route"),
                    )
                },
                attribution = attribution?.let {
                    BiqliAttributionResult.Attribution(
                        referralCode = it.optionalString("referralCode"),
                        metadata = it.optJSONObject("metadata")?.toMap() ?: emptyMap(),
                        dynamic = it.optJSONObject("dynamic")?.toStringMap() ?: emptyMap(),
                    )
                },
                attributionReceipt = json.optionalString("attributionReceipt"),
                requestId = json.getString("requestId"),
            )
        } catch (error: JSONException) {
            throw BiqliException.InvalidResponse(error)
        }
    }

    fun decodeErrorCode(value: String): String? = try {
        JSONObject(value).optJSONObject("error")?.optionalString("code")
    } catch (_: JSONException) {
        null
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.toStringMap(): Map<String, String> = keys().asSequence()
        .associateWith { key -> optString(key) }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence()
        .associateWith { key -> unwrap(opt(key)) }

    private fun unwrap(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> (0 until value.length()).map { index -> unwrap(value.opt(index)) }
        else -> value
    }
}
