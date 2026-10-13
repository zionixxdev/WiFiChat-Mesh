# Architecture

## 1. Transport selection

Four options were considered, as required by the brief.

| Option | Internet-free | Android 12 Go | ARMv7 | Play Services | Multi-peer | Multi-hop | Verdict |
|---|---|---|---|---|---|---|---|
| **A. BLE GATT** | Yes | Yes | Yes | No | Hard (peripheral role limited) | Possible but complex | Rejected for v1 |
| **B. Bluetooth Classic / RFCOMM** | Yes | Yes | Yes | **No** | Yes | Yes | **Selected** |
| **C. Google Nearby Connections** | Yes | Yes | Yes | **Required** | Yes | Built-in | Rejected |
| **D. Wi-Fi Direct** | Yes | Yes | Yes | No | Yes | Yes | Deferred to v2 |

**Why Bluetooth Classic (RFCOMM/SPP):**

- **No Google Play Services.** Google Nearby Connections depends on Play
  Services, which is often absent or restricted on Android Go devices and on
  de-Googled ROMs. The brief explicitly wants strict offline operation on
  diverse devices, so a self-contained transport wins.
- **Reliable, ordered byte stream.** RFCOMM gives a socket-like API with TCP-like
  semantics, which makes framing, retries and store-and-forward much simpler and
  more robust than GATT's request/response model.
- **Multi-peer and multi-hop friendly.** Each device runs one RFCOMM server and
  connects as a client to every neighbour, yielding a full mesh where routing
  logic — not the radio — decides the path.
- **ARMv7 and Android 12 Go compatible.** Bluetooth Classic has been stable
  since API 5 and has no 64-bit-only constraints.

**Trade-offs accepted:** lower throughput than BLE 5 or Wi-Fi Direct, and a
pairing/permission surface that varies by OEM. These are documented in
[KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md). Wi-Fi Direct is left as a future
high-throughput transport behind the same `MeshTransport` interface.

## 2. Module map

```
┌──────────────────────────────────────────────────────────────┐
│ UI (ui/)   Onboarding · Chats · Chat · Nearby · Mesh · Settings │
└───────────────▲───────────────────────────────┬───────────────┘
                │ StateFlow / LiveData          │ user actions
┌───────────────┴───────────────────────────────▼───────────────┐
│ ViewModels  (ChatsViewModel, ChatViewModel, Nearby/Mesh/Settings)│
└───────────────▲───────────────────────────────┬───────────────┘
                │                                │
┌───────────────┴──────────────┐   ┌─────────────▼─────────────┐
│ MeshRouter (mesh/)           │   │ Repositories (repository/) │
│  decode · verify · route     │   │  Message / Conversation /  │
│  dedupe · hop-limit · ack    │   │  Peer / Queue / EventLog   │
│  ┌────────────────────────┐  │   └─────────────┬─────────────┘
│  │ MessageQueue           │  │                 │
│  │  store-and-forward,    │  │                 │
│  │  retry, expiry         │  │                 │
│  └────────────────────────┘  │                 │
│  ┌────────────────────────┐  │                 │
│  │ PacketCodec / Factory  │  │                 │
│  └────────────────────────┘  │                 │
└───────────────▲──────────────┘                 │
                │ inbound frames / sendTo        │ Room
┌───────────────┴──────────────┐   ┌─────────────▼─────────────┐
│ MeshTransport (network/)     │   │ Room (data/)              │
│  BluetoothClassicTransport   │   │  messages · conversations │
│  DiscoveryManager            │   │  peers · forward_queue    │
│  FrameCodec · Status         │   │  seen_ids · event_log     │
└──────────────────────────────┘   └───────────────────────────┘
                │
        Bluetooth Classic (RFCOMM)
```

**Responsibilities**

- `model/` — pure domain types, no Android imports.
- `crypto/` — `CryptoManager` interface + BouncyCastle implementation, plus
  `IdentityKeyStore` (Keystore-wrapped key storage).
- `data/` — Room entities, DAOs, database and entity↔model mappers.
- `repository/` — the only code that touches DAOs; exposes Flows to ViewModels.
- `network/` — the `MeshTransport` interface and its Bluetooth implementation,
  discovery, stream framing, and the shared `NetworkStatusManager`.
- `mesh/` — the protocol: `PacketCodec`, `MeshPacketFactory`, `MeshRouter`,
  `MessageQueue`, `MeshValidator`, `RetryPolicy`.
- `service/` — foreground service that owns discovery + transport + router.
- `ui/` — one package per screen, thin fragments/activities over ViewModels.
- `AppContainer` — hand-rolled dependency container (no Hilt/Dagger; fewer
  dependencies and less build machinery on a low-RAM target).

The transport sits behind `MeshTransport`, so routing, storage and UI are
unaffected if the radio is replaced.

## 3. Data flow

**Outgoing message**

1. `ChatViewModel.send()` → `MeshRouter.sendMessage()`.
2. Router looks up the recipient's X25519 key (learned from ANNOUNCE). If
   absent, it returns null and the UI shows *"Waiting for peer"* — it never
   fakes success.
3. `MeshPacketFactory` derives a per-message key (HKDF), encrypts with AES-GCM,
   signs with Ed25519, and returns a `MeshPacket`.
4. The message is saved locally (`PENDING`), then queued in `forward_queue`
   (`QUEUED`).
5. `MessageQueue` offers the serialised packet to every connected neighbour
   (`TRANSMITTED`), retrying with backoff until acknowledged or expired.

**Incoming frame**

1. `BluetoothClassicTransport` reads a length-prefixed frame and emits it on
   `inbound`.
2. `MeshRouter` decodes it, verifies the signature, drops it if expired or
   already seen (duplicate suppression).
3. If the packet is for us: decrypt, store, update the conversation, and flood
   an `ACK_DELIVERED` back toward the sender.
4. If not for us and `hopCount < maxHops`: increment the hop counter, re-emit to
   all neighbours except the one it arrived from (`MESSAGE_RELAYED`).
5. If the hop budget is exhausted: drop. This is what prevents endless loops.

**Acknowledgements**

`ACK_DELIVERED` moves the sender's message to `DELIVERED_TO_DEVICE`;
`ACK_READ` moves it to `READ`. Both are flooded back so they work over a
multi-hop route. The sender's message is only removed from the queue when an
ack arrives, it expires, or retries are exhausted.

## 4. Threading

- All blocking Bluetooth calls run on `Dispatchers.IO` inside a
  `SupervisorJob` scope; the main thread is never blocked.
- Room access is via `suspend` DAOs and `Flow`, off the main thread.
- Transport exposes `StateFlow`/`SharedFlow`; ViewModels convert to `LiveData`
  for the Views.
- The foreground service is the single owner of discovery + transport + router,
  so their lifetimes are not tied to an Activity.

## 5. Background behaviour

- A `connectedDevice` foreground service keeps the mesh alive while
  backgrounded, with a persistent low-importance notification.
- Discovery is paced (a discovery window followed by a pause) to save battery.
- On reboot, `BootReceiver` restarts the service if the user had discovery on.
- Android Go and OEM battery managers may still suspend the service. When that
  happens the queue is persisted and resumes on restart — the app stays useful
  rather than pretending it is always connected.
