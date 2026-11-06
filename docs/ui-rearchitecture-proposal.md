# EMDuah Personal UI, Navigation, and Branding Proposal

## Recommendation

Wallpaper should be one feature inside EMDuah Personal, not the product’s identity. The app should become a **native personal command center**: a useful Home dashboard, a unified app launcher, and native feature modules where Android adds something the web cannot.

The web dashboard already provides the right conceptual model: **what needs attention, quick access, active work, and recent activity** (`resources/js/pages/app/dashboard.tsx:92-102`). Those priorities should be adapted to mobile rather than reproducing its desktop analytics layout.

## What is off today

- The entire app shell is **Wallpapers + Connection**, so the first feature permanently defines the product (`app/src/main/java/com/emduah/personal/ui/PersonalApp.kt:46-64`).
- “Connection” is account infrastructure, not a user goal, and does not deserve a primary navigation destination.
- The 300dp wallpaper hero and large marketing headline make the opening screen feel like a wallpaper landing page.
- Home/lock selection, preview, configuration, account, devices, and advanced JSON all live in one scrolling composition. The boolean-based `tab`/`editing` state will not scale cleanly to more features or proper back navigation.
- The green/lime palette, default sans typography, large 22–26dp cards, and text-symbol icons form a different visual language from the web app (`PersonalApp.kt:40-44`).
- The current launcher icon is a green geometric E, with no adaptive-icon treatment or deliberately designed splash experience (`app/src/main/res/drawable/ic_app.xml:1`).

## Proposed navigation

Use **two bottom destinations initially**:

1. **Home** — operational overview and quick actions.
2. **Apps** — everything available across EMDuah.

Put **Account & Settings behind the avatar** in the top app bar. Wallpaper, its editor, devices, and other feature screens become pushed destinations with normal Android back behavior.

```text
EMDuah Personal
├── Home
├── Apps
│   ├── Wallpaper                 Native
│   ├── MediaVault                Web
│   ├── Imagen                    Web
│   ├── See / QR / Aux2Vid        Web
│   └── Messaging / Phone / etc.  Web
├── Wallpaper
│   ├── Overview
│   ├── Home / Lock configuration
│   └── Live-wallpaper activation
└── Account
    ├── Profile and appearance
    ├── Connected devices
    └── Sign out
```

An Activity tab should not be added yet. Activity belongs on Home until there is enough real data to justify another destination.

## Home: useful before decorative

A returning user should see something like:

```text
EMDuah                                  [avatar]
Home
Your apps, activity, and what needs attention.

[ Needs attention ]                 Only when relevant
[ Quick access: Wallpaper · MediaVault · Imagen · QR ]

[ At a glance: 2–4 meaningful summaries ]

On this phone
[thumbnail] Wallpaper                         On
            Every 20 min · Home + Lock
            [Next now]                 [Manage]

Active work
Recent activity
```

The important shifts:

- **Needs attention comes first, but only when needed:** sign-in expired, wallpaper update failed, live wallpaper needs activation, or another task requires intervention. Each item gets one clear resolution action.
- **Quick access is task-oriented:** Open Wallpaper, Browse MediaVault, Generate an image, Analyze a document, Create a QR code. Eventually these should reflect the user’s pinned apps, as on the web dashboard.
- **Wallpaper becomes a compact native-feature card**, with a small thumbnail and useful status—not the opening hero.
- **At a glance** can later contain a small selection of the web dashboard’s metrics. Avoid shrinking all its charts and analytics onto a phone.
- **Active work and recent activity appear only when real content exists.** No permanent empty cards or fabricated metrics.
- Signed-out users can still explore the app catalog. Wallpaper configuration drafted before connecting should remain available.

This makes Home valuable even when the user does nothing with wallpapers.

## Apps: one honest, unified catalog

The Apps screen should have:

- A pinned section.
- **On this phone:** native capabilities, initially Wallpaper.
- **Web apps:** MediaVault, Imagen, See, QR, YouTube, Aux2Vid, WhatsApp, Phone, and others defined by the web product.
- Familiar app icons, short descriptions, and a clear external-link indicator for web destinations.
- Search only once the catalog is large enough to benefit from it.

Web tools should open in a browser Custom Tab, not turn the app into a WebView wrapper. Native and web entries should look related but never pretend to offer the same integration.

The web’s catalog already lives in `config/modern.php:6-73`. Longer term, expose a mobile-friendly version of that catalog so names, URLs, availability, and icons do not drift between platforms.

## Wallpaper as a polished feature module

Wallpaper gets its own focused destination rather than disappearing:

- **Overview:** Home/Lock selector, current preview, rotation state, schedule, filter summary, **Next now**, and **Configure**.
- **Configuration:** organized into Source, Filters, and Rotation & Data.
- Keep bucket, media type, ordering, dates, rating, and path filters approachable.
- Move raw filter JSON into a clearly labeled **Expert filters** screen or disclosure.
- Show live-wallpaper activation only when the selected setup requires Android’s system picker.
- Give explicit states for queued, downloading, applied, paused, failed, and activation required.
- Preserve the current reliable behavior: failed updates leave the previous wallpaper in place.

“Next now” should acknowledge the request immediately and update the preview when Android actually applies the media. That feedback loop will feel more delightful than additional decoration.

## Brand and visual system

Mobile should match the web, which has two complementary aesthetics:

- **Dashboard:** neutral monochrome, compact cards, restrained borders, Lato typography (`resources/css/modern-app.css:16-125`).
- **Authentication/brand moments:** warm paper, dark ink, and the orbital creative artwork (`resources/js/features/auth/creative-orbit.tsx:4-6`).

Use them this way:

- **Product name:** `EMDuah Personal`; use the web’s `EMDuah` casing consistently.
- **Typography:** Lato for headings and body, Inconsolata sparingly for technical values.
- **Light theme:** white background, near-black foreground, subtle zinc borders and muted surfaces.
- **Dark theme:** near-black background with elevated zinc surfaces—not green.
- **Brand accent:** warm paper `#F3F1EC` and ink `#292927` for welcome, onboarding, and selected empty states.
- **Color semantics:** green means success, amber attention, red failure. Lime should no longer be the primary brand color.
- **Shape:** approximately 12dp cards, thinner borders, less elevation, and tighter type hierarchy.
- **Icons:** one consistent outlined vector family, replacing `◈`, `◎`, and arrow-character buttons.
- Let wallpaper imagery supply expressive color while the interface remains quiet.

## Launcher icon and splash

The web dashboard’s actual primary mark is the simple **square E monogram** (`resources/js/components/app/app-shell.tsx:247-250`). The mobile icon should build from that rather than invent another logo.

- Adaptive Android icon with separate foreground and background layers.
- Light, dark, and monochrome/themed-icon support.
- Simplified E that remains legible at small sizes.
- Full `EMDuah` wordmark inside the app, not squeezed into the launcher icon.
- Reserve orbital lines and braces as supporting artwork, not tiny launcher details.

For splash:

- Warm paper in light mode, matching neutral dark background in dark mode.
- Centered E monogram with a restrained orbital-line detail.
- Use Android’s system splash and transition directly into Home or first-run sign-in.
- No artificial delay, video, or long brand animation; motion stops when reduced motion is requested.

## Technical shape and delivery

Split the current monolithic `PersonalApp.kt` into an app shell, explicit destinations, feature screens, and feature-level state holders, while keeping the existing wallpaper renderer, scheduler, and secure session storage intact.

One important constraint: the current mobile token has only the `mediavault:wallpaper` capability. Real cross-app dashboard metrics require a proper mobile overview/catalog contract; the token should not be injected into web views to simulate single sign-on.

Suggested rollout:

1. **Foundation:** brand theme, adaptive icon, splash, navigation shell, Home and Apps, Wallpaper refactored into a module using existing local data.
2. **Dashboard integration:** mobile catalog/overview data, real active work and activity, synchronized pinned apps.
3. **Expansion:** additional native modules where Android provides genuine value, plus deeper notifications and shortcuts.

Throughout: preserve scroll and edit state on back navigation, support dynamic type and 48dp touch targets, provide useful offline/stale states, and keep motion subtle.
