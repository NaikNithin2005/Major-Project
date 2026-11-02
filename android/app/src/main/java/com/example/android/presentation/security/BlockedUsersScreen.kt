package com.example.android.presentation.security

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    threatHistoryRepository: ThreatHistoryRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSenderDetails: (String) -> Unit
) {
    val threats by threatHistoryRepository.observeAllThreats().collectAsState(initial = emptyList())
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    // Senders marked with actionTaken == "BLOCK" or explicitly blocked
    val blockedSenders = remember(threats, searchQuery) {
        val list = threats
            .filter { it.actionTaken == "BLOCK" && it.sender.isNotBlank() }
            .groupBy { it.sender }
            .map { (sender, records) ->
                Pair(sender, records.size)
            }

        if (searchQuery.isBlank()) list
        else list.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Blocked Senders", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Restricted sender identities", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF090D16)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search blocked senders...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (blockedSenders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Blocked Senders", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("You have not added any senders to your blocklist.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(blockedSenders, key = { it.first }) { (sender, count) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSenderDetails(sender) }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(sender, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("$count associated messages • Status: BLOCKED", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                TextButton(
                                    onClick = { onNavigateToSenderDetails(sender) }
                                ) {
                                    Text("Details", fontSize = 12.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
