package com.example.android.presentation.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector
)

val onboardingSteps = listOf(
    OnboardingStep(
        title = "Welcome to AegisShield",
        description = "Advanced AI security system designed to protect you from malicious SMS links and QR codes.",
        icon = Icons.Default.Shield
    ),
    OnboardingStep(
        title = "Smishing & Quishing Defense",
        description = "On-device TinyBERT NLP and XGBoost models scan incoming SMS messages and scanned QR codes instantly.",
        icon = Icons.Default.Security
    ),
    OnboardingStep(
        title = "100% Privacy First",
        description = "All AI threat evaluations happen on your device. Your personal messages never leave your phone.",
        icon = Icons.Default.Lock
    ),
    OnboardingStep(
        title = "Zero-Day Anomaly Detection",
        description = "Isolation Forest AI catches novel phishing techniques before traditional blocklists.",
        icon = Icons.Default.Psychology
    ),
    OnboardingStep(
        title = "Ready for Defense",
        description = "Sign in or continue as a guest to activate real-time threat protection.",
        icon = Icons.Default.CheckCircle
    )
)

@Composable
fun OnboardingScreen(
    currentStage: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val step = onboardingSteps.getOrElse(currentStage) { onboardingSteps[0] }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                )
            )
            .padding(24.dp)
    ) {
        // Skip Button
        if (currentStage < onboardingSteps.size - 1) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Skip", color = Color(0xFF94A3B8), fontSize = 16.sp)
            }
        }

        // Center Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color(0xFF0EA5E9).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = step.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = step.description,
                fontSize = 15.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Stage Indicator Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                onboardingSteps.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(
                                width = if (index == currentStage) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .background(
                                color = if (index == currentStage) Color(0xFF38BDF8) else Color(0xFF334155),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (currentStage < onboardingSteps.size - 1) {
                        onNext()
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
            ) {
                Text(
                    text = if (currentStage == onboardingSteps.size - 1) "Get Started" else "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
