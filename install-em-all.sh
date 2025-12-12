GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew --no-daemon assembleDebug
adb -s emulator-5554 uninstall com.example.alwaysondashboard
adb -s emulator-5554 install ./app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.alwaysondashboard/.MainActivity

adb -s e0144b73 uninstall com.example.alwaysondashboard
adb -s e0144b73 install ./app/build/outputs/apk/debug/app-debug.apk
adb -s e0144b73 shell am start -n com.example.alwaysondashboard/.MainActivity

