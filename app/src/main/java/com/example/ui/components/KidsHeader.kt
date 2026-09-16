package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.KidsAmber
import com.example.ui.theme.KidsBlue
import com.example.ui.theme.KidsCyan
import com.example.ui.theme.KidsGreen
import com.example.ui.theme.KidsOrange
import com.example.ui.theme.KidsPurple
import com.example.ui.theme.KidsRed
import com.example.ui.theme.KidsYellow

@Composable
fun KidsHeader(
    isParentMode: Boolean,
    isToddlerLockActive: Boolean,
    screenTimeRemainingSeconds: Long?,
    onToddlerLockClick: () -> Unit,
    onParentModeClick: () -> Unit,
    onFolderManagementClick: () -> Unit = onParentModeClick,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "star_spin")
    val starAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // KidsTube Logo Branding
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = KidsRed,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(listOf(KidsRed, KidsOrange))
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Star",
                            tint = KidsYellow,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(starAngle)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KidsTube",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    }
                }
            }
        }

        // Right side controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Screen Timer Pill
            if (screenTimeRemainingSeconds != null && screenTimeRemainingSeconds > 0) {
                val mins = screenTimeRemainingSeconds / 60
                val secs = screenTimeRemainingSeconds % 60
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC2A2D3E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KidsAmber)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HourglassTop,
                            contentDescription = "Timer",
                            tint = KidsAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = KidsAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Toddler Screen Lock Toggle
            Surface(
                shape = CircleShape,
                color = if (isToddlerLockActive) KidsAmber else Color(0x44FFFFFF),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onToddlerLockClick() }
                    .testTag("toddler_lock_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isToddlerLockActive) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = "Lock Screen for Kids",
                        tint = if (isToddlerLockActive) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Folder Management Button (Protected parent action)
            Surface(
                shape = CircleShape,
                color = if (isParentMode) KidsGreen else Color(0x33FFFFFF),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isParentMode) KidsGreen else KidsAmber
                ),
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { onFolderManagementClick() }
                    .testTag("folder_management_button")
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Base Folder Icon
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder Management",
                        tint = if (isParentMode) Color.Black else KidsAmber,
                        modifier = Modifier.size(24.dp)
                    )

                    // Small Lock Badge attached at top-right
                    Surface(
                        shape = CircleShape,
                        color = if (isParentMode) KidsYellow else KidsRed,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isParentMode) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (isParentMode) Color.Black else Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }

            // Parental Controls Settings Button
            Surface(
                shape = CircleShape,
                color = if (isParentMode) KidsGreen else Color(0x33FFFFFF),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isParentMode) KidsGreen else KidsBlue
                ),
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { onParentModeClick() }
                    .testTag("parent_mode_button")
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Parent Dashboard",
                        tint = if (isParentMode) Color.Black else KidsBlue,
                        modifier = Modifier.size(24.dp)
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (isParentMode) KidsYellow else KidsRed,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isParentMode) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (isParentMode) Color.Black else Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
