package com.example.android.presentation.ai

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquishAiAssistantSheet(
    viewModel: SquishAiAssistantViewModel,
    onDismiss: () -> Unit,
    onNavigateToScanner: ((String) -> Unit)? = null
) {
    val messages by viewModel.messages.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val status by viewModel.status.collectAsState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val suggestedPrompts = listOf(
        "🔍 Analyze a suspicious message",
        "🔗 Check a suspicious link",
        "🛡️ What if I clicked a phishing link?",
        "💡 Give me today's security tip",
        "❓ Why are shortened URLs risky?"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF64748B))
        },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Squish AI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isAnalyzing) Color(0xFFFBBF24) else Color(0xFF34D399),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = status,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Row {
                    IconButton(onClick = { viewModel.clearConversation() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Conversation",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color(0xFF1E293B)
            )

            // Suggested prompt chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestedPrompts) { prompt ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val cleanPrompt = prompt.substringAfter(" ").trim()
                                viewModel.sendMessage(cleanPrompt)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation Message Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg, onNavigateToScanner = onNavigateToScanner)
                }

                if (isAnalyzing) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing with on-device AI...", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasted = clip.getItemAt(0).text?.toString() ?: ""
                                if (pasted.isNotBlank()) {
                                    inputText = pasted
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste Clipboard",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("Ask Squish AI or paste SMS/URL...", fontSize = 13.sp, color = Color(0xFF64748B))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(max = 100.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF38BDF8),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val toSend = inputText
                                inputText = ""
                                viewModel.sendMessage(toSend)
                            }
                        },
                        enabled = inputText.isNotBlank() && !isAnalyzing,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (inputText.isNotBlank() && !isAnalyzing) Color(0xFF0EA5E9) else Color(0xFF334155),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onNavigateToScanner: ((String) -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF0EA5E9) else Color(0xFF1E293B),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = Color.White,
                    lineHeight = 18.sp
                )

                // Structured Verdict Card
                if (message.verdictCard != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    VerdictCardView(card = message.verdictCard, onNavigateToScanner = onNavigateToScanner)
                }
            }
        }
    }
}

@Composable
fun VerdictCardView(
    card: SecurityVerdictCard,
    onNavigateToScanner: ((String) -> Unit)? = null
) {
    val borderColor = when (card.verdict) {
        "MALICIOUS" -> Color(0xFFEF4444)
        "SUSPICIOUS" -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val badgeColor = when (card.verdict) {
        "MALICIOUS" -> Color(0xFFEF4444).copy(alpha = 0.2f)
        "SUSPICIOUS" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
        else -> Color(0xFF10B981).copy(alpha = 0.2f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = card.verdict,
                        color = borderColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "Risk: ${card.score}/100",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Contributing Indicators:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(4.dp))
            card.whyReasons.take(3).forEach { reason ->
                Text("• $reason", fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Recommended Actions:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
            Spacer(modifier = Modifier.height(4.dp))
            card.recommendedActions.take(2).forEach { action ->
                Text("✓ $action", fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 14.sp)
            }
        }
    }
}
