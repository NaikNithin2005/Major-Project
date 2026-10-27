package com.example.android.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SquishAiAssistantViewModel(
    private val assistantEngine: SquishAiAssistantEngine
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Hello! I am **Squish AI**, your dedicated cybersecurity assistant.\n\nPaste any suspicious SMS, URL, or ask me how to respond to potential threats."
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _status = MutableStateFlow("Ready (On-Device AI Engine)")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _activeContext = MutableStateFlow<AssistantContext?>(null)
    val activeContext: StateFlow<AssistantContext?> = _activeContext.asStateFlow()

    fun setContext(context: AssistantContext) {
        _activeContext.value = context
        val contextPrompt = "Please explain the alert: \"${context.title}\" (Severity: ${context.severity})"
        sendMessage(contextPrompt)
    }

    fun sendMessage(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank() || _isAnalyzing.value) return

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = trimmed
        )
        _messages.value = _messages.value + userMessage
        _isAnalyzing.value = true
        _status.value = "Analyzing threat indicators..."

        viewModelScope.launch {
            try {
                val response = assistantEngine.processUserPrompt(trimmed, _activeContext.value)
                _messages.value = _messages.value + response
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "⚠️ An error occurred while evaluating this input: ${e.message ?: "Unknown error"}. Please check the input and try again."
                )
            } finally {
                _isAnalyzing.value = false
                _status.value = "Ready (On-Device AI Engine)"
                _activeContext.value = null
            }
        }
    }

    fun clearConversation() {
        _messages.value = listOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Conversation cleared. Paste any message or link whenever you'd like me to inspect it."
            )
        )
        _activeContext.value = null
    }
}
