package com.example.android.domain.usecase

import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(name: String, email: String, pass: String): Result<User> {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("All fields are required"))
        }
        if (pass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        return repository.register(name, email, pass)
    }
}
