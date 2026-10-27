package com.example.android.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
    object SmsMonitoring : Screen("sms_monitoring")
    object QrScanner : Screen("qr_scanner")
    object Education : Screen("education")
    object SecurityCheck : Screen("security_check?url={url}&source={source}") {
        fun createRoute(url: String, source: String = "INCOMING_URL"): String {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encodedSource = java.net.URLEncoder.encode(source, "UTF-8")
            return "security_check?url=$encodedUrl&source=$encodedSource"
        }
    }
}
