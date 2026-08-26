package com.example.android.presentation.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel : ViewModel() {
    private val _currentStage = MutableStateFlow(0)
    val currentStage: StateFlow<Int> = _currentStage.asStateFlow()

    fun nextStage() {
        if (_currentStage.value < 4) {
            _currentStage.value += 1
        }
    }

    fun previousStage() {
        if (_currentStage.value > 0) {
            _currentStage.value -= 1
        }
    }
}
