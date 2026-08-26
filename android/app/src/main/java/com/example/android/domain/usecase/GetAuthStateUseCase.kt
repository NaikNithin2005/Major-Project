package com.example.android.domain.usecase

import com.example.android.domain.model.AuthState
import com.example.android.domain.repository.AuthRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetAuthStateUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}
