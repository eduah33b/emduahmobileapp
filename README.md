# EMDuah

## App experience

EMDuah is a native personal app. Sign in with your existing verified account on first use; saved sessions take you straight to Home on later launches. The interface follows the web dashboard’s Lato typography, neutral light/dark surfaces, and E monogram, with warm paper and ink for sign-in and launch branding.

- **Home:** a compact overview of wallpaper rotation and a shortcut to manage it.
- **Apps:** the features available on this phone. Currently this contains only **Wallpapers**.
- **Wallpapers:** previews for home and lock screens, rotation controls, and focused configuration for media, filters, schedule, and data preferences.
- **Settings:** a small entry in the app bar for the existing connection and signed-in device controls. Account management stays out of the main navigation.

Back returns to the place you opened a feature from. Configuration keeps its own screen and asks before discarding unsaved edits. The app follows the phone’s light/dark appearance. The launcher icon and system splash use the same EMDuah identity.

This redesign changes presentation and navigation. It does not add a web-app catalog, browser shortcuts, dashboard APIs, analytics, or additional features. Existing wallpaper downloads, scheduling, playback, filters, secure sessions, and home/lock behavior remain the foundation.

A native Kotlin / Jetpack Compose Android app for personal features, with MediaVault wallpaper rotation as its first feature. It is a separate app rather than a wrapper around emduahwebapp. Android 14+ is required for public home/lock live-wallpaper APIs; the connected Samsung Galaxy A34 runs Android 16.

## Current features

- One shared configuration and downloaded file for home and lock screens by default. The Lock screen tab can enable an independent configuration with its own filters, interval and limits.
- Still image rotation (JPEG, PNG, WebP and other device-supported formats), or a live wallpaper that renders images and silently loops videos. Each screen can use either mode. Live video is decoded with the phone's native codecs.
- Inline video previews play the current downloaded home/lock media silently, with play/pause controls and the configured playback cap. Preview playback pauses when the app is backgrounded or the card leaves the viewport; leaving the wallpaper page releases the preview player. It uses the same cropped video renderer as the live wallpaper and makes no additional media download.
- Native sign-in with an existing verified web account, encrypted access/refresh storage in Android Keystore, automatic token renewal, sign-out and a signed-in device list with remote revocation. Password recovery and verification reuse canonical web account flows. Passwords are never persisted, credentials are excluded from backup, and storage requests do not receive API tokens.
- Basic filters are bucket, ordering, media type (images, videos, or both), minimum/maximum modified dates (either can be omitted), minimum rating, and path search with `-term` exclusions. A minimum of 3 stars sends ratings `[3,4,5]` to MediaVault. Advanced contains directory/path matching mode, favorites, review status, tags/categories, shuffle seed, and additional MediaVault JSON filters. Schedule, network preference and download/playback limits have their own visible section. Additional JSON is kept separate from the basic controls so it cannot overwrite them. Trashed, missing and disabled-bucket assets are excluded. Stable shuffle uses the same seed and MD5 order as MediaVault, persists the last applied item, and traverses the sequence before wrapping. Changing the seed reshuffles and starts a new pass.
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

The app's default server is `https://mediavault.emduah.xyz`. Its backend API is prepared in `/Users/emmanuelduah/www/emduahwebapp` on branch `feature/mobile-wallpaper-api`, under `/api/mobile/v1`. The user deployed the initial API on 6 November 2026. Sign-in, private filter options and original-media downloads have been verified on the phone. A subsequent stable-shuffle API change is prepared on the same review branch but has not been deployed by this task; review and deploy it with this app version. The app and API use one contract, with no legacy random-draw mode. See the web repository's `docs/mobile-wallpaper-api.md` for security, endpoint details and deployment prerequisites. After signing in, open Wallpapers from Home or Apps to configure it. Saving changes preserves whether rotation is running or paused; the rotation switch starts or pauses it.

For images, start rotation after connecting. For images/videos in live mode, tap **Open system wallpaper picker**, then **Set wallpaper → Home screen and lock screen**. The Samsung picker was checked and offers Home, Lock, and Both. Android requires this explicit user activation; the app cannot silently install a live wallpaper. The renderer uses per-screen engine flags and, for a shared engine, lock state to select the correct media. Enable **Separate lock settings** to use an independent lock configuration. For mixed setups the live engine can also display still images, so downloading an image does not replace an already active provider.

Videos play their configured initial segment and loop silently; they are not transcoded or trimmed on disk. Decoder support and battery use vary by media/device. Long files may be excluded by the configurable download limit. Periodic rotation does not promise exact wall-clock times.

## Project layout

- `data/`: auth/MediaVault transport, encrypted session storage, settings and media models.
- `wallpaper/`: background scheduler/worker, bounded image decode/crop, unified EGL image/video renderer and home/lock routing.
- `ui/`: themed app shell and navigation, first-run sign-in, Home and Apps, wallpaper overview/editor, and existing account controls.
- `res/font/`: bundled Lato fonts; the font license ships in `assets/licenses/lato-OFL.txt`.

New personal features can add a UI destination and their own feature package, while sharing the account/session transport. MediaVault query logic stays on the web server.

## Validation

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Nineteen JVM tests exercise home/lock routing, filter preservation, limit validation, HTTPS origin validation, authenticated API requests, token isolation from storage, chunked download limits, redirect rejection, shared live-wallpaper inheritance, stable-shuffle request/response validation, minimum-rating expansion, open-ended date ranges, date validation separation of additional filters, and navigation back to the originating tab. Ten device tests pass on the Samsung A34: four cover Keystore encryption, image decoding/crop, independent profile limits, and HTTPS token renewal; one verifies image → advancing video frames → image on the same GPU surface; five cover first-run sign-in, Home/Apps navigation, editor draft restoration/discard, and light mode with enlarged text. Debug build and Android lint pass. Dark and light screenshots were reviewed, including sign-in and Home at 150% text size. Instrumentation uses separate preference files and no real account; it does not change the signed-in phone’s wallpaper settings. To preserve a signed-in phone during instrumentation, build with `:app:assembleDebugAndroidTest`, install the app and test APKs using `adb install -r`, and run `adb shell am instrument -w com.emduah.personal.test/androidx.test.runner.AndroidJUnitRunner`; the Gradle connected-test task may uninstall the app. Reapply the live wallpaper through Android’s picker after instrumentation or an app update if Android clears the provider.

Live image selection, replacement, bounded video downloads, and GPU image/video transitions have been checked. Android confirmed both-screen image application, and only one current file was retained. A scheduled home update subsequently completed successfully with the seeded API response contract. Independent lock-screen video was verified with Android reporting the keyguard visible and the hardware decoder playing; playback logs confirmed the five-second test cap seeking back to the start and playback pausing when hidden. Home and lock retained different asset IDs and exactly one local file each. Full-cycle stable-shuffle traversal is covered by API tests; the whole live catalog has not been traversed on the phone. The API was deployed by the user; no server deployment was performed by this mobile-app task.


## Live wallpaper rendering

Images and video use one EGL producer. Still images are uploaded as 2D textures; MediaPlayer decodes silent video into an external SurfaceTexture consumed by the same renderer. This permits image/video transitions without reconnecting incompatible producers to Android’s wallpaper surface. Rendering and media callbacks run on one graphics thread; playback pauses while hidden. The worker also recognizes a lock screen inheriting the home live provider without a separate WallpaperInfo/ID, as observed on the Samsung A34.

## Final live test configuration

The connected phone was left with separate lock settings enabled: home uses the video bucket, stable shuffle, a 20-minute interval and a 60-second playback cap; lock uses the video bucket, newest modified first, a 20-minute interval and a five-second playback cap. Both profiles use unmetered networks and a 120 MiB download cap. The five-second lock cap was a test setting and can be changed in Configure.

The inline previews were also checked on the Samsung A34: home and lock previews decoded video with zero audio volume; manual pause/play worked; navigating away from Wallpapers released the preview player; backgrounding the app paused the preview while the actual wallpaper resumed. Returning to the app resumed the preview independently.
