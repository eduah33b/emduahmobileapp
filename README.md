# Emduah personal app

A native Kotlin / Jetpack Compose Android app for personal features, with MediaVault wallpaper rotation as its first feature. It is a separate app rather than a wrapper around emduahwebapp. Android 14+ is required for public home/lock live-wallpaper APIs; the connected Samsung Galaxy A34 runs Android 16.

## Current features

- One shared configuration and downloaded file for home and lock screens by default. The Lock screen tab can enable an independent configuration with its own filters, interval and limits.
- Still image rotation (JPEG, PNG, WebP and other device-supported formats), or a live wallpaper that renders images and silently loops videos. Each screen can use either mode. Live video is decoded with the phone's native codecs.
- Native sign-in with an existing verified web account, encrypted access/refresh storage in Android Keystore, automatic token renewal, sign-out and a signed-in device list with remote revocation. Password recovery and verification reuse canonical web account flows. Passwords are never persisted, credentials are excluded from backup, and storage requests do not receive API tokens.
- Filter controls for sorting and MediaVault stable shuffle, path, directory, favorites, exact rating/status, bucket, tag and category. Advanced JSON supports the current MediaVault filter contract, including rating/status arrays, metadata search/relevance, captions, model/LoRA, subreddit, dates and processing state. Trashed, missing and disabled-bucket assets are excluded. Stable shuffle uses the same seed and MD5 order as MediaVault, persists the last applied item, and traverses the sequence before wrapping. Changing the seed reshuffles and starts a new pass.
- Independent configurable intervals (15 minutes to 7 days; default 20 minutes), unmetered-network preference, download limits (1–1024 MiB; default 120) and video playback limits (1–3600 seconds; default 60; 0 plays the full video).
- One streamed download at a time across both screens. Only the current file per screen is retained, plus a temporary replacement during an update. Failed downloads leave the previous wallpaper in place; bounded retries handle transient failures. The server skips known oversized assets, and streaming enforces the cap even without Content-Length.
- Durable WorkManager schedules survive process death and reboot. Android controls precise timing and may defer work for idle mode, low battery or network constraints. Force-stopping the app prevents work until it is opened again.

## Running

Open this folder in Android Studio, use JDK 17 and install Android SDK 35. Set `sdk.dir` in the untracked `local.properties` if needed.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.emduah.personal/.MainActivity
```

The app's default server is `https://mediavault.emduah.xyz`. Its backend API is prepared in `/Users/emmanuelduah/www/emduahwebapp` on branch `feature/mobile-wallpaper-api`, under `/api/mobile/v1`. The user deployed the initial API on 6 November 2026. Sign-in, private filter options and original-media downloads have been verified on the phone. A subsequent stable-shuffle API change is prepared on the same review branch but has not been deployed by this task; review and deploy it with this app version. The app and API use one contract, with no legacy random-draw mode. See the web repository's `docs/mobile-wallpaper-api.md` for security, endpoint details and deployment prerequisites. You can configure and save paused settings before connecting.

For images, start rotation after connecting. For images/videos in live mode, tap **Open system wallpaper picker**, then **Set wallpaper → Home screen and lock screen**. The Samsung picker was checked and offers Home, Lock, and Both. Android requires this explicit user activation; the app cannot silently install a live wallpaper. The renderer uses per-screen engine flags and, for a shared engine, lock state to select the correct media. Enable **Separate lock settings** to use an independent lock configuration. For mixed setups the live engine can also display still images, so downloading an image does not replace an already active provider.

Videos play their configured initial segment and loop silently; they are not transcoded or trimmed on disk. Decoder support and battery use vary by media/device. Long files may be excluded by the configurable download limit. Periodic rotation does not promise exact wall-clock times.

## Project layout

- `data/`: auth/MediaVault transport, encrypted session storage, settings and media models.
- `wallpaper/`: background scheduler/worker, bounded image decode/crop, unified EGL image/video renderer and home/lock routing.
- `ui/`: app shell, connection/account flow, wallpaper dashboard and configuration forms.

New personal features can add a UI destination and their own feature package, while sharing the account/session transport. MediaVault query logic stays on the web server.

## Validation

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Eleven JVM tests exercise home/lock routing, filter preservation, limit validation, HTTPS origin validation, authenticated API requests, token isolation from storage, chunked download limits redirect rejection, shared live-wallpaper inheritance, and stable-shuffle request/response validation. Five instrumentation tests pass on the Samsung A34: Keystore credential encryption/round-trip, JPEG/PNG/WebP decoding and crop, independent profile limits, and HTTPS access-token renewal with rotating credentials using an isolated mock server. A GPU rendering test verifies image → advancing video frames → image on the same surface. Build and Android lint pass. Instrumentation uses separate preference files and no real account.

Live image selection, replacement, bounded video downloads, and GPU image/video transitions have been checked. Android confirmed both-screen image application, and only one current file was retained. Periodic timing, independent lock-screen playback and stable shuffle against the pending API update still need their final integration checks. The API was deployed by the user; no server deployment was performed by this mobile-app task.


## Live wallpaper rendering

Images and video use one EGL producer. Still images are uploaded as 2D textures; MediaPlayer decodes silent video into an external SurfaceTexture consumed by the same renderer. This permits image/video transitions without reconnecting incompatible producers to Android’s wallpaper surface. Rendering and media callbacks run on one graphics thread; playback pauses while hidden. The worker also recognizes a lock screen inheriting the home live provider without a separate WallpaperInfo/ID, as observed on the Samsung A34.
