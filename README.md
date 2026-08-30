# VibeLink — Vibration Signal Messenger

**Version:** 1.0.0  
**Platform:** Android 10+ (API 29+)  
**Package:** `com.vibeapp`

---

## What is VibeLink?

VibeLink lets two or more Android devices exchange short vibration signals over the internet — instantly and privately. No text, no calls, no social profiles. Just tap a button and the other phone vibrates.

```
Device A presses "Yes"  →  Device B vibrates  →  Device A sees ✓ Vibration started
```

---

## Project Structure

```
VibeLink/
├── android/          ← Android app (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/vibeapp/
│   │   │   ├── core/          ← DB, crypto, vibration, service, network
│   │   │   ├── data/          ← Models, repositories
│   │   │   ├── ui/            ← All screens (Compose)
│   │   │   └── MainActivity.kt
│   │   └── google-services.json   ← ⚠️ REPLACE THIS (Step 3)
│   └── build.gradle.kts
└── backend/          ← Firebase Cloud Functions
    ├── functions/src/index.ts
    ├── database.rules.json
    └── firebase.json
```

---

## Setup Guide

### Step 1 — Prerequisites

Install the following on your machine:

- [Android Studio Ladybug+](https://developer.android.com/studio)
- [Node.js 20+](https://nodejs.org)
- [Firebase CLI](https://firebase.google.com/docs/cli): `npm install -g firebase-tools`
- Java 17+ (bundled with Android Studio)

---

### Step 2 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `vibelink` → continue
3. Enable **Google Analytics** → optional
4. Click **Create project**

Then enable these Firebase services:

**Authentication:**
- Go to Build → Authentication → Get started
- Enable **Anonymous** sign-in method → Save

**Realtime Database:**
- Go to Build → Realtime Database → Create database
- Choose a region (e.g. `europe-west1`)
- Start in **test mode** (we'll deploy secure rules later)
- Copy your database URL: `https://vibelink-xxxxx-default-rtdb.europe-west1.firebasedatabase.app`

**Cloud Messaging (FCM):**
- Already enabled by default when you create an Android app

---

### Step 3 — Add Android App to Firebase

1. In Firebase Console → Project Overview → click **Add app** → Android icon
2. Android package name: `com.vibeapp`
3. App nickname: `VibeLink`
4. Click **Register app**
5. Download the `google-services.json` file
6. **Replace** `android/app/google-services.json` with the downloaded file

---

### Step 4 — Deploy Backend

```bash
cd backend

# Login to Firebase CLI
firebase login

# Link to your project
firebase use --add
# Select your project when prompted → alias: default

# Install Cloud Functions dependencies
cd functions
npm install
npm run build
cd ..

# Deploy rules + functions
firebase deploy
```

Expected output:
```
✔  Deploy complete!
Functions: pairCreate, pairConsume, deviceRegister, deviceStatus, deliveryAck, cleanupExpiredCodes
Database rules: deployed
```

---

### Step 5 — Configure Android App

Open `android/` in Android Studio.

**Wait for Gradle sync to complete** (first time downloads ~2GB of dependencies).

If you see Gradle errors:
- Make sure you have Java 17 selected in: File → Project Structure → SDK Location → JDK location
- Make sure `google-services.json` is the real file from Firebase (Step 3)

> **Font Setup**: The app typography has been updated to use system `FontFamily.SansSerif` as default so it compiles out-of-the-box without requiring external `.ttf` font files.

---

### Step 6 — Build & Install APK

**Debug build (for testing):**
```bash
cd android
./gradlew assembleDebug
# APK: android/app/build/outputs/apk/debug/app-debug.apk
```

**Install on connected device:**
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
```

**Release build (for distribution):**

1. Generate a keystore:
```bash
keytool -genkey -v -keystore vibelink.keystore -alias vibelink -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `android/keystore.properties`:
```properties
storeFile=/absolute/path/to/vibelink.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=vibelink
keyPassword=YOUR_KEY_PASSWORD
```

3. Build release:
```bash
cd android
./gradlew assembleRelease
# APK: android/app/build/outputs/apk/release/app-release.apk
```

---

## First Run on Both Phones

1. Install APK on **Phone A** and **Phone B**
2. Grant permissions when prompted:
   - **Notifications** — required for persistent connection indicator
   - **Battery optimization** — tap "Allow" or disable battery optimization for VibeLink
3. On **Phone A**: tap Settings → Paired Devices → + → **Show Code**
4. On **Phone B**: tap Settings → Paired Devices → + → **Enter Code** → type the code from Phone A → Connect
5. Both phones show **● Connected** in the top bar
6. On Phone A: tap **"Да"** (Yes) → Phone B vibrates → Phone A shows **✓ Vibration started**

---

## Important: Battery Optimization

For VibeLink to work reliably in background, disable battery optimization:

**Settings → Apps → VibeLink → Battery → Unrestricted**

On Samsung/Xiaomi/OPPO/Huawei devices, also check:
- Samsung: Settings → Battery → Background app limits → OFF for VibeLink
- Xiaomi: Settings → Battery → Choose apps → VibeLink → No restrictions
- OPPO/OnePlus: Settings → Battery → Battery optimization → VibeLink → Don't optimize

The app's diagnostic section (Settings → future update) will show if restrictions are active.

---

## Architecture

```
Phone A                          Firebase                        Phone B
  │                                  │                               │
  │── Anonymous Auth ──────────────→ │                               │
  │── Write command to RTDB ───────→ │ ←── Listen for commands ─────│
  │                                  │ ──── Command arrives ────────→│
  │                                  │                               │── Vibrate!
  │                                  │ ←── Write ACK ────────────────│
  │←── Read ACK (RTDB listener) ─── │                               │
  │  ✓ Vibration started             │                               │
```

**Real-time channel:** Firebase Realtime Database WebSocket (built-in, automatic)  
**Fallback:** Firebase Cloud Messaging (high-priority FCM for background wake-up)  
**Transport:** All Firebase traffic is TLS-encrypted  
**Storage:** Room (SQLite) + Android Keystore for pairing secrets  
**Auth:** Anonymous Firebase Auth (no email/password required)

---

## Security Notes

- **No personal data collected.** No name, email, phone number, or location.
- **Device ID** is a random UUID generated on first launch.
- **Pairing secrets** are encrypted with AES-256-GCM via Android Keystore.
- **Pairing codes** are one-time, 15-minute TTL, atomically deleted after use.
- **Backend** only routes commands — it does not read vibration content.
- **All secrets** are kept in Firebase (via `google-services.json`) or Android Keystore. None are in source code.

---

## Release on GitHub

```bash
# Tag a release
git tag v1.0.0
git push origin v1.0.0

# Create GitHub Release and attach:
# - android/app/build/outputs/apk/release/app-release.apk
# - README.md (automatically included)
```

---

## Known Limitations (MVP)

| Limitation | Explanation |
|-----------|-------------|
| Background delivery depends on Android settings | Doze mode / manufacturer battery management may delay FCM. Disable battery optimization. |
| Latency is not guaranteed | Network and Android OS factors affect timing. Target: <500ms foreground, <2s background. |
| No iOS support | Android only in MVP |
| No cloud history sync | History is local to each device |
| Inter font must be added manually | See Step 5 |

---

## Development Roadmap

- [x] Phase 1 — Project scaffold
- [x] Phase 2 — Room database + entities
- [x] Phase 3 — Crypto (Keystore + device identity)
- [x] Phase 4 — Vibration engine (10 patterns + manual)
- [x] Phase 5 — Pairing system (Firebase RTDB)
- [x] Phase 6 — WebSocket / RTDB real-time layer
- [x] Phase 7 — Foreground service (remoteMessaging)
- [x] Phase 8 — Boot recovery + network reconnect
- [x] Phase 9 — Main UI (Compose)
- [x] Phase 10 — Settings (patterns editor + devices)
- [x] Phase 11 — Firebase backend (Cloud Functions)
- [ ] Phase 12 — Font assets + app icon finalization
- [ ] Phase 13 — End-to-end testing on real devices
- [ ] Phase 14 — Release APK + GitHub Releases

---

## Troubleshooting

**"App crashes on startup"**
→ Check that `google-services.json` is the real file from Firebase (not the placeholder)

**"Pairing code shows Invalid"**
→ Make sure both phones have internet. Code is case-insensitive.

**"Phone B doesn't vibrate in background"**
→ Disable battery optimization for VibeLink (see above)

**"Status shows Reconnecting indefinitely"**
→ Check Firebase Realtime Database is enabled in your project

**Gradle build fails on `R.font.inter_regular`**
→ Add Inter font files to `android/app/src/main/res/font/` (see Step 5)
