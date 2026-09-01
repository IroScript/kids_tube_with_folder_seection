package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.VideoItem
import com.example.ui.theme.KidsAmber
import com.example.ui.theme.KidsBlue
import com.example.ui.theme.KidsCyan
import com.example.ui.theme.KidsGreen
import com.example.ui.theme.KidsOrange
import com.example.ui.theme.KidsPink
import com.example.ui.theme.KidsPurple
import com.example.ui.theme.KidsRed
import com.example.ui.theme.KidsYellow
import com.example.util.ThumbnailHelper
import com.example.util.YoutubeIdHelper

private val cardGradients = listOf(
    listOf(KidsRed, KidsOrange),
    listOf(KidsBlue, KidsCyan),
    listOf(KidsPurple, KidsPink),
    listOf(KidsGreen, KidsCyan),
    listOf(KidsOrange, KidsYellow)
)

@Composable
fun VideoShelf(
    videos: List<VideoItem>,
    allVideos: List<VideoItem> = videos,
    currentVideo: VideoItem?,
    folders: List<String>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Folders Filter Chips (if more than 1 folder)
        if (folders.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFolder == null,
                    onClick = { onSelectFolder(null) },
                    label = { Text("All Videos (${allVideos.size})", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KidsRed,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0x661E202E),
                        labelColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = null
                )

                folders.forEach { folder ->
                    val count = allVideos.count { it.folderName == folder || it.folderName.startsWith("$folder /") }
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onSelectFolder(folder) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedFolder == folder) Color.White else KidsAmber
                            )
                        },
                        label = { Text("$folder ($count)", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KidsOrange,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0x661E202E),
                            labelColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }
        }

        // Videos Shelf Carousel
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ask parents to add your favorite videos! 🧸",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            val lazyListState = rememberLazyListState()
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_shelf_row")
            ) {
                itemsIndexed(videos, key = { _, item -> item.id }) { index, video ->
                    val isSelected = currentVideo?.id == video.id
                    VideoCardItem(
                        video = video,
                        isSelected = isSelected,
                        index = index,
                        onClick = { onSelectVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoCardItem(
    video: VideoItem,
    isSelected: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var localThumbnail by remember(video.uriString) {
        mutableStateOf(ThumbnailHelper.getMemoryCachedThumbnail(video.uriString))
    }

    LaunchedEffect(video.uriString) {
        if (video.youtubeId == null && localThumbnail == null) {
            localThumbnail = ThumbnailHelper.getVideoThumbnail(context, video.uriString)
        }
    }

    val cardWidth by animateDpAsState(
        targetValue = if (isSelected) 190.dp else 160.dp,
        animationSpec = tween(250),
        label = "card_width"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) KidsYellow else Color.Transparent,
        animationSpec = tween(250),
        label = "border_color"
    )

    val gradient = cardGradients[index % cardGradients.size]

    Surface(
        modifier = Modifier
            .width(cardWidth)
            .height(130.dp)
            .shadow(
                elevation = if (isSelected) 10.dp else 4.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = if (isSelected) KidsYellow else Color.Black,
                spotColor = if (isSelected) KidsRed else Color.Black
            )
            .border(
                width = if (isSelected) 3.5.dp else 1.dp,
                color = if (isSelected) borderColor else Color(0x33FFFFFF),
                shape = RoundedCornerShape(22.dp)
            )
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .testTag("video_card_${video.id}"),
        color = if (isSelected) Color(0xFFE52D27) else Color(0xFF1E202E)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    video.youtubeId != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(YoutubeIdHelper.getThumbnailUrl(video.youtubeId))
                                .crossfade(true)
                                .build(),
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    localThumbnail != null -> {
                        Image(
                            bitmap = localThumbnail!!.asImageBitmap(),
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        // Playful colorful fallback banner
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(gradient)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Play badge overlay
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) KidsYellow else Color(0xCC000000),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Folder tag pill
                if (video.folderName.isNotEmpty() && video.folderName != "All Videos") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xB3000000),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.folderName,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            // Title Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
