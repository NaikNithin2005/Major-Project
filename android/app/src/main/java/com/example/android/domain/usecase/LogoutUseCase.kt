package com.example.android.domain.usecase

import com.example.android.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.logout()
    }
}
