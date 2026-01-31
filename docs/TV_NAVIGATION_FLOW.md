# Android TV Navigation Flow

## Login Screen Navigation

```
┌─────────────────────────────────────────┐
│                                         │
│          Nextcloud Talk Logo            │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │ [Server URL Input]                │  │ ← START HERE (Auto-focused)
│  └───────────────────────────────────┘  │
│         Press OK to type                │
│                                         │
│              ↓ (Down Arrow)             │
│                                         │
│         [ 🔍 Scan QR Code ]             │
│                                         │
│              ↓ (Down Arrow)             │
│                                         │
│     [ Import from Files app ]           │
│                                         │
│              ↓ (Down Arrow)             │
│                                         │
│    [ Configure Certificate Auth ]       │
│                                         │
└─────────────────────────────────────────┘
```

## Call Screen Navigation (TV Mode)

```
┌─────────────────────────────────────────┐
│  Call with John Doe          [00:05]    │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │                                   │  │
│  │      Main Video Feed              │  │
│  │                                   │  │
│  │                                   │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Controls (Bottom):                     │
│                                         │
│  [ 🎤 Mic ] ← [ 📹 Cam ] → [ 📞 Hang Up]│
│                                         │
│  Use Left/Right arrows to navigate     │
│  Press OK to toggle                     │
└─────────────────────────────────────────┘
```

## Remote Control Button Functions

| Button | Function |
|--------|----------|
| ⬆️ Up | Navigate up / Previous item |
| ⬇️ Down | Navigate down / Next item |
| ⬅️ Left | Navigate left / Previous button |
| ➡️ Right | Navigate right / Next button |
| ⭕ OK/Select | Activate focused item |
| ◀️ Back | Return to previous screen |

## Navigation Tips

1. **Initial Focus**: Always starts on the most important element
2. **Visual Feedback**: Focused items have a colored border and scale slightly
3. **Logical Order**: Navigation follows reading order (top-to-bottom, left-to-right)
4. **No Dead Ends**: Can always navigate to all interactive elements
5. **Back Button**: Always returns to previous screen or exits gracefully

## Keyboard Navigation

When an input field is focused:
1. Press OK to open on-screen keyboard
2. Use arrow keys to select letters
3. Press OK to type selected letter
4. Press Back to close keyboard
5. Focus returns to input field

## Troubleshooting Navigation

### Focus Lost
- Press Back and re-enter the screen
- Use arrow keys to find visible buttons

### Can't Reach Button
- Try all four arrow directions
- Check if button is visible on screen
- Scroll if in a scrollable list

### Keyboard Won't Appear
- Ensure input field is focused (has border)
- Press OK button firmly
- Try Back button then OK again
