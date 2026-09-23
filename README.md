# code-server-online — Vastavik Authenticator

Just a Code Server running on a VPS with protection and this is the app for running it!

---

# Vastavik Authenticator — Android App

Modern obsidian Android app (Kotlin 2.0 + Jetpack Compose + Material 3, Target SDK 35) serving as a hardware security key for self-hosted VPS services `code.vastaviklearning.online` (VS Code) and `screen.vastaviklearning.online` (VNC Screen).

## Structure
```
app/src/main/java/com/vastavik/codeauth/
├── MainActivity.kt          # Bottom nav [Scan] [Sessions] [Settings] #080C14
├── CodeAuthApp.kt
├── data/
│   ├── SecurePrefs.kt       # EncryptedSharedPreferences (AES256_GCM)
│   ├── NetworkModels.kt     # @Serializable models (List<DeviceSession> fix)
│   └── ApiService.kt        # Retrofit2 + kotlinx.serialization (raw array)
└── ui/
    ├── theme/ (Color.kt, Type.kt, Theme.kt — #080C14 obsidian, #0E1526, #38BDF8)
    └── screens/
        ├── ScannerScreen.kt   # CameraX + ML Kit, dual-service https://<server>/api/app/approve, haptics, "Authorized for <server>"
        ├── SessionsScreen.kt  # GET List<DeviceSession>, badge [VS CODE]/[VNC SCREEN], pull-to-refresh, revoke animation
        ├── SessionsViewModel.kt
        └── SettingsScreen.kt  # Domain + Secret encrypted storage
app/src/main/AndroidManifest.xml
```

## Critical Bug Fix
`GET /api/app/devices` returns a raw JSON Array `[{id:"...", ...}]`. Previous wrapper `DevicesResponse` caused:
`Unexpected JSON token at offset 0: Expected start of the object '{', but had '[' instead`
Fixed:
```kotlin
@GET("api/app/devices")
suspend fun getActiveDevices(@Header("x-app-secret") secret: String): List<DeviceSession>
```
Model:
```kotlin
@Serializable
data class DeviceSession(
    val id: String,
    val targetService: String? = "code.vastaviklearning.online",
    val deviceName: String? = "Unknown Device",
    val browserAgent: String? = null,
    val ip: String? = null,
    val loginTime: String? = null
)
```

## Build
```powershell
.\gradlew.bat assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk
```
Requires Android SDK 35, JDK 17.

## Features
- **Secure Config Storage** via Jetpack Security EncryptedSharedPreferences (AES256_GCM) - Domain `https://code.vastaviklearning.online` + secret `ChangeThisToASecretHighEntropyKey123!`
- **Dual-Service QR Scanner** — parses `{"server":"code|screen.vastaviklearning.online","sessionId":"<UUID>"}` → `POST https://<server>/api/app/approve` {secret, sessionId, deviceName = "MANUFACTURER MODEL"} + haptics + "Authorized for <server>"
- **Live Sessions Kill-Switch** — `GET /api/app/devices` → `List<DeviceSession>` cards with Service Badge, IP, User-Agent, Timestamp, red Revoke Device → `POST /api/app/revoke` {tokenId} + animated removal + pull-to-refresh

## Endpoints
- `POST https://<server>/api/app/approve` body `{secret, sessionId, deviceName}`
- `GET https://code.vastaviklearning.online/api/app/devices` header `x-app-secret` → `List<DeviceSession>`
- `POST https://code.vastaviklearning.online/api/app/revoke` header `x-app-secret` body `{tokenId}`

All credentials stored via `EncryptedSharedPreferences` and never logged.

## Permissions
- `CAMERA` (Accompanist Permissions)
- `INTERNET`, `VIBRATE`
