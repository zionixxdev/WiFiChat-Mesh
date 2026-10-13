# Protocol Specification (v1)

This document defines the wire format, the security scheme, and the routing
rules. It is the contract every implementation must follow.

## 1. Transport framing

Bluetooth RFCOMM provides a byte stream with no message boundaries, so every
packet is length-prefixed:

```
[ 4 bytes big-endian length N ][ N bytes packet ]
```

- Maximum frame size: 64 KiB (`Constants.MAX_PACKET_BYTES`).
- A frame whose declared length exceeds the maximum is rejected as corrupt,
  which also bounds the memory a hostile peer can make us allocate.

## 2. Packet layout

All integers are big-endian.

| Field | Type | Notes |
|---|---|---|
| magic | 4 bytes | `W C M 0x01` |
| protocolVersion | int32 | currently `1` |
| type | byte | see packet types below |
| messageId | string | UUID; the unit of duplicate suppression |
| senderId | string | sender's stable peer id |
| recipientId | string | destination peer id, or group id |
| createdAt | int64 | epoch millis |
| expiresAt | int64 | epoch millis; `0` = never expires |
| hopCount | int32 | incremented by each relay |
| maxHops | int32 | hop budget (default 5) |
| nonce | bytes | AES-GCM nonce (12 bytes) |
| payload | bytes | ciphertext (or clear text for ANNOUNCE) |
| signature | bytes | Ed25519 over the signable bytes (64 bytes) |

A `string` is a 4-byte length followed by UTF-8 bytes; a `bytes` field is the
same. See `mesh/PacketCodec.kt`.

### Packet types

| Code | Type | Purpose |
|---|---|---|
| `0x01` | `ANNOUNCE` | peer hello: display name, Ed25519 key, X25519 key |
| `0x02` | `DATA` | a 1:1 message |
| `0x03` | `GROUP` | a group message |
| `0x04` | `ACK_DELIVERED` | destination device received the message |
| `0x05` | `ACK_READ` | recipient read the message |
| `0x06` | `KEY_EXCHANGE` | X25519 key material for session setup |

## 3. Signature coverage

`signableBytes(packet)` covers the header **except `hopCount`**, plus the nonce
and the payload:

```
magic · version · type · messageId · senderId · recipientId ·
createdAt · expiresAt · maxHops · nonce · payload
```

`hopCount` is excluded because relays legitimately mutate it; everything else is
covered so a relay cannot tamper with the sender, recipient or expiry without
invalidating the signature.

## 4. Security scheme

Primitives (BouncyCastle — reviewed, maintained, no home-made algorithms):

- **Ed25519** — identity and packet signatures.
- **X25519** — ECDH key agreement.
- **HKDF-SHA256** — key derivation.
- **AES-256-GCM** — authenticated encryption.

### Keys

Each device holds, generated locally on first launch:

- an Ed25519 identity key pair (signing);
- a static X25519 key pair (key agreement).

The Ed25519 **public** key and the X25519 **public** key are broadcast in the
`ANNOUNCE` packet. The private halves are wrapped by a non-exportable AES key
held in the Android Keystore (`crypto/IdentityKeyStore.kt`).

### Message sealing

For a message with id `M` from A to B:

```
sessionKey = HKDF-SHA256(
                 X25519(A.staticPriv, B.staticPub),
                 salt = M,                       // per-message salt
                 info = "wifichat-mesh-v1" )
aad        = canonical header (version, type, M, sender, recipient,
                               createdAt, expiresAt, maxHops)
payload    = AES-256-GCM(plaintext, sessionKey, nonce, aad)
signature  = Ed25519( signableBytes(header + nonce + payload), A.identityPriv )
```

Using the message id as the HKDF salt gives every message a distinct key while
deriving from one long-lived X25519 pair, so no extra round trip is needed to
start a conversation. B reconstructs the identical `sessionKey` from
`X25519(B.staticPriv, A.staticPub)` with the same salt and info.

### Relay confidentiality

A relay node forwards **ciphertext** and verifies the signature, but cannot
decrypt the payload because it does not hold the recipient's X25519 private key.
Relays therefore cannot read private messages.

### Integrity and replay

- AES-GCM's authentication tag detects any tampering with the ciphertext or AAD.
- The Ed25519 signature detects tampering with the header.
- The `messageId` plus the persistent `seen_ids` ledger provides replay and
  duplicate protection.

### Safety number

Two peers can compare a deterministic 60-digit safety number
(`CryptoManager.safetyNumber`) out of band to confirm they hold each other's
real keys. It is order-independent so both sides compute the same value.

### Group messaging

The design intent is a per-group key rather than one shared secret reused
verbatim. v1 ships group delivery as best-effort broadcast; the group-key
protocol is not yet implemented (see KNOWN_LIMITATIONS).

## 5. Routing rules

- **Strategy:** epidemic forwarding with a hop limit and duplicate suppression.
  An outgoing packet is offered to every directly connected neighbour; each
  forwards it the same way until it reaches the destination or exhausts its hop
  budget. This is correct for small nearby meshes and needs no routing table.
- **Hop limit:** a packet is forwarded only while `hopCount < maxHops`
  (`MeshValidator.isForwardable`). At the limit it is dropped. This is the
  loop-prevention guarantee.
- **Duplicate suppression:** a packet whose `messageId` has been seen is neither
  displayed nor forwarded again. Two layers: a fast in-memory ring for the hot
  read path, and the persistent `seen_ids` table for restarts.
- **Expiry:** a packet with `expiresAt` in the past is dropped
  (`MeshValidator.isExpired`). `expiresAt == 0` means never.
- **Malformed packets:** rejected at decode; they never crash the read loop.
- **Retries:** exponential backoff with ±20% jitter, capped, up to a fixed
  attempt ceiling (`RetryPolicy`). Retries are stored so they survive restarts.

## 6. Delivery states (never faked)

| State | Meaning |
|---|---|
| `PENDING` | created locally, not yet queued |
| `QUEUED` | in the store-and-forward queue, waiting for a route |
| `TRANSMITTED` | bytes handed to a directly connected neighbour |
| `DELIVERED_TO_DEVICE` | the destination device returned an ack |
| `READ` | the recipient reported the message as read |
| `FAILED` | retries exhausted or dropped |
| `EXPIRED` | past its expiry |

A successful RFCOMM write is **not** proof of any state beyond `TRANSMITTED`.
The UI shows only states backed by a real event.
