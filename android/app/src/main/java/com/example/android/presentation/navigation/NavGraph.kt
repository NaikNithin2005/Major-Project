package com.example.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.example.android.presentation.settings.SettingsScreen
import com.example.android.presentation.settings.SettingsViewModel
import com.example.android.presentation.splash.SplashScreen
import com.example.android.presentation.splash.SplashViewModel

@Composable
fun AegisNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as AegisApplication
    val container = app.container

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
                        container.getThreatHistoryUseCase,
                        container.addThreatRecordUseCase,
                        container.deleteThreatRecordUseCase
                    )
                }
            )
            val authState by viewModel.authState.collectAsState()
            val currentUser = (authState as? AuthState.Authenticated)?.user

            DashboardScreen(
                currentUser = currentUser,
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onLogoutClicked = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
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
    }
}
