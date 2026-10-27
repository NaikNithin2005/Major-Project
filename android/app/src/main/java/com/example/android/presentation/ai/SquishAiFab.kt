package com.example.android.presentation.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SquishAiFab(
    hasUnresolvedAlerts: Boolean = false,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .padding(bottom = 16.dp, end = 16.dp)
            .scale(if (hasUnresolvedAlerts) pulseScale else 1.0f)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color(0xFF0EA5E9),
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "Open Squish AI Assistant",
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }

        if (hasUnresolvedAlerts) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color(0xFFEF4444), CircleShape)
                    .align(Alignment.TopEnd)
            )
        }
    }
}
