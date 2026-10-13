# Third-party licenses

WiFiChat Mesh's own source is MIT-licensed (see `LICENSE`). It depends on the
following third-party components, each under its own license. All are
permissive and compatible with a free, open, no-cost application.

## Runtime dependencies

| Dependency | Version | License |
|---|---|---|
| AndroidX Core KTX | 1.13.1 | Apache License 2.0 |
| AndroidX AppCompat | 1.7.0 | Apache License 2.0 |
| Material Components for Android | 1.12.0 | Apache License 2.0 |
| AndroidX ConstraintLayout | 2.1.4 | Apache License 2.0 |
| AndroidX RecyclerView | 1.3.2 | Apache License 2.0 |
| AndroidX SwipeRefreshLayout | 1.1.0 | Apache License 2.0 |
| AndroidX Fragment KTX | 1.8.2 | Apache License 2.0 |
| AndroidX Activity KTX | 1.9.1 | Apache License 2.0 |
| AndroidX Navigation (Fragment + UI KTX) | 2.7.7 | Apache License 2.0 |
| AndroidX Lifecycle (ViewModel/LiveData/Runtime/Service KTX) | 2.8.4 | Apache License 2.0 |
| Kotlinx Coroutines (Android) | 1.8.1 | Apache License 2.0 |
| AndroidX Room (runtime, ktx, compiler) | 2.6.1 | Apache License 2.0 |
| BouncyCastle `bcprov-jdk18on` | 1.78.1 | MIT License (Bouncy Castle License) |

## Test dependencies

| Dependency | Version | License |
|---|---|---|
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| Kotlinx Coroutines Test | 1.8.1 | Apache License 2.0 |
| Google Truth | 1.4.2 | Apache License 2.0 |
| AndroidX Test Ext JUnit | 1.2.1 | Apache License 2.0 |
| AndroidX Test Espresso Core | 3.6.1 | Apache License 2.0 |
| AndroidX Room Testing | 2.6.1 | Apache License 2.0 |

## Build tooling

| Tool | Version | License |
|---|---|---|
| Android Gradle Plugin | 8.5.2 | Apache License 2.0 |
| Gradle | 8.7 | Apache License 2.0 |
| Kotlin | 1.9.24 | Apache License 2.0 |
| KSP (Kotlin Symbol Processing) | 1.9.24-1.0.20 | Apache License 2.0 |

## Notes

- BouncyCastle is pure Java and adds **no native libraries**, which keeps the
  APK architecture-neutral (see README → ARMv7 / ARM64).
- AndroidX and Material components are also pure Java/Kotlin.
- Full license texts for each dependency are available in their respective
  artifacts and project pages.
