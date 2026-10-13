# Build and toolchain

## Version matrix

These versions are pinned in `build.gradle.kts` (root) and
`app/build.gradle.kts`, and in `gradle/wrapper/gradle-wrapper.properties`. They
are chosen to be mutually compatible.

| Component | Version |
|---|---|
| JDK | 17 |
| Gradle | 8.7 |
| Android Gradle Plugin (AGP) | 8.5.2 |
| Kotlin | 1.9.24 |
| KSP | 1.9.24-1.0.20 |
| compileSdk | 34 |
| targetSdk | 34 |
| minSdk | 24 (Android 7.0) |
| Android Build Tools (CI) | 34.0.0 |
| Room | 2.6.1 |
| BouncyCastle | 1.78.1 |

Do not bump AGP, Gradle or Kotlin independently — check the compatibility matrix
before changing any of them.

## The Gradle wrapper

The `gradle/wrapper/gradle-wrapper.jar` is a binary and is intentionally **not**
committed. Two consequences:

- **CI** does not use `./gradlew`. The workflow installs Gradle 8.7 directly via
  `gradle/actions/setup-gradle` and runs `gradle <task>`.
- **Locally**, if you want `./gradlew`, run `gradle wrapper` once to generate the
  wrapper JAR, then use `./gradlew` as normal.

## Building locally

```bash
# Requires JDK 17 on PATH and Gradle 8.7 (or generate the wrapper first)
gradle testDebugUnitTest   # run JVM unit tests
gradle assembleDebug       # build the debug APK
gradle lint                # optional: Android lint

# APK output:
#   app/build/outputs/apk/debug/app-debug.apk
#   (and, because ABI splits are enabled, per-ABI and universal APKs)
```

## Building in CI

Push to `main`/`master`, or open the repository's **Actions** tab → **Android
Build** → **Run workflow**. The workflow:

1. checks out the repo;
2. sets up JDK 17 and the Android SDK;
3. installs `platforms;android-34` and `build-tools;34.0.0`;
4. installs Gradle 8.7;
5. runs `testDebugUnitTest`;
6. runs `assembleDebug`;
7. inspects every APK's native libraries and `aapt` badging and prints the ABIs;
8. stages `WiFiChat-Mesh-armv7.apk`, `-arm64.apk`, `-universal.apk`;
9. uploads each as a downloadable artifact, plus the test report.

No paid runners, no paid APIs, no local Android Studio.

## Troubleshooting

- **`SDK location not found`** — CI sets it up automatically; locally, set
  `ANDROID_SDK_ROOT` or add `local.properties` with `sdk.dir=...`.
- **`Unsupported class file major version`** — you are not on JDK 17.
- **Room schema errors** — the schema is exported to `app/schemas/`; if you
  change an entity, bump the Room version and add a `Migration` in
  `data/db/Migrations.kt`.
- **`Could not resolve ...`** — check network access to `google()` and
  `mavenCentral()` repositories.

## Release signing (not configured)

Only a **debug** APK is produced. To ship a release build you must create a
keystore, reference it from a `signingConfigs` block, and supply the credentials
through CI secrets. **Never commit a keystore or its passwords.** This is left
as a deliberate, documented step rather than being wired up with placeholder
secrets.
