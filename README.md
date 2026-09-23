# code-server-online
Just a Code Server running on a VPS with protection and this is the app for running it!

---

# CodeAuth VPS — Android App

Modern clean Android app (Kotlin 2.0 + Jetpack Compose + Material 3, Target SDK 35) for authenticating `code.vastaviklearning.online` via QR.

## Structure
```
app/src/main/java/com/vastavik/codeauth/
+-- MainActivity.kt          # NavHost + bottom bar (Scan / Sessions / Settings)
+-- CodeAuthApp.kt
+-- data/
¦   +-- SecurePrefs.kt       # EncryptedSharedPreferences (AES256_GCM)
¦   +-- NetworkClient.kt     # Retrofit2 + kotlinx.serialization
¦   +-- Models.kt
+-- ui/
    +-- theme/ (Color.kt, Type.kt, Theme.kt — #0F141C dark)
    +-- screens/
        +-- ScannerScreen.kt   # CameraX + ML Kit, viewfinder, POST /approve, haptics
        +-- DashboardScreen.kt # GET /devices, revoke cards + animation
        +-- SettingsScreen.kt  # Domain + Secret encrypted storage
app/src/main/AndroidManifest.xml
```

## Build
```powershell
.\gradlew.bat assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk
```

Requires Android SDK 35, JDK 17.

## Features
- **Secure Config Storage** via Jetpack Security EncryptedSharedPreferences (AES256_GCM) - Domain `https://code.vastaviklearning.online` + secret `ChangeThisToASecretHighEntropyKey123!`
- **QR Scanner** CameraX + ML Kit live preview with viewfinder, JSON `{server, sessionId}` parse, POST `/api/app/approve` with haptics + success modal
- **Dashboard Kill-Switch** GET `/api/app/devices` header `x-app-secret`, revoke cards with animation
- **Theme** Dark #0F141C slate/indigo, Retrofit2 + kotlinx.serialization, runtime Camera permission

## Endpoints (defaults)
- Domain: `https://code.vastaviklearning.online`
- Secret header: `x-app-secret: ChangeThisToASecretHighEntropyKey123!`
- `POST /api/app/approve` body `{secret, sessionId, deviceName}`
- `GET  /api/app/devices` header `x-app-secret`
- `POST /api/app/revoke` body `{tokenId}`

All credentials are stored via `EncryptedSharedPreferences` and never logged.

## Permissions
- `CAMERA` (runtime via Accompanist Permissions)
- `INTERNET`, `VIBRATE`

## Notes
- Scanner parses `{"server":"...", "sessionId":"..."}`; falls back to raw `sessionId` if QR is plain string.
- Device name = `android.os.Build.MODEL`.
- Dashboard handles flexible JSON keys (`id|tokenId`, `deviceName|browserAgent|userAgent`, `ip|ipAddress`, etc.).
