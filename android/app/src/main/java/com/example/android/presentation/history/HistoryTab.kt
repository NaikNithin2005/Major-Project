package com.example.android.presentation.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android.domain.model.ThreatRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryTab(
    threats: List<ThreatRecord>,
    onDeleteThreat: (String) -> Unit,
    onThreatClicked: (ThreatRecord) -> Unit,
    onNavigateToScanner: () -> Unit,
    initialTypeFilter: String = "All",
    initialSeverityFilter: String = "All"
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember(initialTypeFilter) { mutableStateOf(initialTypeFilter) }
    var selectedSeverityFilter by remember(initialSeverityFilter) { mutableStateOf(initialSeverityFilter) }

    val dateOptions = listOf("All", "Today", "7 Days", "30 Days")
    val typeOptions = listOf("All", "Messages", "QR Codes", "Links", "Flagged")
    val severityOptions = listOf("All", "Looks Safe", "Needs Attention", "Dangerous")

    val currentTime = System.currentTimeMillis()
    val oneDayMs = 24 * 60 * 60 * 1000L

    val filteredThreats = remember(threats, searchQuery, selectedDateFilter, selectedTypeFilter, selectedSeverityFilter) {
        threats.filter { threat ->
            // Search Query filter
            val matchesQuery = searchQuery.isBlank() ||
                    threat.details.contains(searchQuery, ignoreCase = true) ||
                    threat.sender.contains(searchQuery, ignoreCase = true) ||
                    threat.category.contains(searchQuery, ignoreCase = true) ||
                    (!threat.rawContent.isNullOrBlank() && threat.rawContent.contains(searchQuery, ignoreCase = true)) ||
                    threat.id.contains(searchQuery, ignoreCase = true)

            // Date filter
            val matchesDate = when (selectedDateFilter) {
                "Today" -> (currentTime - threat.timestamp) < oneDayMs
                "7 Days" -> (currentTime - threat.timestamp) < (7 * oneDayMs)
                "30 Days" -> (currentTime - threat.timestamp) < (30 * oneDayMs)
                else -> true
            }

            // Type filter
            val matchesType = when (selectedTypeFilter) {
                "Messages" -> threat.source.contains("SMS", ignoreCase = true) || threat.payloadType == "SMS"
                "QR Codes" -> threat.source.contains("QR", ignoreCase = true) || threat.payloadType == "QR_CODE"
                "Links" -> threat.source.contains("URL", ignoreCase = true) || !threat.extractedUrl.isNullOrBlank()
                "Flagged" -> threat.isQuarantined || threat.riskScore >= 70
                else -> true
            }

            // Severity filter (Consumer-friendly: Looks Safe, Needs Attention, Dangerous)
            val matchesSeverity = when (selectedSeverityFilter) {
                "Dangerous" -> threat.riskScore >= 70 || threat.category == "HIGH" || threat.category == "CRITICAL"
                "Needs Attention" -> (threat.riskScore in 35..69) || threat.category == "MEDIUM" || threat.category == "SUSPICIOUS"
                "Looks Safe" -> threat.riskScore < 35 || threat.category == "SAFE"
                else -> true
            }

            matchesQuery && matchesDate && matchesType && matchesSeverity
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by sender, message text, or address...", fontSize = 12.sp, color = Color(0xFF64748B)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF94A3B8)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(dateOptions) { opt ->
                FilterChip(
                    selected = selectedDateFilter == opt,
                    onClick = { selectedDateFilter = opt },
                    label = { Text("📅 $opt", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFF38BDF8),
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }

            items(typeOptions) { opt ->
                FilterChip(
                    selected = selectedTypeFilter == opt,
                    onClick = { selectedTypeFilter = opt },
                    label = { Text(opt, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFA78BFA),
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }

            items(severityOptions) { opt ->
                FilterChip(
                    selected = selectedSeverityFilter == opt,
                    onClick = { selectedSeverityFilter = opt },
                    label = { Text(opt, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444).copy(alpha = 0.25f),
                        selectedLabelColor = Color(0xFFF87171),
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Result count & clear filter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filteredThreats.size} of ${threats.size} events",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            if (searchQuery.isNotBlank() || selectedDateFilter != "All" || selectedTypeFilter != "All" || selectedSeverityFilter != "All") {
                Text(
                    text = "Reset Filters",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.clickable {
                        searchQuery = ""
                        selectedDateFilter = "All"
                        selectedTypeFilter = "All"
                        selectedSeverityFilter = "All"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History Events List
        if (filteredThreats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No events match your criteria",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Try clearing filters or search keywords, or run a manual scan in the Scanner tab.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToScanner,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Scanner", fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredThreats, key = { it.id }) { threat ->
                    HistoryItemCard(
                        threat = threat,
                        onClick = { onThreatClicked(threat) },
                        onCopyId = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Event ID", threat.id))
                            Toast.makeText(context, "Copied Event ID", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { onDeleteThreat(threat.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    threat: ThreatRecord,
    onClick: () -> Unit,
    onCopyId: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val isCritical = threat.riskScore >= 70
    val isSuspicious = threat.riskScore in 40..69

    val severityColor = when {
        isCritical -> Color(0xFFEF4444)
        isSuspicious -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val actionBadgeColor = when (threat.actionTaken) {
        "RESOLVED" -> Color(0xFF10B981)
        "FALSE_POSITIVE" -> Color(0xFF38BDF8)
        "BLOCK" -> Color(0xFFEF4444)
        else -> Color(0xFFF59E0B)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(severityColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                threat.source.contains("QR", ignoreCase = true) -> Icons.Default.QrCode
                                threat.source.contains("SMS", ignoreCase = true) -> Icons.Default.Sms
                                else -> Icons.Default.Link
                            },
                            contentDescription = null,
                            tint = severityColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(threat.category, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val payloadLabel = threat.payloadType?.ifBlank { threat.source } ?: threat.source
                        Text("Source: $payloadLabel • ${threat.sender}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (threat.isQuarantined) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "🛡️ Quarantined",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Surface(
                        color = severityColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${threat.riskScore}/100",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scanned Content Preview (Always visible)
            val hasRaw = !threat.rawContent.isNullOrBlank()
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasRaw) "Original Message / Content:" else "Analysis Summary:",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isExpanded) "Tap card to collapse" else if (hasRaw) "Tap to expand details" else "Content not retained",
                            fontSize = 10.sp,
                            color = if (hasRaw) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (hasRaw) threat.rawContent!! else threat.details.ifBlank { "No content retained." },
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0),
                        maxLines = if (isExpanded) 10 else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }

            // Expanded Animated Details Section
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (!threat.extractedUrl.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF1E1B4B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Website Address Found:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA5B4FC))
                                Text(threat.extractedUrl!!, fontSize = 11.sp, color = Color.White, maxLines = 3)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (threat.details.isNotBlank()) {
                        Text("Why This Result Was Given:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text(threat.details, fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Checked: On-Device Engine (100% Private)",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        TextButton(
                            onClick = onClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Open Full Dialog", fontSize = 11.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = Color(0xFF334155))

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(threat.timestamp)),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = actionBadgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            threat.actionTaken.ifBlank { "FLAGGED" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = actionBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onCopyId, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
