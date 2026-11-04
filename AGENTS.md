# ShuttleRef Android

- Kotlin/Jetpack Compose foundation client, not full iOS feature parity. App namespace `com.yahabib.shuttleref`, `.demo` suffix, existing versions and Tuya SDK 7.11.0 are preserved.
- Use JDK 17, Android SDK 35 / Build Tools 35.0.0 and the checked-in Gradle 8.11.1 wrapper. Run `bash scripts/check.sh`: fresh JVM tests, real C DP protocol replay and demo APK assembly. No skipped replay accepted by this entry.
- `tests/support` is a pinned C oracle snapshot; source hashes are recorded under `docs/migration`. Canonical C score rules live in `shuttleref-edge`; coordinate updates rather than silently diverging.
- Private default path is `config/local`, optionally overridden with Gradle `-PshuttlerefPrivateDir=/private/path`. Never copy actual credentials, security AAR, JKS/keystore, APK/AAB, SDK caches or login data into Git.
- Demo builds need no Tuya credentials/AAR and do not make production account requests. Tuya builds require the existing Android SDK app identity and privately supplied matching security component. Backend cloud keys never belong in APKs.
- Preserve ticket verification, identity/host context fences, sequence persistence, device-ACK authority and unknown-result/no-resubmit behavior. Do not fabricate login success or scoring confirmation.
- Business code in `app/src` and `core/src` is unchanged by migration. SDK APIs should be verified against the actual pinned local SDK, not guessed or replaced with new endpoints.
- Compile-only checks do not prove account login, real device/BLE/DP exchange, shared iOS homes or production readiness. No install/publish/device action without the applicable authorization.
