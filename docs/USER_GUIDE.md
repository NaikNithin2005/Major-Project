# Squish Shield — End-User Mobile Application Guide

**Application Name:** Squish Shield  
**Version:** 1.0.0  
**Supported Platforms:** Android 8.0 (API 26) through Android 15 (API 35)  
**Package:** `com.squishshield.app`  

---

## 1. Introduction

Squish Shield is an advanced, privacy-first cybersecurity application for Android that protects your device against **Smishing** (phishing via SMS messages) and **Quishing** (phishing via QR codes).

Unlike traditional security apps that send your personal messages to remote cloud servers, **Squish Shield performs 100% of its AI threat detection directly on your phone**. Your private SMS messages never leave your device.

---

## 2. Installation & Initial Setup

### 2.1 Installing the Application
1. Download the release installer: `app-release.apk`.
2. On your Android phone, locate the downloaded file and tap to install.
3. If prompted with *"Install unknown apps"*, enable permission for your browser or file manager.
4. Complete the installation and open **Squish Shield**.

### 2.2 Account Registration & Login
1. On the welcome screen, tap **Register** to create a new account using your email and password, or select **Sign In with Google**.
2. If you already have an account, enter your credentials and tap **Sign In**.
3. *Note:* An account enables background synchronization of anonymized threat metrics and allows you to view your security trends across multiple devices.

---

## 3. Required App Permissions

To provide real-time protection, Squish Shield requires three core Android permissions:

| Permission | Purpose | Privacy Guarantee |
| :--- | :--- | :--- |
| **SMS Monitoring** (`RECEIVE_SMS`) | Allows the app to inspect incoming text messages for phishing links and scam language. | **Strictly Private:** Messages are evaluated in-memory by our on-device AI. Message contents are **never** uploaded to any server. |
| **Camera Access** (`CAMERA`) | Required to scan QR codes using your device's camera. | **Zero Recording:** Camera frames are analyzed for barcodes in real-time and immediately discarded. No images or videos are saved. |
| **Notifications** (`POST_NOTIFICATIONS`) | Allows the app to display immediate warning alerts when a dangerous message or QR code is detected. | Used exclusively for security alerts and sync status notifications. |

Tap **Allow All Permissions** on the onboarding screen to ensure continuous protection.

---

## 4. Smishing Protection (SMS Phishing)

### 4.1 How It Works
When a text message arrives on your phone:
1. Squish Shield's background monitor intercepts the message before you read it.
2. The on-device **TinyBERT AI model** reads the message text and computes a threat probability score within 50 milliseconds.
3. If the message is determined to be benign, no alert is raised.
4. If a threat is detected (e.g. fake delivery notification, bank impersonation, urgent lottery claim), a high-priority warning notification is triggered immediately.

### 4.2 Understanding Threat Alerts
A threat alert presents three key pieces of information:
- **Risk Score (0–100):** 
  - `0 – 30`: Safe / Low Risk
  - `31 – 70`: Suspicious / Medium Risk
  - `71 – 100`: Malicious / High Risk (Immediate Danger)
- **Threat Category:** Identifies the nature of the scam (e.g., *Bank Impersonation*, *Urgent Call to Action*, *Credential Harvesting*).
- **Explainability Breakdown:** Explains exactly why the AI flagged the message (e.g., *"Urgency language detected"*, *"Unverified link shortener detected"*).

### 4.3 Taking Action
The threat alert provides quick-action buttons:
- **Block Sender:** Adds the sending phone number or sender ID to your phone's blocked list.
- **Mark as Safe (False Positive):** If you trust the sender, tap this to report a false alarm. This helps train the local model not to alert on similar messages in the future.
- **Dismiss:** Closes the warning notification.

---

## 5. Quishing Protection (QR Code Phishing)

### 5.1 Scanning a QR Code
1. Open the Squish Shield app and tap the **Scan QR** icon on the bottom navigation bar.
2. Point your camera at any QR code (e.g., on a parking meter, restaurant table, flyer, or computer screen).
3. The app scans the barcode and extracts the embedded web address (URL).

### 5.2 Pre-Navigation Security Modal
Before opening any link in your browser, Squish Shield analyzes 22 security characteristics of the URL using **XGBoost** and **Isolation Forest**:
- Checks for look-alike characters and typo-squatted domains (e.g., `paypa1.com` instead of `paypal.com`).
- Checks for dangerous file extensions and obfuscated IP addresses.
- Detects zero-day structural anomalies.

The screen displays a safety assessment:
- **Safe URLs:** Displays a green badge. You can tap **Open Link in Browser**.
- **Dangerous URLs:** Displays a prominent red warning shield, highlights suspicious elements of the URL, and blocks automatic redirection.
  - Tap **Copy Sanitized URL** to inspect the text safely.
  - Tap **Cancel & Discard** to prevent malicious site navigation.

---

## 6. Threat History & Dashboard

- **History Tab:** View a chronological log of all analyzed SMS messages and scanned QR codes.
- **Filtering:** Filter threats by category (High, Medium, Low) or type (SMS vs. QR).
- **Incident Inspection:** Tap any logged incident to view the full threat score, contributing heuristics, and the time of detection.
- **Feedback:** Tap **Report Incorrect Detection** to submit feedback to our security team.

---

## 7. Offline Operation & Background Synchronization

### 7.1 Complete Offline Operation
Squish Shield does not require an active Wi-Fi or cellular data connection to protect you. All AI models, dictionaries, and threat engines are packaged directly inside the app. You are fully protected on airplanes, in remote areas, or when mobile data is disabled.

### 7.2 Background Synchronization
When your device reconnects to the internet:
- The app uses Android's WorkManager to securely synchronize anonymized threat statistics with the backend.
- **Reminder:** Only anonymized threat categories, risk scores, and defensive actions are synchronized. Your personal SMS text messages remain securely on your phone.
