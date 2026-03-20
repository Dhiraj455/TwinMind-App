package com.example.twinmind2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.twinmind2.ui.theme.CardBlueBg
import com.example.twinmind2.ui.theme.CardPeachBg
import com.example.twinmind2.ui.theme.TextPrimary
import com.example.twinmind2.ui.theme.TextSecondary

@Composable
fun HomeFeatureCards(
    onTodoClick: () -> Unit,
    onMemoriesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeatureCard(
            modifier = Modifier.weight(1f),
            backgroundColor = CardBlueBg,
            icon = Icons.Filled.CheckBox,
            iconTint = Color(0xFF3A7BD5),
            title = "To-Do",
            subtitle = "View Tasks",
            onClick = onTodoClick
        )
        FeatureCard(
            modifier = Modifier.weight(1f),
            backgroundColor = CardPeachBg,
            icon = Icons.Filled.Folder,
            iconTint = Color(0xFFE07828),
            title = "Notes & Chats",
            subtitle = "View Memories",
            onClick = onMemoriesClick
        )
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(110.dp)
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopEnd)
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$subtitle >",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
