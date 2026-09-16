package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.model.TrackedFolderItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.VideoItem
import com.example.ui.theme.KidsAmber
import com.example.ui.theme.KidsBlue
import com.example.ui.theme.KidsCyan
import com.example.ui.theme.KidsGreen
import com.example.ui.theme.KidsOrange
import com.example.ui.theme.KidsPurple
import com.example.ui.theme.KidsRed
import com.example.ui.theme.KidsYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentControlsSheet(
    videos: List<VideoItem>,
    isAutoPlay: Boolean,
    isShuffleMode: Boolean,
    screenTimerMinutes: Int?,
    trackedFolders: List<TrackedFolderItem> = emptyList(),
    onImportFolder: (Uri) -> Unit,
    onImportFiles: (List<Uri>) -> Unit,
    onScanDevice: () -> Unit,
    onRestoreSamples: () -> Unit,
    onRemoveVideo: (String) -> Unit,
    onClearAll: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetScreenTimer: (Int?) -> Unit,
    onToggleFolderEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveFolder: (String) -> Unit = {},
    onRescanFolder: (String) -> Unit = {},
    onReGrantFolderPermission: (String, Uri) -> Unit = { _, _ -> },
    isFolderManagerMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(isFolderManagerMode) {
        if (isFolderManagerMode) {
            lazyListState.scrollToItem(0)
        }
    }

    // Disable dragging dismiss and ensure dialog only closes on explicit Cross / Done button click
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )

    // Track which folder is awaiting permission re-grant
    var pendingReGrantFolderId by remember { mutableStateOf<String?>(null) }
    val reGrantPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val folderId = pendingReGrantFolderId
        if (folderId != null && uri != null) {
            onReGrantFolderPermission(folderId, uri)
        }
        pendingReGrantFolderId = null
    }

    // Runtime Permission Launcher for Storage & Media Videos
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val isGranted = perms.values.any { it } ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
             ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
             ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

        if (isGranted) {
            onScanDevice()
        } else {
            Toast.makeText(context, "Storage permission is required to scan device videos", Toast.LENGTH_LONG).show()
        }
    }

    fun handleScanDeviceClick() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            onScanDevice()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Folder picker launcher (Storage Access Framework)
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onImportFolder(it) }
    }

    // Multiple file picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportFiles(uris)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Intentionally empty: Window will NOT dismiss when clicking outside in empty space.
            // User MUST click the Cross [X] button or the Done button to close.
        },
        sheetState = sheetState,
        containerColor = Color(0xFF161824),
        contentColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top Header with Close (X) Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = KidsGreen.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = KidsGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Parent Dashboard 🛡️",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Manage folders, videos & safe viewing time",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Explicit Cross (X) Close Button
                Surface(
                    shape = CircleShape,
                    color = KidsRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, KidsRed.copy(alpha = 0.4f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_parent_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close Dashboard",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF2B2E42))
            Spacer(modifier = Modifier.height(10.dp))

            if (isFolderManagerMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = KidsOrange.copy(alpha = 0.2f),
                    border = BorderStroke(1.5.dp, KidsOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = KidsOrange, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Folder Management Active 📁", fontWeight = FontWeight.Bold, color = KidsOrange, fontSize = 14.sp)
                            Text("Add, toggle or remove child-safe video folders directly.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Scroll Guidance Hint Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E2235),
                border = BorderStroke(1.dp, KidsOrange.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(1)
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = KidsYellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "👇 Scroll down to manage Video Library (${videos.size} videos loaded) & safe timers",
                            color = KidsYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = KidsOrange.copy(alpha = 0.2f),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Scroll Down ⬇️",
                            color = KidsOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Landscape Side-by-Side Row for Section 1 (Sources) & Section 2 (Timers & Playback)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Column 1: Video & Folder Sources
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF202334)),
                                border = if (isFolderManagerMode) BorderStroke(2.dp, KidsOrange) else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "📁 Video & Folder Sources",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = KidsYellow
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { folderPicker.launch(null) },
                                            colors = ButtonDefaults.buttonColors(containerColor = KidsOrange),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("pick_folder_button")
                                        ) {
                                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Select Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { filePicker.launch(arrayOf("video/*")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = KidsBlue),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("pick_files_button")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add Videos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { handleScanDeviceClick() },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("scan_device_button"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KidsCyan)
                                        ) {
                                            Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Scan Device", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = onRestoreSamples,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("restore_samples_button"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KidsAmber)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sample Videos", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            // Column 2: Safe Viewing & Playback Controls
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF202334)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "⏱️ Safe Viewing & Controls",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = KidsYellow
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Auto-Play Next Video", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                            Text("Continuous playback for playlists", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Switch(
                                            checked = isAutoPlay,
                                            onCheckedChange = { onToggleAutoPlay() },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = KidsGreen
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = Color(0xFF33374E))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("🔀 Random Shuffle", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                            Text("ভিডিও র‍্যান্ডম ক্রমে চলবে", fontSize = 10.sp, color = KidsYellow)
                                        }
                                        Switch(
                                            checked = isShuffleMode,
                                            onCheckedChange = { onToggleShuffle() },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = KidsPurple
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Screen Time Limit Timer:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val timerOptions = listOf(null to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m")
                                        timerOptions.forEach { (mins, label) ->
                                            val isSelected = screenTimerMinutes == mins
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSetScreenTimer(mins) },
                                                label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = KidsAmber,
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = Color(0xFF141520),
                                                    labelColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                border = null,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 2.5: Tracked Folders Management
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF25283C),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOpen,
                                            contentDescription = null,
                                            tint = KidsYellow,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "📁 Tracked Folders (${trackedFolders.size})",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = KidsYellow
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Disabled or unpermitted folders are hidden from kids. Removing a folder keeps files on storage 100% safe.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    if (trackedFolders.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF202334)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No folders tracked yet. Click 'Select Folder' above to add child-safe folders.",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(trackedFolders, key = { it.id }) { folder ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!folder.isPermissionGranted) Color(0xFF2D1E22) else Color(0xFF202334)
                                ),
                                border = if (!folder.isPermissionGranted) BorderStroke(1.dp, KidsRed.copy(alpha = 0.5f)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FolderOpen,
                                                contentDescription = null,
                                                tint = if (!folder.isPermissionGranted) KidsRed else if (folder.isEnabled) KidsGreen else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = folder.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${folder.videoCount} videos",
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    if (!folder.isPermissionGranted) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = KidsRed.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "⚠️ Permission Lost",
                                                                color = KidsRed,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else if (folder.isEnabled) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = KidsGreen.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "Active for Kids",
                                                                color = KidsGreen,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color.Gray.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = "Hidden from Kids",
                                                                color = Color.LightGray,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = folder.isEnabled,
                                                onCheckedChange = { onToggleFolderEnabled(folder.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = KidsGreen
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { onRescanFolder(folder.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = "Rescan folder",
                                                    tint = KidsCyan,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(2.dp))
                                            IconButton(
                                                onClick = { onRemoveFolder(folder.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Remove folder from library",
                                                    tint = KidsRed.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (!folder.isPermissionGranted) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                pendingReGrantFolderId = folder.id
                                                reGrantPicker.launch(null)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KidsRed),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Re-Grant Storage Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Manage Video Library Header
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF25283C),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleFilled,
                                        contentDescription = null,
                                        tint = KidsYellow,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🎬 Video Library (${videos.size} Videos)",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = KidsYellow
                                    )
                                }

                                if (videos.isNotEmpty()) {
                                    TextButton(
                                        onClick = onClearAll,
                                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = KidsRed),
                                        modifier = Modifier.testTag("clear_all_videos_button")
                                    ) {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear All", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Video List Items
                    if (videos.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF222536)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No videos loaded yet. Click 'Select Folder', 'Scan Device' or 'Sample Videos' above.",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(videos, key = { it.id }) { video ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF222536)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayCircleFilled,
                                            contentDescription = null,
                                            tint = KidsOrange,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = video.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Folder: ${video.folderName}",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onRemoveVideo(video.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete video",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Dynamic "Scroll Down" Indicator when list can scroll forward
                androidx.compose.animation.AnimatedVisibility(
                    visible = lazyListState.canScrollForward,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = KidsPurple,
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable {
                            coroutineScope.launch {
                                lazyListState.animateScrollToItem(lazyListState.layoutInfo.totalItemsCount - 1)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scroll down for ${videos.size} videos ⬇️",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Done & Exit Button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = KidsRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("exit_parent_dashboard_button")
            ) {
                Text("Done & Exit to Kids View", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
