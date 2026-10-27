package com.example.android.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.android.AegisApplication
import com.example.android.di.AppViewModelFactory
import com.example.android.domain.model.AuthState
import com.example.android.presentation.auth.login.LoginScreen
import com.example.android.presentation.auth.login.LoginViewModel
import com.example.android.presentation.auth.register.RegisterScreen
import com.example.android.presentation.auth.register.RegisterViewModel
import com.example.android.presentation.dashboard.DashboardScreen
import com.example.android.presentation.dashboard.DashboardViewModel
import com.example.android.presentation.onboarding.OnboardingScreen
import com.example.android.presentation.onboarding.OnboardingViewModel
import com.example.android.presentation.securitycheck.SecurityCheckScreen
import com.example.android.presentation.securitycheck.SecurityCheckViewModel
import com.example.android.presentation.settings.SettingsScreen
import com.example.android.presentation.settings.SettingsViewModel
import com.example.android.presentation.sms.SmsMonitoringScreen
import com.example.android.presentation.sms.SmsMonitoringViewModel
import com.example.android.presentation.splash.SplashScreen
import com.example.android.presentation.splash.SplashViewModel
import com.example.android.util.DeepLinkRequest

@Composable
fun AegisNavGraph(
    navController: NavHostController = rememberNavController(),
    initialDeepLink: DeepLinkRequest? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as AegisApplication
    val container = app.container

    LaunchedEffect(initialDeepLink) {
        if (initialDeepLink != null) {
            val target = initialDeepLink.normalizedUrl.ifBlank { initialDeepLink.rawInput }
            if (target.isNotBlank()) {
                val currentRoute = navController.currentDestination?.route
                if (currentRoute != null && currentRoute != Screen.Splash.route) {
                    val route = Screen.SecurityCheck.createRoute(
                        url = target,
                        source = initialDeepLink.source
                    )
                    navController.navigate(route)
                    onDeepLinkConsumed()
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            val viewModel: SplashViewModel = viewModel(
                factory = AppViewModelFactory { SplashViewModel(container.getAuthStateUseCase) }
            )
            val authState by viewModel.authState.collectAsState()

            SplashScreen(
                authState = authState,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                    if (initialDeepLink != null) {
                        val target = initialDeepLink.normalizedUrl.ifBlank { initialDeepLink.rawInput }
                        if (target.isNotBlank()) {
                            val checkRoute = Screen.SecurityCheck.createRoute(target, initialDeepLink.source)
                            navController.navigate(checkRoute)
                            onDeepLinkConsumed()
                        }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(Screen.Onboarding.route) {
            val viewModel: OnboardingViewModel = viewModel(
                factory = AppViewModelFactory { OnboardingViewModel() }
            )
            val currentStage by viewModel.currentStage.collectAsState()

            OnboardingScreen(
                currentStage = currentStage,
                onNext = { viewModel.nextStage() },
                onSkip = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onFinish = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Login Screen
        composable(Screen.Login.route) {
            val viewModel: LoginViewModel = viewModel(
                factory = AppViewModelFactory {
                    LoginViewModel(container.loginUseCase, container.guestLoginUseCase)
                }
            )
            val uiState by viewModel.uiState.collectAsState()

            LoginScreen(
                uiState = uiState,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onLoginSubmitted = {
                    viewModel.login(
                        onSuccess = {
                            navController.navigate(Screen.Main.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                },
                onGuestSubmitted = {
                    viewModel.loginAsGuest(
                        onSuccess = {
                            navController.navigate(Screen.Main.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    )
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // Register Screen
        composable(Screen.Register.route) {
            val viewModel: RegisterViewModel = viewModel(
                factory = AppViewModelFactory {
                    RegisterViewModel(container.registerUseCase)
                }
            )
            val uiState by viewModel.uiState.collectAsState()

            RegisterScreen(
                uiState = uiState,
                onNameChanged = viewModel::onNameChanged,
                onEmailChanged = viewModel::onEmailChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
                onRegisterSubmitted = {
                    viewModel.register(
                        onSuccess = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Register.route) { inclusive = true }
                            }
                        }
                    )
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        // Home Dashboard Shell Screen
        composable(Screen.Main.route) {
            val viewModel: DashboardViewModel = viewModel(
                factory = AppViewModelFactory {
                    DashboardViewModel(
                        container.getAuthStateUseCase,
                        container.logoutUseCase,
                        container.threatHistoryRepository,
                        container.smsAnalysisRepository,
                        container.qrAnalysisRepository,
                        container.addThreatRecordUseCase,
                        container.deleteThreatRecordUseCase,
                        container.syncRepository,
                        container.submitFeedbackUseCase
                    )
                }
            )
            val aiViewModel: com.example.android.presentation.ai.SquishAiAssistantViewModel = viewModel(
                factory = AppViewModelFactory {
                    com.example.android.presentation.ai.SquishAiAssistantViewModel(
                        container.squishAiAssistantEngine
                    )
                }
            )
            val authState by viewModel.authState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user

            DashboardScreen(
                currentUser = currentUser,
                uiState = uiState,
                onDeleteThreat = viewModel::deleteThreat,
                onUpdateThreatAction = viewModel::updateThreatAction,
                onSaveThreat = viewModel::saveThreat,
                onClearAllThreats = viewModel::clearAllThreats,
                onSubmitFeedback = viewModel::submitFeedback,
                onTriggerManualSync = { viewModel.triggerManualSync(context) },
                onOpenEducation = {
                    navController.navigate(Screen.Education.route)
                },
                onNavigateToSmsMonitoring = {
                    navController.navigate(Screen.SmsMonitoring.route)
                },
                onNavigateToQrScanner = {
                    navController.navigate(Screen.QrScanner.route)
                },
                onLogoutClicked = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                },
                onOpenSecurityCheck = { url, source ->
                    navController.navigate(Screen.SecurityCheck.createRoute(url, source))
                },
                smishingClassifier = container.smishingClassifier,
                urlClassifier = container.urlClassifier,
                ruleEngine = container.ruleEngine,
                explainableAiEngine = container.explainableAiEngine,
                processQrCodeUseCase = container.processQrCodeUseCase,
                aiViewModel = aiViewModel
            )
        }

        // Education Center Screen
        composable(Screen.Education.route) {
            com.example.android.presentation.education.EducationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // SMS Monitoring Screen
        composable(Screen.SmsMonitoring.route) {
            val viewModel: SmsMonitoringViewModel = viewModel(
                factory = AppViewModelFactory {
                    SmsMonitoringViewModel(
                        container.checkSmsPermissionUseCase,
                        container.getSettingsUseCase,
                        container.updateSettingUseCase,
                        container.smsAnalysisRepository,
                        container.threatHistoryRepository,
                        container.processIncomingSmsUseCase
                    )
                }
            )

            SmsMonitoringScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // QR Scanner Screen
        composable(Screen.QrScanner.route) {
            val viewModel: com.example.android.presentation.qr.QrScannerViewModel = viewModel(
                factory = AppViewModelFactory {
                    com.example.android.presentation.qr.QrScannerViewModel(
                        container.checkCameraPermissionUseCase,
                        container.processQrCodeUseCase,
                        container.qrAnalysisRepository
                    )
                }
            )

            com.example.android.presentation.qr.QrScannerScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings Shell Screen
        composable("settings") {
            val viewModel: SettingsViewModel = viewModel(
                factory = AppViewModelFactory {
                    SettingsViewModel(
                        container.getAuthStateUseCase,
                        container.logoutUseCase,
                        container.getSettingsUseCase,
                        container.updateSettingUseCase
                    )
                }
            )
            val authState by viewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user

            SettingsScreen(
                currentUser = currentUser,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogoutSubmitted = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        // Dedicated Security Check Screen (Deep Links, App Links, Sharesheet, In-App Scans)
        composable(
            route = Screen.SecurityCheck.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("source") { type = NavType.StringType; defaultValue = "INCOMING_URL" }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
            val encodedSource = backStackEntry.arguments?.getString("source") ?: "INCOMING_URL"
            val decodedUrl = try {
                java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            } catch (e: Exception) {
                encodedUrl
            }
            val decodedSource = try {
                java.net.URLDecoder.decode(encodedSource, "UTF-8")
            } catch (e: Exception) {
                encodedSource
            }

            val checkViewModel: SecurityCheckViewModel = viewModel(
                factory = AppViewModelFactory {
                    SecurityCheckViewModel(
                        urlClassifier = container.urlClassifier,
                        ruleEngine = container.ruleEngine,
                        explainableAiEngine = container.explainableAiEngine,
                        threatHistoryRepository = container.threatHistoryRepository,
                        feedbackRepository = container.feedbackRepository
                    )
                }
            )

            val aiViewModel: com.example.android.presentation.ai.SquishAiAssistantViewModel = viewModel(
                factory = AppViewModelFactory {
                    com.example.android.presentation.ai.SquishAiAssistantViewModel(
                        container.squishAiAssistantEngine
                    )
                }
            )

            var isAiAssistantOpen by remember { mutableStateOf(false) }

            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                SecurityCheckScreen(
                    rawUrl = decodedUrl,
                    source = decodedSource,
                    viewModel = checkViewModel,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Main.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    onExplainWithAi = { targetUrl, domain, severity, reasons ->
                        aiViewModel.setContext(
                            com.example.android.presentation.ai.AssistantContext(
                                title = domain,
                                severity = severity,
                                type = com.example.android.presentation.ai.AssistantContextType.ALERT_EXPLANATION,
                                details = "Target URL: $targetUrl; Indicators: $reasons"
                            )
                        )
                        isAiAssistantOpen = true
                    }
                )

                if (isAiAssistantOpen) {
                    com.example.android.presentation.ai.SquishAiAssistantSheet(
                        viewModel = aiViewModel,
                        onDismiss = { isAiAssistantOpen = false },
                        onNavigateToScanner = {
                            isAiAssistantOpen = false
                            navController.navigate(Screen.Main.route)
                        }
                    )
                }
            }
        }
    }
}
