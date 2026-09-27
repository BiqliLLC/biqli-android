# Biqli Android SDK

The Kotlin-first SDK handles verified Android App Links and exact deferred attribution through Google Play Install Referrer. It supports Android API 23+, Kotlin coroutines, a Java-compatible callback API, process-safe pending requests, and idempotent retries.

Source: [github.com/BiqliLLC/biqli-android](https://github.com/BiqliLLC/biqli-android)  
Issues: [github.com/BiqliLLC/biqli-android/issues](https://github.com/BiqliLLC/biqli-android/issues)

## Install

During local development, include the `:biqli` module. Published customers will use the signed Maven coordinate:

```kotlin
dependencies {
    implementation("li.biq:biqli-android:1.0.0")
}
```

The SDK transitively includes Google's Install Referrer 2.2 library and requests only `android.permission.INTERNET`. It does not request Advertising ID, location, contacts, or storage permissions.

The app-instance ID, pending opaque handoff, idempotent request, and cached result are stored with Android Keystore-backed encrypted preferences. Raw Play referrer strings are never stored.

## Configure

Use the public mobile app ID and `biqli_mobile_pk_...` key shown in the Biqli Mobile Apps settings. The publishable key identifies and rate-limits the app; it is not a secret and grants no workspace-management access.

```kotlin
Biqli.configure(
    context = applicationContext,
    appId = "biq_mapp_xxx",
    publishableKey = "biqli_mobile_pk_xxx",
)
```

Configure once before resolving, normally in `Application.onCreate` or the first activity. Delayed configuration is supported.

## Add the verified App Link

Add every Biqli custom domain claimed by this app to the launcher activity:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="go.example.com" android:pathPrefix="/" />
</intent-filter>
```

In Biqli, attach the same domain and Android package to the Mobile App and add every production/upload signing SHA-256 certificate fingerprint. Biqli then serves:

```text
https://go.example.com/.well-known/assetlinks.json
```

The file must be reachable over HTTPS without redirects. The Android package name and certificate fingerprint must exactly match the installed build.

## Resolve in Kotlin

Pass the launch intent. This handles a direct App Link when installed, or reads Google Play Install Referrer once after a store install.

```kotlin
lifecycleScope.launch {
    try {
        val result = Biqli.resolveAttribution(intent)
        result.attribution?.referralCode?.let { referral ->
            // Send referral + result.click.id to your trusted backend.
            // Let the backend validate eligibility and issue any reward.
        }
        result.link?.route?.let(router::open)
    } catch (error: BiqliException) {
        // Do not block app startup. The next cold launch can retry pending work.
    }
}
```

Forward new App Links from a `singleTop` activity:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    lifecycleScope.launch { Biqli.resolveAttribution(intent) }
}
```

Call `resolveAttribution` on each cold launch. Once first-install attribution reaches a terminal result, the SDK returns its cached result without another network event when there is no new App Link. An offline pending request retains the same event and idempotency key across process recreation.

Signed attribution receipts last 10 minutes and are returned only on the live resolver response. The cached referral result intentionally removes the receipt, so submit the live receipt to the trusted backend immediately and never treat a cached result as fresh proof.

## Java callback

```java
Biqli.configure(getApplicationContext(), "biq_mapp_xxx", "biqli_mobile_pk_xxx");
Biqli.resolveAttribution(getIntent(), true, new BiqliAttributionCallback() {
  @Override public void onSuccess(BiqliAttributionResult result) {
    // Inspect result.getAttribution() and result.getLink().
  }
  @Override public void onError(BiqliException error) {
    // Keep startup non-blocking and retry on a later launch.
  }
});
```

## Install Referrer behavior

- The SDK connects only during the pending first-open window, reads once, extracts only `biqli_token`, and closes the connection.
- The raw Google Play referrer is never persisted or sent to Biqli.
- A found opaque token is persisted until the resolver accepts it, allowing a later cold launch to recover from an offline first launch.
- A missing Play Store, unsupported API, permission/developer error, or empty referrer becomes a clean no-match and does not block startup.
- Temporary service disconnects and timeouts remain retryable for up to 24 hours.
- Reinstalling normally creates a new app data store and therefore a new app instance eligible for first-install attribution.

## Consent and diagnostics

Set `consentRequired = true` if the customer app must collect consent before attribution. Until `consentGranted = true` is supplied, the SDK does not query Install Referrer or contact Biqli.

Diagnostics are disabled by default. When enabled, the listener receives bounded status enums only—never the referral token, raw URL, publishable key, or response payload.

## Local checks versus real store testing

Android Studio/emulator testing can verify App Link intent handling, API requests, no-match behavior, offline persistence, and callback behavior. The actual deferred path requires a Google Play build (an internal test track is sufficient), because only Google Play can supply the real Install Referrer value.

See [`sample`](sample) for a minimal activity. Supply these Gradle properties locally:

```properties
BIQLI_SAMPLE_APP_ID=biq_mapp_xxx
BIQLI_SAMPLE_PUBLISHABLE_KEY=biqli_mobile_pk_xxx
BIQLI_SAMPLE_DOMAIN=go.example.com
BIQLI_SAMPLE_APPLICATION_ID=com.example.app
```

Never commit a workspace secret API key. The sample uses only the public mobile key.

## License

Copyright 2026 Biqli LLC. Licensed under the [Apache License 2.0](LICENSE).
