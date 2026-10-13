# WiFiChat Mesh

Offline, serverless, peer-to-peer messaging for Android. Nearby phones talk
directly over Bluetooth, and messages can hop through other phones to reach
someone you are not directly connected to — no mobile data, no internet, no
account, no server.

> **Status — read this first.** This repository is a complete, buildable
> project. The cryptographic core and the wire codec have been executed and
> verified against the real BouncyCastle library. The Android application has
> **not** yet been compiled into an APK or run on a physical device from this
> environment. The first real "it works" confirmation comes from your own
> GitHub Actions build and your TECNO Pop 7. See
> [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

---

## Tech stack (named explicitly)

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** (JVM target 17) | Modern, concise, first-class Android support |
| UI toolkit | **Android XML Views + Material 3** (not Jetpack Compose) | Lighter on RAM and APK size for Android Go / ARMv7; no Compose runtime |
| Build | **Gradle (Kotlin DSL) 8.7**, AGP 8.5.2, Kotlin 1.9.24 | Pinned, reproducible |
| Storage | **Room 2.6.1** (SQLite) | Versioned migrations, coroutine/Flow support |
| Crypto | **BouncyCastle `bcprov-jdk18on` 1.78.1** | Pure-Java: Ed25519, X25519, HKDF-SHA256, AES-256-GCM — **no native code** |
| Transport | **Bluetooth Classic / RFCOMM (SPP)** | Reliable byte stream, works on ARMv7 + Android 12 Go, no Google Play Services |
| CI | **GitHub Actions** (free runners) | Builds + tests + packages the APK |

**Every dependency is pure Java/Kotlin — the app ships zero native (`.so`)
libraries.** This is deliberate and is the whole answer to the ARMv7/ARM64
question: an APK with no native code is architecture-neutral and installs on
both 32-bit and 64-bit ARM devices. See [ARMv7 / ARM64](#armv7--arm64) below.

Minimum Android version: **7.0 (API 24)**. Target: **34**.

---

## What it does

- **Offline peer-to-peer messaging** over Bluetooth, with no internet at all.
- **Automatic discovery** of nearby WiFiChat Mesh devices.
- **Multi-hop relaying** — store-and-forward through intermediate phones.
- **End-to-end encryption** (X25519 + HKDF + AES-256-GCM, Ed25519 signatures).
- **Persistent local history** that survives restarts and reboots.
- **Reliable delivery** with duplicate suppression, hop limits, retries with
  backoff, and message expiry.
- **A polished dark-first UI** with four tabs: Chats, Nearby, Mesh, Settings.

## What it deliberately does NOT do

- No server, no cloud, no account, no login. There is no `INTERNET` permission
  in the manifest.
- No fake peers, fake device counts, or fabricated delivery ticks. Every status
  shown corresponds to a real observed event.
- No terminal-style interface, no placeholder screens.

---

## Project structure

```
app/
  src/main/java/com/wifichat/mesh/
    model/        Domain types: Peer, Message, Conversation, MeshPacket, states
    crypto/       BouncyCastle primitives + Keystore-wrapped identity
    data/         Room entities, DAOs, database, mappers
    repository/   Message/Conversation/Peer/Queue/EventLog + settings
    network/      Bluetooth RFCOMM transport, discovery, framing, status
    mesh/         Packet codec, packet factory, router, queue, validators
    service/      Foreground service + boot receiver
    ui/           Onboarding, bottom nav, Chats, Chat, Nearby, Mesh, Settings
    util/         Constants, ids, logging
  src/test/       JVM unit tests (codec, crypto, routing, dedupe, retry)
  schemas/        Exported Room schema for migration review
docs/             Architecture, protocol, permissions, testing, limitations
.github/workflows/android-build.yml
```

---

## Build from your phone (Termux → GitHub → APK)

You do not need a computer. Follow
[docs/TERMUX_SETUP.md](docs/TERMUX_SETUP.md) for the exact commands. In short:

1. **Install Termux** (from F-Droid, not Play Store) and run
   `pkg install git openssh`.
2. **Push this project to GitHub** from Termux (the doc has the copy-paste
   commands, including creating the repo and pushing with a personal access
   token).
3. **Let GitHub Actions build it** — the workflow runs on every push and can be
   triggered manually from the **Actions** tab → **Android Build** →
   **Run workflow**.
4. **Download the APK** from the run's **Artifacts** section
   (`WiFiChat-Mesh-armv7`, `-arm64`, `-universal`).
5. **Install** the APK on your TECNO Pop 7 (allow install from unknown sources
   when prompted).

## Build locally (any machine with a JDK)

```bash
# JDK 17 required
gradle testDebugUnitTest      # run the unit tests
gradle assembleDebug          # build the debug APK
# output: app/build/outputs/apk/debug/
```

The Gradle wrapper JAR is intentionally not committed (it is a binary). If you
prefer `./gradlew`, run `gradle wrapper` once to generate it.

---

## ARMv7 / ARM64

The brief asks for separate 32-bit and 64-bit APKs and warns against assuming a
"universal" APK really supports both. Here is the honest, verified position:

- **This app contains no native libraries.** Every dependency (AndroidX, Room,
  BouncyCastle) is pure Java/Kotlin. There is no `armeabi-v7a`-excluding
  dependency because there are no `.so` files at all.
- An APK with an empty `lib/` directory is **architecture-neutral**: the same
  bytes install and run on `armeabi-v7a` and `arm64-v8a`.
- The Gradle `splits.abi` block is enabled so CI emits explicitly ABI-labelled
  artifacts, and the workflow **inspects each APK's `lib/` contents and
  `native-code` badging** so you can see for yourself that the list is empty.
- Therefore `WiFiChat-Mesh-armv7.apk`, `-arm64.apk` and `-universal.apk` are
  functionally identical. The separate names are kept for clarity and to match
  the deliverable. The workflow prints this explicitly.

If a future dependency introduces native code, `splits.abi` will then produce
genuinely different per-ABI APKs, and the workflow's inspection step will show
the real ABIs — no code change needed.

---

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — modules, transport choice, data flow
- [Protocol](docs/PROTOCOL.md) — packet format, crypto scheme, routing rules
- [Permissions](docs/PERMISSIONS.md) — every permission and why
- [Testing](docs/TESTING.md) — unit tests + real-device test plan (A–G)
- [Known limitations](docs/KNOWN_LIMITATIONS.md) — what is not done yet
- [Build & versions](docs/BUILD.md) — pinned toolchain and version matrix
- [Termux setup](docs/TERMUX_SETUP.md) — phone-only workflow
- [Licenses](docs/LICENSES.md) — third-party dependency licenses

## License

The application source is released under the **MIT License** (see `LICENSE`).
Third-party components keep their own licenses; see
[docs/LICENSES.md](docs/LICENSES.md).
