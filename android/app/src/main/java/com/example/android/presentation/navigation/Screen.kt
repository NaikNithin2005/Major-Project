package com.example.android.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permissions : Screen("permissions")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
    object SmsMonitoring : Screen("sms_monitoring")
    object QrScanner : Screen("qr_scanner")
    object Education : Screen("education")
    object SafetyAlerts : Screen("safety_alerts")
    object TrustedUsers : Screen("trusted_users")
    object UntrustedUsers : Screen("untrusted_users")
    object QuarantinedSms : Screen("quarantined_sms")
    object BlockedUsers : Screen("blocked_users")
    object BlockedSms : Screen("blocked_sms")
    object FeedbackHistory : Screen("feedback_history")
    object SmsDetails : Screen("sms_details/{threatId}") {
        fun createRoute(threatId: String): String = "sms_details/$threatId"
    }
    object SenderDetails : Screen("sender_details/{senderId}") {
        fun createRoute(senderId: String): String = "sender_details/${java.net.URLEncoder.encode(senderId, "UTF-8")}"
    }
    object SecurityCheck : Screen("security_check?url={url}&source={source}") {
        fun createRoute(url: String, source: String = "INCOMING_URL"): String {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encodedSource = java.net.URLEncoder.encode(source, "UTF-8")
            return "security_check?url=$encodedUrl&source=$encodedSource"
        }
    }
}
