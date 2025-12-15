# Always On Dashboard (Android)

An always-on, immersive dashboard built with Kotlin + Jetpack Compose. Left side shows a live digital clock (12h) and today's calendar events; right side shows current weather, hourly forecast, and tomorrow's outlook using OpenWeather.

## Features
- Clock panel: 12h time/date, auto dark/light based on sunrise/sunset (manual toggle still available), battery indicator.
- Weather: current, hourly, tomorrow, running-conditions indicator, unit swap (F/C), 15‑minute auto refresh, last updated time on clock card.
- Calendar: tabs for Today / Upcoming, expired events pruned as the day progresses, auto-defaults to Upcoming if Today is empty; day change triggers refresh.
- Always-on layout: immersive, keeps screen awake; locale-based unit defaults.

## Setup (local dev)
- Requires JDK 17 and Gradle 8.2.1+ (Android Studio Giraffe+ recommended).
- Copy `local.properties.example` to `local.properties` and set your OpenWeather key:
  ```
  OPEN_WEATHER_API_KEY=your-key
  sdk.dir=/path/to/android/sdk
  ```
  (Release builds from CI intentionally ship without the weather key.)
- Permissions: location (auto-location), calendar read, network.

## Run on emulator/device
```
./gradlew installDebug
adb shell am start -n com.example.alwaysondashboard/.MainActivity
```

## CI release APK (GitHub Actions)
- Workflow: `.github/workflows/build-release.yml`.
- Secrets required (repository secrets):
  - `ANDROID_KEYSTORE_BASE64` (base64 of `release.keystore`)
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- The workflow builds a signed release APK and uploads it as an artifact. It does **not** embed `OPEN_WEATHER_API_KEY` (local.properties is generated empty in CI).
