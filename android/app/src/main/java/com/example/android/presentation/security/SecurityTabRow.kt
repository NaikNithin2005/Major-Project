package com.example.android.presentation.security

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SecurityTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "Trusted Users",
        "Untrusted Users",
        "Quarantined SMS",
        "Blocked Users",
        "Blocked SMS"
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color(0xFF0F172A),
        contentColor = Color(0xFF38BDF8),
        edgePadding = 12.dp,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                color = Color(0xFF38BDF8),
                height = 3.dp
            )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTabIndex == index) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                }
            )
        }
    }
}

