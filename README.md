# LockGlow

A Kotlin Android app that shows a Samsung-style glowing fingerprint scanner
the instant your screen turns on, and unlocks in place with your phone's
biometric hardware. Fully offline — no `INTERNET` permission is declared
anywhere in the app.

## How it works

Modern Android (8+) doesn't let a normal third-party app fully replace the
system keyguard — that's locked down for security reasons. What LockGlow
does instead (the same approach every fingerprint-lock-style app on the
Play Store uses) is:

1. A **foreground service** (`LockOverlayService`) listens for `SCREEN_ON`.
2. The instant the screen wakes, it draws a **transparent full-screen
   overlay** on top of everything — your real wallpaper stays fully
   visible, only the glow scanner element and a hint label are drawn.
3. Tapping the glow (or it auto-firing on screen-on) launches a tiny
   invisible activity that hosts the real Android `BiometricPrompt` dialog
   (a bare `Service` can't host it directly — that's an Android
   restriction, not a LockGlow one).
4. On success, the overlay is removed and you're in.
5. On **3 failed attempts**, the overlay is removed and your phone's own
   built-in lock screen (already underneath) takes over.

## Project structure

- `MainActivity` — first-run setup wizard: overlay permission → notification
  permission → battery-optimization exemption → fingerprint enrollment
  check → activate.
- `LockOverlayService` — the always-on foreground service; owns the overlay
  window and the failed-attempt counter.
- `BiometricUnlockActivity` — invisible trampoline that hosts the actual
  biometric prompt.
- `FingerprintGlowView` — the glow/ring animation (dim idle breathing glow,
  snaps bright on touch, green flash on success, red flash on failure).
- `ScreenStateReceiver` / `BootReceiver` — screen-on/off handling and
  auto-restart after reboot.

## Building locally

```
./gradlew assembleDebug
```

(You'll need the Android SDK installed; Android Studio handles that for you.)

## Building a release via GitHub Actions

Push a tag and the workflow in `.github/workflows/release.yml` builds the
APK and attaches it to a new GitHub Release automatically:

```
git tag v1.0
git push origin v1.0
```

You can also trigger it manually from the **Actions** tab
("Build APK and Release" → **Run workflow**).

## Permissions used, and why

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the glow overlay on top of other apps/the lock screen |
| `USE_BIOMETRIC` | Trigger the fingerprint prompt |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the lock service alive |
| `RECEIVE_BOOT_COMPLETED` | Resume protection after reboot |
| `POST_NOTIFICATIONS` | Required on Android 13+ to show the foreground-service notification |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask not to be killed in the background |

No `INTERNET` permission — the app cannot make network calls even if it wanted to.

## Known limitation

Because true keyguard replacement isn't available to third-party apps, on
some devices there may be a brief flash of the real system lock screen
before LockGlow's overlay draws on screen-on. This is an Android platform
constraint, not a bug.
