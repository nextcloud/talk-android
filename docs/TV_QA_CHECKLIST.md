# Android TV - QA Testing Checklist

## Setup & Installation

- [ ] App appears in Android TV launcher after installation
- [ ] App icon/banner displays correctly in TV UI
- [ ] First-time setup wizard works with remote control
- [ ] Can enter server URL using on-screen keyboard
- [ ] Can enter credentials using on-screen keyboard
- [ ] Permissions dialog works with remote control

## Login Screen (Server Selection)

### Navigation
- [ ] Server URL input field is focusable with D-pad
- [ ] Can navigate to Scan QR button with D-pad
- [ ] Can navigate to import accounts link with D-pad
- [ ] Can navigate to certificate auth link with D-pad
- [ ] Navigation order is logical (input → scan → import → cert)
- [ ] Focus indicator visible on all elements

### Focus Highlighting
- [ ] Server input field shows focus border when selected
- [ ] Scan QR button highlights when focused
- [ ] Import accounts text highlights when focused
- [ ] Certificate auth text highlights when focused
- [ ] Focus transitions smoothly between elements

### Functionality
- [ ] On-screen keyboard appears when input field focused
- [ ] Can type server URL with on-screen keyboard
- [ ] Can submit server URL with OK button
- [ ] Progress indicator shows when checking server
- [ ] Error messages are visible and readable
- [ ] Can select Scan QR with OK button
- [ ] Can select import accounts with OK button
- [ ] Can select certificate auth with OK button

### Browser Login Screen
- [ ] WebView loads correctly on TV
- [ ] Can navigate login form with D-pad
- [ ] Username field is focusable
- [ ] Password field is focusable
- [ ] Login button is focusable
- [ ] On-screen keyboard works for credentials
- [ ] Can complete login with remote only

## Navigation

### Remote Control D-pad
- [ ] Left arrow key moves focus left
- [ ] Right arrow key moves focus right
- [ ] Up arrow key navigates up (where applicable)
- [ ] Down arrow key navigates down (where applicable)
- [ ] OK/Select button activates focused item
- [ ] Back button returns to previous screen

### Focus Indicators
- [ ] Focused button has visible border highlight
- [ ] Focused button scales up slightly (1.1x)
- [ ] Focus indicator color matches app theme
- [ ] Focus transitions smoothly between buttons
- [ ] Focus state is clear and visible from 10 feet away

## Call Interface - TV Mode

### Layout Verification
- [ ] Controls are positioned at bottom of screen
- [ ] Main video area fills most of screen
- [ ] Self-video appears in top-right corner
- [ ] Text is readable from across the room
- [ ] Call duration displays at top
- [ ] Participant names are visible

### Video Display
- [ ] Video displays at 16:9 aspect ratio
- [ ] Video fills TV screen appropriately
- [ ] No black bars (or minimal letterboxing if needed)
- [ ] Self-video window sized correctly (200x260dp on TV)
- [ ] Remote participant videos render correctly
- [ ] Screen share works and displays properly

### Button Controls
- [ ] Microphone button is accessible
- [ ] Camera button is accessible
- [ ] Hangup button is accessible
- [ ] All buttons are 56dp in TV mode
- [ ] Button icons are clear and recognizable
- [ ] Button states update correctly (on/off)

## Functionality

### Starting a Call
- [ ] Can navigate to start call button
- [ ] Can select contact with remote
- [ ] Can initiate video call
- [ ] Can initiate voice-only call
- [ ] Camera activates when call starts
- [ ] Microphone activates when call starts

### During Call
- [ ] Can toggle microphone on/off
- [ ] Can toggle camera on/off
- [ ] Can end call with hangup button
- [ ] Audio is clear and synchronized
- [ ] Video is smooth (30fps target)
- [ ] No lag in remote control response

### Call Quality
- [ ] Video resolution appropriate for TV (1080p preferred)
- [ ] Frame rate is smooth (minimum 24fps)
- [ ] Audio is clear without distortion
- [ ] Lip sync is accurate
- [ ] Network quality indicators work

## Device Compatibility

### Android TV Versions
- [ ] Works on Android TV 10
- [ ] Works on Android TV 11
- [ ] Works on Android TV 12
- [ ] Works on Android TV 13+

### Hardware Configurations
- [ ] Works with built-in TV camera
- [ ] Works with USB camera
- [ ] Works with built-in TV microphone
- [ ] Works with USB microphone
- [ ] Works with Bluetooth headset
- [ ] Works with TV speakers

## Performance

- [ ] App launches within 3 seconds
- [ ] Call connects within 5 seconds
- [ ] No visible lag when navigating with remote
- [ ] Memory usage is reasonable (<500MB)
- [ ] No crashes during 30-minute call
- [ ] Battery drain is acceptable (if applicable)

## Regression Testing

### Non-TV Devices (Ensure no breakage)
- [ ] Still works normally on phones
- [ ] Still works normally on tablets
- [ ] Layout switches correctly based on device type
- [ ] Touch interaction still works on touch devices
- [ ] No TV-specific code breaks mobile UI

## Edge Cases

- [ ] Handles loss of camera during call
- [ ] Handles loss of microphone during call
- [ ] Handles network disconnection gracefully
- [ ] Handles call timeout appropriately
- [ ] Handles multiple participants correctly
- [ ] Handles screen rotation (if TV supports)
- [ ] Handles incoming call notifications

## Accessibility

- [ ] Buttons meet minimum touch target size (48dp)
- [ ] Color contrast meets WCAG standards
- [ ] Focus order is logical
- [ ] No critical features require touch
- [ ] Works with TalkBack (screen reader)
- [ ] Works with external accessibility devices

## Specific TV Features

- [ ] Focus highlighting works in all call states
- [ ] D-pad navigation never gets stuck
- [ ] Can always reach hangup button
- [ ] Self-video doesn't block important content
- [ ] Controls auto-hide after inactivity (if applicable)
- [ ] Controls reappear when remote is used

## Known Issues to Verify Fixed
- [ ] [Add any known issues here]

## Device-Specific Tests

### TCL Android TV
- [ ] All features work
- [ ] Remote control fully functional
- [ ] Video quality good

### Sony Bravia
- [ ] All features work
- [ ] Remote control fully functional
- [ ] Video quality good

### NVIDIA Shield
- [ ] All features work
- [ ] Remote control fully functional
- [ ] Video quality good

### Xiaomi Mi Box
- [ ] All features work
- [ ] Remote control fully functional
- [ ] Video quality good

## Sign-off

- **Tester Name**: _______________
- **Test Date**: _______________
- **Build Version**: _______________
- **Device**: _______________
- **Android TV Version**: _______________
- **Overall Result**: ⬜ PASS ⬜ FAIL ⬜ PARTIAL

**Notes**:
_____________________________________
_____________________________________
_____________________________________
