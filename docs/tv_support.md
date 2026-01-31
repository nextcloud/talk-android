# Android TV Support

## Overview
Nextcloud Talk now supports Android TV with optimized UI and remote control navigation.

## Features
- TV mode detection
- D-pad/remote navigation with focus highlighting
- TV-optimized layouts (controls at top)
- 16:9 aspect ratio video
- Larger buttons for TV

## Implementation

### Key Files
- `TvUtils.kt` - TV detection and navigation utilities
- `layout-television/call_activity.xml` - TV-specific layout
- `values-television/dimens.xml` - TV dimensions
- `drawable/tv_button_focus_selector.xml` - Focus highlighting

### Usage
App automatically detects TV mode and adjusts UI. Remote control navigation is enabled with proper focus highlighting.

## Testing
Test on Android TV emulator or physical Android TV device.