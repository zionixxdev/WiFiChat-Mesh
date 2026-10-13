# Permissions

WiFiChat Mesh requests the minimum needed for nearby Bluetooth communication.
It has **no `INTERNET` permission** — there is no server and nothing is
uploaded. Each permission is explained to the user in onboarding *before* it is
requested.

## Bluetooth

Bluetooth permissions changed at API 31 (Android 12). The app declares both
sets and the OS applies the right one for the running version.

| Permission | Applies to | Why |
|---|---|---|
| `BLUETOOTH` (maxSdkVersion 30) | API ≤ 30 | Legacy permission to use Bluetooth at all |
| `BLUETOOTH_ADMIN` (maxSdkVersion 30) | API ≤ 30 | Legacy permission to start discovery |
| `ACCESS_FINE_LOCATION` (maxSdkVersion 30) | API ≤ 30 | Android requires location permission for Bluetooth discovery on these versions |
| `BLUETOOTH_SCAN` | API ≥ 31 | Discover nearby devices |
| `BLUETOOTH_CONNECT` | API ≥ 31 | Open RFCOMM sockets and read peer names |
| `BLUETOOTH_ADVERTISE` | API ≥ 31 | Be discoverable so others can find us |

`BLUETOOTH_SCAN` is declared with `android:usesPermissionFlags="neverForLocation"`
because the app does not derive physical location from scan results.

On API ≤ 30, location permission is required by the **operating system** for
Bluetooth discovery, not by this app. The app never reads, stores or transmits
location data.

## Notifications

| Permission | Applies to | Why |
|---|---|---|
| `POST_NOTIFICATIONS` | API ≥ 33 | Show the persistent notification required by the foreground service |

## Foreground service

| Permission | Applies to | Why |
|---|---|---|
| `FOREGROUND_SERVICE` | all | Run the mesh while backgrounded |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | API ≥ 34 | The correct foreground-service type for Bluetooth connections |

## Boot

| Permission | Applies to | Why |
|---|---|---|
| `RECEIVE_BOOT_COMPLETED` | all | Restart the mesh service after a reboot if discovery was enabled |

## Hardware

```xml
<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

The app is useless without Bluetooth, so the device must have it.

## What the app does NOT request

- No `INTERNET` — nothing leaves the device.
- No contacts, camera, microphone, storage or location-read permissions.
- No account, email or phone number.

## Backups

`android:allowBackup="false"` is set, and both `backup_rules.xml` and
`data_extraction_rules.xml` exclude the database and shared preferences, because
they contain private message history and identity metadata.
