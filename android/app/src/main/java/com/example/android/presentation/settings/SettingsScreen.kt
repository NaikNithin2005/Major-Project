package com.example.android.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUser: User?,
    onNavigateBack: () -> Unit,
    onLogoutSubmitted: () -> Unit
) {
    var realTimeProtectionEnabled by remember { mutableStateOf(true) }
    var alertNotificationsEnabled by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", color = Color.White) },
            text = { Text("Are you sure you want to log out of AegisShield?", color = Color(0xFF94A3B8)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutSubmitted()
                    }
                ) {
                    Text("Sign Out", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Preferences", color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "Guest User",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (currentUser?.isGuest == true) "Guest Session (Offline Mode)" else currentUser?.email ?: "",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Protection Toggles
                item {
                    Text("Security Engine Controls", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column {
                            SettingToggleItem(
                                title = "Real-Time AI Scanning",
                                subtitle = "On-device TinyBERT & XGBoost inference",
                                icon = Icons.Default.Shield,
                                checked = realTimeProtectionEnabled,
                                onCheckedChange = { realTimeProtectionEnabled = it }
                            )
                            HorizontalDivider(color = Color(0xFF334155))
                            SettingToggleItem(
                                title = "Threat Push Notifications",
                                subtitle = "Instant alert notifications for smishing & quishing",
                                icon = Icons.Default.NotificationsActive,
                                checked = alertNotificationsEnabled,
                                onCheckedChange = { alertNotificationsEnabled = it }
                            )
                        }
                    }
                }

                // System Information
                item {
                    Text("About & Version", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column {
                            SettingInfoItem(title = "App Version", value = "1.0.0 (Phase 1)", icon = Icons.Default.Info)
                            HorizontalDivider(color = Color(0xFF334155))
                            SettingInfoItem(title = "AI Inference Engine", value = "ONNX Runtime v1.17", icon = Icons.Default.Memory)
                            HorizontalDivider(color = Color(0xFF334155))
                            SettingInfoItem(title = "Privacy Architecture", value = "Offline-First On-Device", icon = Icons.Default.Lock)
                        }
                    }
                }

                // Logout Button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f), contentColor = Color(0xFFFCA5A5))
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0EA5E9),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
fun SettingInfoItem(title: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(title, fontSize = 15.sp, color = Color.White)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF38BDF8))
    }
}
