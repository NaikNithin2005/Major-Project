package com.example.android.presentation.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.Send
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

/**
 * Production Squish AI Assistant Bottom Sheet.
 * Implements Phase 6 specifications:
 * - Structured markdown rendering (headings, bullet points, numbered recommendations, safety callouts)
 * - Modern chat bubble styling (user gradient bubble, AI assistant card with avatar)
 * - Interactive quick prompts ("Why was this flagged?", "Is this bank message legitimate?", "What should I do right now?")
 * - Copy button and thumbs up / thumbs down feedback on every AI response
 * - Pulsing thinking animation and responsive keyboard handling (imePadding)
 */
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
        "🔍 Why was this message flagged?",
        "🏦 Is this bank message legitimate?",
        "⚠️ What should I do right now?",
        "📱 How can I check this sender?",
        "🔗 Check a suspicious link",
        "🛡️ What if I clicked a phishing link?",
        "💡 Today's security tip"
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
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Squish AI Assistant",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Edge AI",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
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
                                text = if (isAnalyzing) "Thinking locally..." else "Ready to assist (<30ms)",
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
                modifier = Modifier.padding(vertical = 6.dp),
                color = Color(0xFF1E293B)
            )

            // Interactive quick prompt chips
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
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Conversation Message Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg, onNavigateToScanner = onNavigateToScanner)
                }

                if (isAnalyzing) {
                    item {
                        ThinkingAnimationBubble()
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                            .size(36.dp)
                            .background(
                                if (inputText.isNotBlank() && !isAnalyzing) Color(0xFF0EA5E9) else Color(0xFF334155),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Chat Bubble with rich structured markdown layout and feedback controls.
 */
@Composable
fun ChatBubble(
    message: ChatMessage,
    onNavigateToScanner: ((String) -> Unit)? = null
) {
    val isUser = message.sender == MessageSender.USER
    val context = LocalContext.current
    var feedbackState by remember { mutableStateOf<String?>(null) } // "LIKE" or "DISLIKE"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Squish AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) Color(0xFF0284C7) else Color(0xFF1E293B),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.8f)) else null,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                } else {
                    StructuredMarkdownRenderer(text = message.text)

                    // Embedded Security Verdict Card (if applicable)
                    if (message.verdictCard != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        VerdictCardView(card = message.verdictCard, onNavigateToScanner = onNavigateToScanner)
                    }

                    // Bottom feedback and copy controls for AI messages
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbs up / down
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    feedbackState = "LIKE"
                                    Toast.makeText(context, "Thanks for the feedback!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (feedbackState == "LIKE") Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                    contentDescription = "Helpful",
                                    tint = if (feedbackState == "LIKE") Color(0xFF10B981) else Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    feedbackState = "DISLIKE"
                                    Toast.makeText(context, "Feedback logged. We'll improve!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (feedbackState == "DISLIKE") Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                    contentDescription = "Not Helpful",
                                    tint = if (feedbackState == "DISLIKE") Color(0xFFEF4444) else Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Copy response
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", message.text))
                                Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders structured markdown blocks (Headings, Bullet points, Numbered steps, Tips)
 */
@Composable
fun StructuredMarkdownRenderer(text: String) {
    val lines = remember(text) { text.lines() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = trimmed.removePrefix("### "),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
                trimmed.startsWith("• ") || trimmed.startsWith("- ") -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trimmed.substring(2),
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 16.sp
                        )
                    }
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val num = trimmed.substringBefore(".")
                    val content = trimmed.substringAfter(".").trim()
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(num, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = content,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 16.sp
                        )
                    }
                }
                trimmed.startsWith("💡") -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trimmed,
                            fontSize = 11.sp,
                            color = Color(0xFFBAE6FD),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                trimmed.isNotBlank() -> {
                    Text(
                        text = trimmed,
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

/**
 * Pulsing Thinking / Analyzing Animation Bubble
 */
@Composable
fun ThinkingAnimationBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF38BDF8).copy(alpha = dotAlpha1), CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF38BDF8).copy(alpha = dotAlpha2), CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(6.dp).background(Color(0xFF38BDF8).copy(alpha = dotAlpha3), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Squish AI is thinking...", fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                Text(
                    text = card.category,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = card.verdict,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Safety Score: ${card.score}/100",
                fontSize = 12.sp,
                color = borderColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
