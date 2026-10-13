# Phone-only setup: Termux → GitHub → APK

Everything here is done from your Android phone. No computer required. This is
written for your TECNO Pop 7 (Android 12 Go).

---

## 1. Install Termux (from F-Droid, not the Play Store)

The Play Store version of Termux is outdated and broken. Install the F-Droid
build:

1. Install **F-Droid** (from https://f-droid.org).
2. In F-Droid, search **Termux** and install it.

Open Termux and update packages:

```bash
pkg update -y && pkg upgrade -y
pkg install -y git openssh
```

## 2. Configure Git

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## 3. Create a GitHub repository

1. In your phone browser, sign in to GitHub.
2. Create a **new empty repository** named `WiFiChat-Mesh` (private is fine).
   Do **not** add a README, .gitignore or license — the project already has them.
3. Create a **Personal Access Token** (classic) with the `repo` scope:
   GitHub → Settings → Developer settings → Personal access tokens → Tokens
   (classic) → Generate new token. Copy it somewhere safe.

## 4. Get the project onto your phone and push it

Copy the project folder to Termux storage. If you downloaded a zip of the
project, unzip it (in Termux, `pkg install unzip` first):

```bash
pkg install -y unzip
cd ~
unzip /sdcard/Download/WiFiChat-Mesh.zip -d ~/WiFiChat-Mesh
```

Then push it to GitHub (replace `YOUR_USER`):

```bash
cd ~/WiFiChat-Mesh
git init
git add .
git commit -m "Initial commit: WiFiChat Mesh"
git branch -M main
git remote add origin https://github.com/YOUR_USER/WiFiChat-Mesh.git

# When prompted for a password, paste your Personal Access Token (not your GitHub password)
git push -u origin main
```

## 5. Let GitHub Actions build the APK

1. Open your repository in the browser → **Actions** tab.
2. The **Android Build** workflow runs automatically on the push.
3. To build on demand: **Android Build** → **Run workflow** → **Run workflow**.
4. Wait for the run to go green (a few minutes the first time — Gradle downloads
   dependencies).

## 6. Download the APK

1. Open the completed workflow run.
2. Scroll to **Artifacts**.
3. Download **`WiFiChat-Mesh-armv7`** (this is the one for your TECNO Pop 7 —
   though because the app has no native code, all three APKs are identical and
   any will install). The artifact downloads as a `.zip`; unzip it to get the
   `.apk`.

## 7. Install the APK on the phone

1. Open your file manager and tap the downloaded `.apk`.
2. When Android blocks it, allow **"Install unknown apps"** for your browser or
   file manager (Settings → Apps → Special access).
3. Tap **Install**, then **Open**.
4. On first launch, grant **Bluetooth** and **Notification** permissions when
   asked, and choose a display name.

## 8. First run checklist (Android 12 Go / ARMv7)

- [ ] The app launches and shows the onboarding screens.
- [ ] You can set a display name and reach the Chats tab.
- [ ] **Nearby** → **Start discovery** turns the status to *Discovering*.
- [ ] A second phone running the same APK appears in **Nearby**.
- [ ] You can open a conversation and send a message (offline).
- [ ] The **Mesh** tab shows real counts and a live event log.
- [ ] Airplane-mode-with-Bluetooth-on (no data, no Wi-Fi) still sends messages.

If Nearby stays empty: make sure Bluetooth is on, the other phone has the app
open with discovery started, and the other device is *discoverable* (some phones
hide this by default in Bluetooth settings).

---

## Optional: check the APK's architecture from Termux

```bash
pkg install -y unzip
unzip -l ~/storage/downloads/WiFiChat-Mesh-armv7.apk | grep -E 'lib/.*\.so' \
  || echo "No native libraries — APK is architecture-neutral (ARMv7 + ARM64)"
```

An empty result confirms the APK carries no native code, so it runs on both
32-bit and 64-bit ARM.

## Troubleshooting

- **`git push` rejected** — re-check the token has `repo` scope and that you
  pasted it as the password.
- **Action fails at `assembleDebug`** — open the failed step's log; it will name
  the file and line. Most first-run failures are a typo introduced while
  copying files.
- **APK won't install** — uninstall any older copy first; make sure "Install
  unknown apps" is enabled for the app you are installing from.
