# Home switcher

Small companion app for Lawnchair development: tapping its icon makes the next installed
Lawnchair build (e.g., release or debug) the default home app and goes to it. It is a
development aid, which is separate from the launcher itself.

Android only lets privileged callers change the default home app without a confirmation
screen. The switcher gets that privilege through [Shizuku](https://shizuku.rikka.app), which
gives apps the rights of `adb shell` once it has been started. It runs the equivalent of

    adb shell cmd package set-home-activity <package>/<activity>

When Shizuku is not running (or permission is denied), the system's "Default home app"
screen is opened instead, so the switch then takes one more tap.

## Setup

1. Build and install: `./gradlew :home-switcher:installDebug`
2. Install Shizuku and start it. Without root, this uses Wireless debugging on the device
   itself (Android 11+), which needs Wi-Fi but no computer. See the Shizuku user manual.
3. Tap "Home switcher" and allow it in the Shizuku prompt (first time only).

Shizuku stops when the device restarts, so step 2 needs repeating after each reboot.

## Notes

- Lawnchair builds are found by package name (i.e., `app.lawnchair` or `app.lawnchair.*`).
- Diagnostics are logged under the `HomeSwitcher` tag.
- Unit tests: `./gradlew :home-switcher:testDebugUnitTest`

Created with the help of Claude Code using model claude-fable-5-2.
