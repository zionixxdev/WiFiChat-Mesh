# Known limitations and unfinished work

This file is deliberately blunt. The brief requires that nothing be claimed
working until it has been proven, and that remaining limitations be documented.

## Build and verification status

- **The Android app has not been compiled into an APK or run on a physical
  device from the development environment that produced this repository.**
  Only the GitHub Actions workflow on your account can produce the first APK.
- The cryptographic core and the wire codec **have** been executed against the
  real BouncyCastle 1.78.1 JAR and pass (Ed25519, X25519+HKDF, AES-256-GCM with
  tamper detection, codec round trip). Everything else in the app is verified
  only by compilation and the JVM unit tests.
- Do not claim the app "works" until Tests A–G in
  [TESTING.md](TESTING.md) pass on real devices.

## Routing

- **Epidemic flooding, not shortest-path routing.** Packets are offered to every
  neighbour and forwarded until the hop limit is reached. This is correct for
  small nearby meshes but wastes bandwidth in dense ones. There is no routing
  table, no link-quality metric, and no path optimisation.
- **No store-and-forward across a *disconnected* relay beyond its queue.**
  If a relay is switched off, it holds queued packets and forwards them when it
  returns (Test C), but a packet cannot be handed to a relay that is currently
  unreachable.
- **Hop limit is a fixed default of 5** (configurable in Settings up to 10), not
  adaptive.

## Security

- **Key trust is not automatically verified.** When a packet arrives from a peer
  whose Ed25519 key has not yet been learned (before their ANNOUNCE), the
  signature is accepted so the first exchange can complete. A safety number is
  computed for out-of-band verification, but there is no QR-code exchange and
  the app does not force verification before messaging. Treat the first-contact
  window as unauthenticated.
- **Group messaging is not cryptographically complete.** Group delivery in v1 is
  best-effort broadcast. A proper per-group key design is specified as intent but
  not implemented; do not treat group messages as having the same guarantees as
  1:1 messages.
- **Session keys are held in memory only.** X25519 public keys learned from
  ANNOUNCE are not persisted, so after a restart a peer must re-announce before
  you can send to them. This is a functional limitation, not a vulnerability.
- **Not audited.** Using reviewed primitives does not make the app audited. It
  is not claimed to be secure against a determined adversary.

## Networking and platform

- **Bluetooth Classic discovery only finds *discoverable* devices.** Already
  paired devices are also listed. Devices that are neither discoverable nor
  paired will not appear.
- **OEM and Android Go background restrictions apply.** The foreground service
  may be suspended or killed by aggressive battery managers. The queue is
  persisted so nothing is lost, but background connectivity is not guaranteed.
- **Bluetooth Classic throughput** is lower than BLE 5 or Wi-Fi Direct. Large
  transfers are not attempted.
- **Single transport.** Only Bluetooth Classic is implemented. Wi-Fi Direct
  (planned as a high-throughput transport) and BLE are not.

## Features not yet implemented

- Attachments: images, files, voice messages.
- Message reactions.
- QR-based contact exchange and a key-verification flow.
- Local announcements / broadcast channels.
- A true network map with live route paths (the Mesh screen shows nodes and
  direct links only, from real data).
- Release signing. Only a debug APK is produced; release signing must be set up
  separately, and signing credentials must never be committed.

## UI

- Attachment menu on the chat screen is a prepared placeholder, not functional.
- Empty states and error states are present but have not been visually reviewed
  on a physical device from this environment.
