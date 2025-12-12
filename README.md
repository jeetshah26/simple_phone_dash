# Always On Dashboard (Android)

An always-on, immersive dashboard built with Kotlin + Jetpack Compose. Left side shows a live digital clock (12h) and today's calendar events; right side shows current weather, hourly forecast, and tomorrow's outlook using OpenWeather.

## Setup
- Requires Android Studio Electric Eel or newer (or Gradle 8.2.1+ with JDK 17).
- Copy `local.properties.example` to `local.properties` and set your OpenWeather API key:
  ```
  OPEN_WEATHER_API_KEY=your-key
  ```
- Permissions at runtime: location (for auto-detect), calendar read, network.

## Run on emulator
- Start an emulator or connect a device.
- From project root:
  ```
  ./gradlew installDebug
  adb shell am start -n com.example.alwaysondashboard/.MainActivity
  ```
  (First build will download Gradle + dependencies.)

## Notes
- Units auto-select based on locale (imperial for US/BS/BZ/KY, otherwise metric).
- Screen is kept awake and system bars are hidden for an always-on feel.
- Calendar events query your device calendar for the current day only.
