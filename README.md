# OpenPRT

![version](https://img.shields.io/badge/version-0.1.36-blue)

**A free, open-source bus and light rail app for Pittsburgh Regional Transit (PRT).** Android for now.

See which buses you can catch from where you are standing, follow your bus live on the map, and plan a trip
across Pittsburgh — no ads, no account, no analytics.

## What it does

- 📍 **Nearby departures** — the buses and **T light rail** (Red, Blue and Silver Line) you can still walk to and
  catch, soonest first, updated every 30 seconds
- 🚏 **Tap any stop** on the map to see its next departures, live when PRT has a prediction and from the
  timetable when not
- 🚌 **Follow a bus** — tap a departure to see its route, its stops and where the bus is right now, with the
  minutes until it reaches your stop
- 🧭 **Plan a trip** — search for a place (or long-press the map), optionally start from an address instead of
  your location, and get up to three ways to get there with walking, transfers and times.
  Choose **Leave now**, **Depart at** or **Arrive by**; walking legs follow real streets on the map
- 🌙 **Light and dark themes** — follows your phone, or pick one with the half-circle button at the top
- 📶 **Works on a bad connection** — shows the last departures it got when you are offline, and keeps the bus
  timetable on your phone

## Install

1. Open the **[Releases](https://github.com/AquilaWei/OpenPRT/releases)** page and pick the latest version
2. Download the APK that fits your phone:
   - **`OpenPRT-vX.Y.Z-arm64-v8a.apk`** — almost every Android phone from the last several years (smallest, about 24 MB)
   - `OpenPRT-vX.Y.Z-armeabi-v7a.apk` — older 32-bit phones
   - `OpenPRT-vX.Y.Z-universal.apk` — not sure? This one works on every phone, but it is about 60 MB
3. (Optional) Check the download is intact against the matching `.sha256` file:

   ```bash
   sha256sum -c OpenPRT-vX.Y.Z-arm64-v8a.apk.sha256
   ```

4. Open the APK on your phone and allow "install unknown apps" when asked

> On first launch the app downloads PRT's stops and timetable (about 22 MB, about **75 MB** once stored).
> It refreshes them **in the background on Wi-Fi or mobile data** when they are more than 7 days old, and keeps
> the old copy if a download fails.

> If you installed a debug build you compiled yourself, uninstall it first: it is signed differently, so the
> release cannot install over it. Uninstalling clears the key you entered and the downloaded timetable.

### Live times need a free PRT TrueTime key

Live departures and bus positions come from the **PRT TrueTime API**, which needs a personal key:

1. Create an account at **[PRT TrueTime](https://truetime.rideprt.org/bustime/home.jsp)** and sign in
2. Request a real-time API key as described on the **My Account** page
3. **Paste the key on the welcome screen** the first time you open the app and tap **Save key**;
   the app checks it with TrueTime before saving
   - To change it later, tap the **key icon** at the top right of the map
   - Or tap **Skip for now**: stops, place search and timetable-based trip planning work without a key

The key stays in the app's private storage on your phone; it is not backed up and is only sent to PRT.

## Where the data comes from

All free, none needs a key except TrueTime:

- **PRT TrueTime** — live predictions and bus positions
- **PRT GTFS** — stops and the timetable, the `gtfs.zip` on PRT's
  [developer resources page](https://www.rideprt.org/business-resources/web-developer-resources/)
- **[MapLibre](https://maplibre.org/)** with **[OpenFreeMap](https://openfreemap.org/)** OpenStreetMap tiles — the map
- **[Photon](https://photon.komoot.io/)** — place search (OpenStreetMap geocoding)
- **[FOSSGIS Valhalla](https://valhalla1.openstreetmap.de/)** — walking routes along streets. When you open a trip, the
  start and end of each walk are sent to this service; if it does not answer within 5 seconds the walk is drawn
  as a straight line

## Build from source

### Requirements

- **JDK 21 or newer** (any JDK or JRE works; Gradle downloads the JDK 21 toolchain itself)
- **Android SDK** with platform **android-37** and build-tools 36 (Gradle downloads missing packages once the
  licenses are accepted)

Create `local.properties` in the project root (never committed) with your SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

**For development only (optional):** add `PRT_API_KEY=your-key` to `local.properties` and debug builds will carry
that key and skip the welcome screen; a key entered in the app wins. **Do not commit this file or paste it into
issues or chats.** Everything builds and the tests run without any key.

### Build and test

The full check, the same one CI runs:

```bash
./gradlew --no-daemon ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

| Command | What it does |
|---|---|
| `./gradlew ktlintFormat` | Fixes Kotlin formatting |
| `./gradlew testDebugUnitTest` | Unit tests, including Robolectric Compose tests |
| `./gradlew lintDebug` | Android Lint; every warning is an error |
| `./gradlew assembleDebug` | Builds `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew installDebug` | Installs on a connected phone or emulator |
| `./gradlew assembleRelease` | Per-ABI release APKs; unsigned (`*-release-unsigned.apk`) without a signing setup |
| `scripts/test-release-scripts.sh` | Tests for the release scripts (release notes extraction, version check) |

## Contributing

- **The version lives only in `gradle.properties`** (`VERSION_NAME` / `VERSION_CODE`); the app reads it from there
- ktlint decides formatting and Lint warnings are errors, so run the full check above before sending changes
- GitHub Actions (`.github/workflows/ci.yml`) runs the same check on every push and pull request
- Changes are recorded in [CHANGELOG.md](CHANGELOG.md)

### Publishing a release

Pushing a `v*` tag makes GitHub Actions (`.github/workflows/release.yml`) run the same check, build signed APKs,
attach SHA256 files and create a GitHub Release whose notes are that version's CHANGELOG section.

1. Bump the version in `gradle.properties` and the README badge, and add the version's CHANGELOG section
   (`scripts/check-version.sh` checks the badge)
2. Push the tag. It must be `v` + `VERSION_NAME` or the workflow fails; this reads the version for you:

   ```bash
   version="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)" && git tag "v$version" && git push origin "v$version"
   ```

**Before the first release**, add these secrets under the repo's *Settings → Secrets and variables → Actions*
(**never commit the signing key**; `.gitignore` excludes `*.jks` and `*.keystore`):

| Secret | Value |
|---|---|
| `OPENPRT_KEYSTORE_BASE64` | The keystore file in base64, e.g. the output of `base64 -w0 release.jks` |
| `OPENPRT_KEYSTORE_PASSWORD` | Keystore password |
| `OPENPRT_KEY_ALIAS` | Key alias |
| `OPENPRT_KEY_PASSWORD` | Key password |
| `PRT_API_KEY` (optional) | A TrueTime key built into the APK. **Leave it unset for public releases**: anyone can pull the key out of an APK, and without it the app asks each user for their own |

Create a signing key yourself (do not paste the password anywhere; **back up the keystore separately** — without
it you cannot publish updates that install over earlier versions):

```bash
keytool -genkeypair -keystore release.jks -alias openprt -keyalg RSA -keysize 4096 -validity 10000
```
