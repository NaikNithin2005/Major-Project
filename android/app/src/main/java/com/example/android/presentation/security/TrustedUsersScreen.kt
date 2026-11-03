package com.example.android.presentation.security

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.TrustedSender
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedUsersScreen(
    trustedSenderRepository: TrustedSenderRepository,
    onNavigateBack: () -> Unit,
    onNavigateToSenderDetails: (String) -> Unit,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trustedSenders by trustedSenderRepository.observeTrustedSenders().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newSenderInput by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                trustedSenderRepository.syncTrustedSenders()
            } catch (ignored: Exception) {}
        }
    }

    val filtered = remember(trustedSenders, searchQuery) {
        if (searchQuery.isBlank()) trustedSenders
        else trustedSenders.filter { it.sender.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Security Center", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Trusted Users & Senders", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val res = trustedSenderRepository.syncTrustedSenders()
                                    val count = res.getOrDefault(0)
                                    Toast.makeText(context, "Synced $count trusted senders from cloud", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Cloud", tint = Color(0xFF38BDF8))
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Trusted Sender", tint = Color(0xFF38BDF8))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                )
                if (onNavigateToTab != null) {
                    SecurityTabRow(
                        selectedTabIndex = 0,
                        onTabSelected = onNavigateToTab
                    )
                }
            }
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

            // Invariant Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2937)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sender trust verifies identity, but every incoming message is still analyzed by TinyBERT for credential requests and malicious URLs.",
                        fontSize = 11.sp,
                        color = Color(0xFFBAE6FD),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search trusted senders...", fontSize = 12.sp, color = Color(0xFF64748B)) },
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

            Spacer(modifier = Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Trusted Senders", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Tap + above to add an official bank or organization sender.", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.sender }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSenderDetails(item.sender) }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.sender, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Trusted • Added ${timeFormatter.format(Date(item.addedTimestamp))}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            trustedSenderRepository.removeTrustedSender(item.sender)
                                            Toast.makeText(context, "Removed ${item.sender} from trusted list", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Trust a Sender", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter the bank header or phone number you verify as legitimate:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        OutlinedTextField(
                            value = newSenderInput,
                            onValueChange = { newSenderInput = it },
                            placeholder = { Text("e.g. HDFCBK or +91...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newSenderInput.isNotBlank()) {
                                scope.launch {
                                    trustedSenderRepository.addTrustedSender(newSenderInput.trim())
                                    showAddDialog = false
                                    newSenderInput = ""
                                    Toast.makeText(context, "Sender added to trusted list", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Text("Trust Sender", fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}
