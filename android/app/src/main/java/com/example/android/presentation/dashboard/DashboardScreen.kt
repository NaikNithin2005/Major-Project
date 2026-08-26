package com.example.android.presentation.dashboard

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

@Composable
fun DashboardScreen(
    currentUser: User?,
    onNavigateToSettings: () -> Unit,
    onLogoutClicked: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    val greetingName = when {
        currentUser?.isGuest == true -> "User"
        !currentUser?.displayName.isNullOrBlank() -> currentUser?.displayName!!
        else -> "User"
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color(0xFF94A3B8)
            ) {
                val items = listOf(
                    Triple("Home", Icons.Default.Home, 0),
                    Triple("History", Icons.Default.History, 1),
                    Triple("Scanner", Icons.Default.QrCodeScanner, 2),
                    Triple("Alerts", Icons.Default.Notifications, 3),
                    Triple("Settings", Icons.Default.Settings, 4)
                )

                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (index == 4) {
                                onNavigateToSettings()
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header User Greeting
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hello, $greetingName",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (currentUser?.isGuest == true) "Guest Session" else currentUser?.email ?: "",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                // Security Overview Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF10B981), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Protection Active", fontSize = 12.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Medium)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text("Security Score", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                Text("92 / 100", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF0EA5E9).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }

                // Activity Counters Grid
                item {
                    Text("Today's Activity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard("Threats", "0", Icons.Default.Warning, Color(0xFFEF4444), Modifier.weight(1f))
                        StatCard("SMS Scanned", "0", Icons.Default.Sms, Color(0xFF3B82F6), Modifier.weight(1f))
                        StatCard("QR Scanned", "0", Icons.Default.QrCode, Color(0xFF10B981), Modifier.weight(1f))
                    }
                }

                // Quick Actions
                item {
                    Text("Quick Actions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR")
                        }

                        OutlinedButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("History")
                        }
                    }
                }

                // Recent Threat Activity Placeholder
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Recent Threats", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No threat activity detected today. On-device AI engines (TinyBERT & XGBoost) are active.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
    }
}
