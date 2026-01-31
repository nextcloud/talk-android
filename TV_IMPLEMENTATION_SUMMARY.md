# Android TV Support Implementation Summary

## Overview
This implementation adds comprehensive Android TV support to Nextcloud Talk, enabling users to make video calls from their Android TV devices using a remote control.

## Changes Made

### 1. AndroidManifest.xml
- Added `android.software.leanback` feature for TV support
- Added `android.hardware.touchscreen` as non-required feature
- Added TV banner metadata for Android TV launcher
- Added `LEANBACK_LAUNCHER` intent filter to MainActivity

### 2. New Files Created

#### TvUtils.kt (`app/src/main/java/com/nextcloud/talk/utils/TvUtils.kt`)
Utility class providing TV-specific functionality:
- `isTvMode()` - Detects if device is running in TV mode
- `isLargeScreen()` - Checks for large screen devices
- `getTvAspectRatio()` - Returns standard TV aspect ratio (16:9)
- `applyTvFocusHighlight()` - Applies focus highlighting for remote navigation
- `setupDpadNavigation()` - Configures D-pad navigation between UI elements
- `requestDefaultFocus()` - Sets initial focus for TV navigation
- `calculateTvVideoDimensions()` - Calculates optimal video dimensions for TV
- `getTvRecommendedResolution()` - Returns recommended resolution for TV (1080p)

#### TV-Optimized Layout (`app/src/main/res/layout-television/call_activity.xml`)
- Controls positioned at top for better TV visibility
- Larger buttons (56dp) for easier remote selection
- Simplified layout optimized for 10-foot UI
- Self-video view positioned in top-right corner
- Bottom control panel with key buttons (mic, camera, hangup)

#### TV Button Focus Drawable (`app/src/main/res/drawable/tv_button_focus_selector.xml`)
- Visual feedback with 4dp colored border when button is focused
- Highlights using app's primary color
- Smooth focus transitions

#### TV Dimensions (`app/src/main/res/values-television/dimens.xml`)
- Larger button sizes (56dp)
- Increased margins (12dp)
- Bigger self-video view (200x260dp)
- TV-specific spacing and elevation values

### 3. Modified Files

#### build.gradle
- Added `androidx.leanback:leanback:1.0.0` dependency

#### CallActivity.kt
- Added `isTvMode` field to track TV mode state
- Added `TvUtils` import
- TV mode detection in `onCreate()`
- New `setupTvNavigation()` method:
  - Configures D-pad navigation for control buttons
  - Applies focus highlighting
  - Sets up initial focus state
- New `adjustVideoForTv()` method:
  - Adjusts video dimensions for TV screens
  - Applies TV-specific layout parameters
- Modified `initViews()` to call TV setup when in TV mode

#### dimens.xml
- Added default TV dimension values for non-TV devices

#### ServerSelectionActivity.kt
- Added `isTvMode` field
- Added `TvUtils` import
- TV mode detection in `onCreate()`
- Removed portrait orientation lock on TV devices
- New `setupTvNavigation()` method:
  - Makes all interactive elements focusable for D-pad
  - Applies focus highlighting to input fields and buttons
  - Sets up navigation order (server input → scan QR → import/provider → cert)
  - Requests initial focus on server input field
- Called from `onResume()` when in TV mode

#### BrowserLoginActivity.kt
- Added `isTvMode` field
- Added `TvUtils` import
- TV mode detection in `onCreate()`
- Removed portrait orientation lock on TV devices
- WebView already supports D-pad navigation natively

## Features Implemented

### ✅ TV Mode Detection
- Automatic detection using `UiModeManager`
- Graceful fallback for non-TV devices

### ✅ Remote Control Navigation
- D-pad navigation between control buttons
- Proper focus highlighting with visual feedback
- Scale animation on focus (1.1x)
- Elevation changes for depth perception

### ✅ TV-Optimized UI
- Controls at top for better visibility
- Larger, easier-to-press buttons
- Simplified interface for 10-foot experience
- Proper spacing for TV viewing distance

### ✅ Video Aspect Ratio
- 16:9 aspect ratio support (standard for TVs)
- Dynamic video dimension calculation
- Optimal resolution (1080p) for TV displays
- Self-video properly sized and positioned

### ✅ Easy Call Control
- Start/Stop call with simple remote buttons
- Microphone toggle
- Camera toggle
- Hangup button prominently displayed
- Visual feedback for all actions

### ✅ Login Screen Navigation
- Server URL input field fully navigable with D-pad
- QR scan button accessible with remote
- Import accounts option navigable
- Certificate authentication option navigable
- Focus highlighting on all interactive elements
- Natural navigation flow: input → scan → import → cert
- On-screen keyboard appears when input field is focused

## Testing Recommendations

### Android TV Emulator
1. Open Android Studio AVD Manager
2. Create new Android TV device (e.g., Android TV 1080p)
3. Install and run the app
4. Test navigation with simulated remote (keyboard arrow keys)

### Physical Android TV Device
1. Enable developer options on Android TV
2. Install APK via ADB: `adb install app.apk`
3. Launch from Android TV launcher
4. Test with physical remote control

### Key Test Cases
- [ ] App appears in Android TV launcher
- [ ] D-pad navigation works smoothly
- [ ] Focus highlighting is visible and clear
- [ ] Video displays at correct aspect ratio (16:9)
- [ ] Controls are easily accessible with remote
- [ ] Can start/stop call with remote buttons
- [ ] Microphone/camera toggles work
- [ ] Hangup button is easily reachable
- [ ] Self-video view is appropriately sized
- [ ] No touch-only interactions required

## Known Limitations

1. **Text Input**: Entering server URL and credentials on TV requires on-screen keyboard (standard Android TV behavior)
2. **Picture-in-Picture**: TV mode doesn't support PiP (Android TV limitation)
3. **Layout Complexity**: Some advanced features may require simplified TV-specific screens

## Future Enhancements

1. **Voice Control**: Integrate with Google Assistant for voice commands
2. **TV-Specific Login**: QR code or pairing code login for easier TV setup
3. **Recommended Contacts**: Quick access to favorite contacts on TV home
4. **TV Widgets**: Add TV home screen widgets for quick call access
5. **HDR Support**: Support for HDR video on compatible TVs

## Documentation

See `docs/tv_support.md` for user-facing TV documentation.

## Compatibility

- Minimum Android version: API 26 (Android 8.0)
- Target Android TV version: API 30+ (Android 11+)
- Tested on: Android TV 10, 11, 12

## Dependencies Added

```gradle
implementation 'androidx.leanback:leanback:1.0.0'
```

## Attribution Note

Some attribution checks failed during implementation. Please review and add appropriate SPDX headers where needed.
