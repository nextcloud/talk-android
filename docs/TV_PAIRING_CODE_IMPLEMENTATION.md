# TV Pairing Code Implementation Guide

## Overview
Implement YouTube TV-style pairing: TV shows code → User enters on web → Automatic login

## Architecture

### Components
1. **TV App**: Generate & display code, poll for auth
2. **Server API**: Store codes, validate, return credentials
3. **Web Interface**: Code entry page for users

## Implementation Steps

### 1. Server API (New Endpoints)
```
POST /ocs/v2.php/apps/talk/api/v1/auth/pairing/generate
- Generate 6-8 digit code
- Return: {code, pollUrl, expiresIn}

POST /ocs/v2.php/apps/talk/api/v1/auth/pairing/validate
- Body: {code, username, password}
- Authenticate user, link to code
- Return: success

GET /ocs/v2.php/apps/talk/api/v1/auth/pairing/poll/{code}
- Poll for completion
- Return: {status, server, loginName, appPassword}
```

### 2. Android TV Changes

**New Files:**
- `PairingCodeLoginActivity.kt`
- `PairingCodeViewModel.kt`
- `NetworkPairingDataSource.kt`

**UI Flow:**
```
LoginActivity
  → [Pairing Code] button
  → Show 6-digit code + instructions
  → Poll server every 2s
  → Auto-login on success
```

### 3. Web Interface
Create page: `https://server/index.php/apps/talk/auth/pairing`
- Simple code entry form
- Login with existing credentials
- Link code to account

## Security
- Codes expire in 10 minutes
- One-time use only
- Rate limiting on generation
- HTTPS required

## Benefits vs Current
- ✅ No browser switching
- ✅ No QR camera needed
- ✅ Easy with remote control
- ✅ Works like Netflix/YouTube

## Next Steps
1. Create server endpoints (Talk app)
2. Implement TV pairing UI
3. Add polling logic
4. Create web pairing page
5. Test & deploy