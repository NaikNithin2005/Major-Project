package com.example.android.presentation.education

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedLesson by remember { mutableStateOf<SecurityLesson?>(null) }
    val completedLessonIds = remember { mutableStateListOf<String>() }

    val categories = listOf("All", "SMS Phishing", "QR Security", "Financial Safety", "Identity Protection", "Web Safety")

    val filteredLessons = remember(selectedCategory) {
        if (selectedCategory == "All") {
            EducationRepository.lessons
        } else {
            EducationRepository.lessons.filter { it.category == selectedCategory }
        }
    }

    if (selectedLesson != null) {
        LessonDetailDialog(
            lesson = selectedLesson!!,
            isCompleted = completedLessonIds.contains(selectedLesson!!.id),
            onToggleCompleted = {
                if (completedLessonIds.contains(selectedLesson!!.id)) {
                    completedLessonIds.remove(selectedLesson!!.id)
                } else {
                    completedLessonIds.add(selectedLesson!!.id)
                }
            },
            onDismiss = { selectedLesson = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Security Academy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Learn to recognize scams & social engineering", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Progress Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Your Security Progress", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${completedLessonIds.size} of ${EducationRepository.lessons.size} lessons completed",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (EducationRepository.lessons.isNotEmpty()) {
                                            completedLessonIds.size.toFloat() / EducationRepository.lessons.size
                                        } else 0f
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF334155)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8))
                            }
                        }
                    }
                }

                item {
                    // Category filter chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFF38BDF8),
                                    containerColor = Color(0xFF1E293B),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                items(filteredLessons) { lesson ->
                    val isCompleted = completedLessonIds.contains(lesson.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLesson = lesson },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        if (isCompleted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF0EA5E9).copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isCompleted) Color(0xFF34D399) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(lesson.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    lesson.explanation,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = Color(0xFF334155),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            lesson.category,
                                            fontSize = 10.sp,
                                            color = Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text("⏱ ${lesson.readTimeMinutes} min read", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = "View", tint = Color(0xFF64748B))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun LessonDetailDialog(
    lesson: SecurityLesson,
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedQuizOption by remember { mutableStateOf<Int?>(null) }
    var showQuizResult by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lesson.category.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Text(lesson.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Explanation
                    item {
                        Text("Overview", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(lesson.explanation, fontSize = 13.sp, color = Color(0xFFCBD5E1), lineHeight = 18.sp)
                    }

                    // Simulated Scenario
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Realistic Scenario", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    lesson.realisticScenario,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0),
                                    lineHeight = 16.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }

                    // Warning Signs
                    item {
                        Text("Typical Warning Signs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(6.dp))
                        lesson.warningSigns.forEach { sign ->
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Text("⚠️", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(sign, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
                            }
                        }
                    }

                    // Protective Steps
                    item {
                        Text("Recommended Protective Steps", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                        Spacer(modifier = Modifier.height(6.dp))
                        lesson.protectiveSteps.forEachIndexed { i, step ->
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Text("${i + 1}.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(step, fontSize = 12.sp, color = Color(0xFFCBD5E1), lineHeight = 16.sp)
                            }
                        }
                    }

                    // Interactive Quiz Check
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Quick Knowledge Check", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(lesson.quizQuestion, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(10.dp))

                                lesson.quizOptions.forEachIndexed { idx, opt ->
                                    val isSelected = selectedQuizOption == idx
                                    val isCorrect = idx == lesson.correctOptionIndex

                                    val bgColor = when {
                                        !showQuizResult && isSelected -> Color(0xFF0EA5E9).copy(alpha = 0.2f)
                                        showQuizResult && isCorrect -> Color(0xFF10B981).copy(alpha = 0.25f)
                                        showQuizResult && isSelected && !isCorrect -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                        else -> Color(0xFF0F172A)
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                selectedQuizOption = idx
                                                showQuizResult = true
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = bgColor
                                    ) {
                                        Text(
                                            opt,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }

                                if (showQuizResult) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (selectedQuizOption == lesson.correctOptionIndex) "✅ Correct! ${lesson.quizExplanation}"
                                        else "❌ Not quite. ${lesson.quizExplanation}",
                                        fontSize = 11.sp,
                                        color = if (selectedQuizOption == lesson.correctOptionIndex) Color(0xFF34D399) else Color(0xFFF87171),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom button: Mark completed
                Button(
                    onClick = onToggleCompleted,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color(0xFF10B981) else Color(0xFF0EA5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isCompleted) "Completed (Tap to Undo)" else "Mark Lesson as Completed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
