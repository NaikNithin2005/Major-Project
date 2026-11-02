# Squish — Official Android Default SMS Handler Architecture

## 1. Overview & Dual-Mode Philosophy

Squish provides robust protection across two distinct operational modes, allowing users to choose their preferred level of system integration:

| Mode | Role Held | Interception Capability | Inbox Experience |
|---|---|---|---|
| **Passive Shield Mode** | None (Standard App) | Real-time broadcast inspection via `SMS_RECEIVED` | Squish notifies and advises on threats; system messaging inbox handles user SMS. |
| **Default SMS App Mode** | `RoleManager.ROLE_SMS` | Full system delivery interception via `SMS_DELIVER` | Squish can actively quarantine dangerous messages, block malicious senders, and manage messaging threads. |

---

## 2. Mandatory Android OS & Google Play System Components

Android OS enforces strict verification before an application is permitted to appear in the system Default SMS dialog. If any of the following four components are absent from `AndroidManifest.xml`, `RoleManager` silently rejects the role request:

### 2.1 SMS Delivery Receiver (`SmsReceiver`)
- **Intent Filter:** `android.provider.Telephony.SMS_DELIVER` (guarded by `android.permission.BROADCAST_SMS`)
- **Fallback Filter:** `android.provider.Telephony.SMS_RECEIVED` (for passive monitoring when not default app)
- **Implementation:** `com.example.android.receiver.SmsReceiver`
- **Function:** Parses the raw PDU, extracts sender and text body, evaluates smishing indicators via `ProcessIncomingSmsUseCase`, and records detection metrics.

### 2.2 MMS Delivery Receiver (`MmsReceiver`)
- **Intent Filter:** `android.provider.Telephony.WAP_PUSH_DELIVER` (guarded by `android.permission.BROADCAST_WAP_PUSH`)
- **Data Scheme:** `application/vnd.wap.mms-message`
- **Implementation:** `com.example.android.receiver.MmsReceiver`
- **Function:** Inspects incoming multimedia messages and headers for scam links and unverified attachments.

### 2.3 Headless Quick-Response Service (`HeadlessSmsSendService`)
- **Intent Filter:** `android.intent.action.RESPOND_VIA_MESSAGE` (guarded by `android.permission.SEND_RESPOND_VIA_MESSAGE`)
- **Data Schemes:** `sms:`, `smsto:`, `mms:`, `mmsto:`
- **Implementation:** `com.example.android.service.HeadlessSmsSendService`
- **Function:** Handles rapid automated replies (e.g., "In a meeting, will text later") sent from notification actions or call rejection screens without bringing the full UI into the foreground.

### 2.4 Compose & SendTo Activity (`MainActivity`)
- **Intent Filter:** `android.intent.action.SEND` and `android.intent.action.SENDTO`
- **Data Schemes:** `sms:`, `smsto:`, `mms:`, `mmsto:`
- **Category:** `android.intent.category.DEFAULT`, `android.intent.category.BROWSABLE`
- **Implementation:** `com.example.android.MainActivity`
- **Function:** Opens the composer interface when external applications launch an SMS sharing or messaging intent.

---

## 3. Role Request Flow

The role request implementation in `PermissionUtils.kt` is fully backward- and forward-compatible:

```kotlin
fun createDefaultSmsRoleIntent(context: Context): Intent? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }
}
```

Invoked via Jetpack Compose's `rememberLauncherForActivityResult`:
```kotlin
val defaultRoleLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
) {
    refreshAllStatus()
}
```

---

## 4. Google Play Store Release Compliance

To satisfy Google Play Developer Program Policies regarding the **SMS and Call Log Permissions Group**:
1. **Prominent Disclosure:** A transparent permission disclosure is presented in `PermissionScreen.kt` explaining why SMS monitoring is requested before system dialogs appear.
2. **Core App Functionality:** Squish's primary function is real-time defense against smishing and quishing fraud, explicitly recognized under Google Play's security exemptions for Default SMS applications.
3. **Graceful Fallback:** If the user declines the Default SMS role, Squish seamlessly operates in **Passive Shield Mode**, verifying messages via `RECEIVE_SMS` without breaking any other app features.

