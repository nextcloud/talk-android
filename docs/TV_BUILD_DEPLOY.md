# Building and Deploying Nextcloud Talk for Android TV

## Build Requirements
- Android Studio Arctic Fox or later
- Gradle 7.0+
- Android SDK API 36
- Kotlin 2.3.0+

## Build Steps

### 1. Clone Repository
```bash
git clone https://github.com/nextcloud/talk-android.git
cd talk-android
```

### 2. Build APK
```bash
./gradlew assembleGplayRelease
```

### 3. Install on Android TV
```bash
adb connect <TV_IP_ADDRESS>
adb install app/build/outputs/apk/gplay/release/app-gplay-release.apk
```

## Testing on Android TV Emulator

1. Open AVD Manager
2. Create Android TV device (1080p)
3. Launch emulator
4. Install APK via drag-and-drop

## Deployment Checklist

- [ ] Test on Android TV emulator
- [ ] Test on physical Android TV device
- [ ] Verify remote navigation
- [ ] Test video quality
- [ ] Verify all permissions work
- [ ] Test call start/stop

## TV-Specific Build Variants

The app automatically detects TV mode at runtime. No separate TV build needed.

## Troubleshooting

- If app doesn't appear in TV launcher, verify leanback feature in manifest
- For ADB connection issues, enable debugging in TV developer options
- For focus issues, check TvUtils.setupDpadNavigation() is called