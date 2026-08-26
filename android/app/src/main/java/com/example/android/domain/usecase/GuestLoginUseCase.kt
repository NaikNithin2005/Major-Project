package com.example.android.domain.usecase

import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import javax.inject.Inject

class GuestLoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<User> {
        return repository.loginAsGuest()
    }
}
