package com.example.android.domain.usecase

import com.example.android.domain.model.UserFeedback
import com.example.android.domain.repository.FeedbackRepository

class SubmitFeedbackUseCase(
    private val repository: FeedbackRepository
) {
    suspend operator fun invoke(feedback: UserFeedback): Result<Unit> {
        return repository.submitFeedback(feedback)
    }
}
