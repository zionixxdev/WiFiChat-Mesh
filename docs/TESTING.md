# Testing

Two layers: fast JVM unit tests that run in CI on every push, and a real-device
plan that must be carried out by hand because multi-hop routing cannot be proven
in a unit test.

## 1. Automated unit tests (JVM, no device)

Run with:

```bash
gradle testDebugUnitTest
```

| Test file | Covers |
|---|---|
| `mesh/PacketCodecTest` | encode/decode round trip, bad magic, truncation, negative length, unknown type, signable-bytes stability across hop counts |
| `crypto/CryptoManagerTest` | Ed25519 sign/verify, tamper rejection, wrong-key rejection, AES-GCM round trip, ciphertext tamper, wrong AAD, ECDH symmetry, per-salt key divergence, safety-number determinism |
| `mesh/MeshPacketFactoryTest` | full seal→verify→decrypt handshake between two identities, ANNOUNCE contents, codec round trip |
| `mesh/MeshValidatorTest` | expiry, hop-limit forwarding guard, hop clamping |
| `mesh/RetryPolicyTest` | backoff growth, cap, jitter bounds, exhaustion |
| `repository/MessageRepositoryTest` | duplicate suppression within an instance and across restarts, status transitions, expiry pruning |
| `repository/QueueRepositoryTest` | enqueue/due, removal, expiry pruning, bounded queue eviction |
| `util/IdsTest` | message-id uniqueness, random byte length, short tags |

These use in-memory DAO doubles (`fakes/FakeDaos.kt`), so they need no emulator.

> The cryptographic core and the wire codec have additionally been executed
> against the real BouncyCastle 1.78.1 JAR during development and pass
> (Ed25519 sign/verify, X25519+HKDF symmetry, AES-256-GCM round trip with
> tamper detection, and codec round trip).

## 2. Real-device tests (required — multi-hop must be proven)

You need at least **three** phones with WiFiChat Mesh installed. Tests B, C and
D are the ones that actually prove the mesh works; a two-phone chat test alone
does **not** prove multi-hop support.

Install the APK on each phone and grant Bluetooth + notification permissions.

**Test A — direct delivery.** A and B are connected.
Expected: a message from A appears on B, and A's tick reaches
*Delivered to device*.

**Test B — multi-hop relay (the important one).** A, B and C are running.
Arrange the topology so A **cannot** reach C directly — put physical distance
between them, or connect only B↔A and B↔C. Send A → C.
Expected: the message arrives at C, and B's Mesh tab shows *Messages relayed*
incrementing. If it only works when A and C are adjacent, multi-hop is not
proven.

**Test C — queue persistence across a disconnect.** Start A → C with B
relaying. Turn B off mid-flight. Expected: the message stays *Queued* on A. Turn
B back on. Expected: it forwards and eventually reaches C. Also force-stop and
reopen the app while it is queued and confirm it is still queued.

**Test D — duplicate suppression.** Send the same packet twice (retry
aggressively, or reconnect B during a send). Expected: the message is displayed
once at C and not forwarded indefinitely.

**Test E — restart persistence.** Send messages, close the app, reboot the
phone, reopen. Expected: conversations and any pending messages survive.

**Test F — true offline.** Disable mobile data and Wi-Fi on every phone. Expected:
the transport still works after installation and initial setup. This is the core
claim of the app.

**Test G — installed APK and ABIs on the TECNO Pop 7.** Install the APK and
confirm it launches on Android 12 Go / ARMv7. From a shell, confirm the APK has
no native libraries and thus supports both ABIs:

```bash
# On a computer, or via Termux with unzip available:
unzip -l WiFiChat-Mesh-universal.apk | grep -E 'lib/.*\.so' || echo "no native libs (ABI-neutral)"
```

## 3. What a passing build does and does not prove

- A green CI run proves the project **compiles** and the unit tests **pass**.
- It does **not** prove the app runs correctly on a device, that Bluetooth
  works on your OEM ROM, or that multi-hop routing works. Only tests A–G do.
- Do not claim multi-hop support until Test B passes with a genuine
  non-adjacent topology.
