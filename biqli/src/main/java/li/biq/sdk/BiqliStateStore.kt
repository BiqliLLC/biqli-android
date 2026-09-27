package li.biq.sdk

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.GeneralSecurityException
import java.util.UUID

internal class BiqliStateStore(context: Context, appId: String) {
    private val preferences = encryptedPreferences(
        context,
        "li.biq.sdk.${sha256(appId).take(16)}",
    )

    fun appInstanceId(): String = synchronized(this) {
        preferences.getString(APP_INSTANCE, null) ?: run {
            val value = "install_${UUID.randomUUID().toString().replace("-", "")}" 
            preferences.edit().putString(APP_INSTANCE, value).commit()
            value
        }
    }

    fun isFirstOpenPending(now: Long = System.currentTimeMillis()): Boolean = synchronized(this) {
        if (preferences.getBoolean(FIRST_OPEN_COMPLETE, false)) return false
        val started = preferences.getLong(FIRST_OPEN_STARTED, 0L)
        if (started == 0L) {
            preferences.edit().putLong(FIRST_OPEN_STARTED, now).apply()
            return true
        }
        if (now - started >= FIRST_OPEN_WINDOW_MILLIS) {
            preferences.edit().putBoolean(FIRST_OPEN_COMPLETE, true).apply()
            return false
        }
        true
    }

    fun completeFirstOpen(resultJson: String) {
        preferences.edit()
            .putBoolean(FIRST_OPEN_COMPLETE, true)
            .putString(FIRST_RESULT, withoutReceipt(resultJson))
            .remove(PENDING_TOKEN)
            .apply()
    }

    fun cachedFirstResult(): String? = preferences.getString(FIRST_RESULT, null)

    fun rememberLastResult(resultJson: String) {
        preferences.edit().putString(LAST_RESULT, withoutReceipt(resultJson)).apply()
    }

    fun lastResult(): String? = preferences.getString(LAST_RESULT, null)

    fun pendingDeepLink(): String? = preferences.getString(PENDING_DEEP_LINK, null)

    fun savePendingDeepLink(url: String) {
        preferences.edit().putString(PENDING_DEEP_LINK, url).apply()
    }

    fun clearPendingDeepLink(expected: String?) {
        if (expected == null || pendingDeepLink() == expected) {
            preferences.edit().remove(PENDING_DEEP_LINK).apply()
        }
    }

    fun hasReadInstallReferrer(): Boolean = preferences.getBoolean(REFERRER_READ, false)

    fun markInstallReferrerRead(token: String?) {
        preferences.edit()
            .putBoolean(REFERRER_READ, true)
            .apply {
                if (token == null) remove(PENDING_TOKEN) else putString(PENDING_TOKEN, token)
            }
            .apply()
    }

    fun pendingToken(): String? = preferences.getString(PENDING_TOKEN, null)

    fun clearPendingToken() {
        preferences.edit().remove(PENDING_TOKEN).apply()
    }

    fun pendingRequest(): MobileOpenRequest? = preferences.getString(PENDING_REQUEST, null)
        ?.let(BiqliJson::decodePendingRequest)

    fun savePendingRequest(request: MobileOpenRequest) {
        preferences.edit()
            .putString(PENDING_REQUEST, BiqliJson.encodePendingRequest(request))
            .commit()
    }

    fun clearPendingRequest(eventId: String) {
        if (pendingRequest()?.eventId == eventId) {
            preferences.edit().remove(PENDING_REQUEST).apply()
        }
    }

    companion object {
        private const val APP_INSTANCE = "app_instance_id"
        private const val FIRST_OPEN_COMPLETE = "first_open_complete"
        private const val FIRST_OPEN_STARTED = "first_open_started_at"
        private const val FIRST_RESULT = "first_result"
        private const val LAST_RESULT = "last_result"
        private const val PENDING_DEEP_LINK = "pending_deep_link"
        private const val REFERRER_READ = "install_referrer_read"
        private const val PENDING_TOKEN = "pending_handoff_token"
        private const val PENDING_REQUEST = "pending_open_request"
        private const val FIRST_OPEN_WINDOW_MILLIS = 24L * 60L * 60L * 1000L

        private fun withoutReceipt(resultJson: String): String = runCatching {
            JSONObject(resultJson)
                .put("attributionReceipt", JSONObject.NULL)
                .toString()
        }.getOrDefault(resultJson)

        @Suppress("DEPRECATION")
        private fun encryptedPreferences(context: Context, fileName: String): SharedPreferences {
            fun create(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    fileName,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }

            return try {
                create()
            } catch (_: GeneralSecurityException) {
                context.deleteSharedPreferences(fileName)
                create()
            } catch (_: IOException) {
                context.deleteSharedPreferences(fileName)
                create()
            }
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
