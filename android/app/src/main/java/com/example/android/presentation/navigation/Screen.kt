package com.example.android.presentation.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
    object SmsMonitoring : Screen("sms_monitoring")
    object QrScanner : Screen("qr_scanner")
}
