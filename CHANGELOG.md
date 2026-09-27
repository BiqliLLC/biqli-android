# Changelog

## 1.0.0 - 2026-09-27

- Add verified Android App Link capture.
- Add one-time Google Play Install Referrer retrieval and opaque-token parsing.
- Add first-open state, cached terminal attribution, and process-safe idempotent retries.
- Protect the app instance, opaque handoff, pending request, and cached result with Android Keystore-backed encrypted preferences.
- Keep short-lived attribution receipts out of the long-lived result cache.
- Add coroutine and Java callback APIs.
- Add consent gating, bounded diagnostics, sample app, and local Maven staging publication.
