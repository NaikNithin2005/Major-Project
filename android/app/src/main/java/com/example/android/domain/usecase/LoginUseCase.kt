package com.example.android.domain.usecase

import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, pass: String): Result<User> {
        if (email.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password cannot be empty"))
        }
        return repository.login(email, pass)
    }
}
