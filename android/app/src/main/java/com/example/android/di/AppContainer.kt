package com.example.android.di

import com.example.android.data.repository.AuthRepositoryImpl
import com.example.android.data.source.FirebaseAuthDataSource
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.GuestLoginUseCase
import com.example.android.domain.usecase.LoginUseCase
import com.example.android.domain.usecase.LogoutUseCase
import com.example.android.domain.usecase.RegisterUseCase

class AppContainer {
    val firebaseAuthDataSource: FirebaseAuthDataSource by lazy {
        FirebaseAuthDataSource()
    }

    val firestoreDataSource: FirestoreDataSource by lazy {
        FirestoreDataSource()
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(firebaseAuthDataSource, firestoreDataSource)
    }

    val loginUseCase: LoginUseCase by lazy {
        LoginUseCase(authRepository)
    }

    val registerUseCase: RegisterUseCase by lazy {
        RegisterUseCase(authRepository)
    }

    val logoutUseCase: LogoutUseCase by lazy {
        LogoutUseCase(authRepository)
    }

    val getAuthStateUseCase: GetAuthStateUseCase by lazy {
        GetAuthStateUseCase(authRepository)
    }

    val guestLoginUseCase: GuestLoginUseCase by lazy {
        GuestLoginUseCase(authRepository)
    }
}
