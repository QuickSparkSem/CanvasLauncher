package com.example.canvaslauncher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.example.canvaslauncher.data.AppInfo
import com.example.canvaslauncher.data.CanvasWaypoint
import com.example.canvaslauncher.data.StatusBarMode
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.data.WebQuickPreset
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import com.example.canvaslauncher.shizuku.VirtualDisplayWindowManager
import com.example.canvaslauncher.web.AdBlockerEngine
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class CanvasTool { PAN, INTERACT, SELECT, LASSO }

data class InstalledBrowserInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasLauncherApp(
    items: MutableList<CanvasItem>,
    appList: List<AppInfo>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    shizukuReady: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRequestPickSystemWidget: () -> Unit,
    onRequestPickSystemShortcut: () -> Unit,
    onStatusBarModeChanged: (StatusBarMode) -> Unit,
    onShowSelfInDrawerChanged: (Boolean) -> Unit,
    incomingText: String?,
    incomingImageUri: Uri?,
    incomingAudioUri: Uri?,
    onIncomingConsumed: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var liveShizukuReady by remember(shizukuReady) {
        mutableStateOf(shizukuReady || VirtualDisplayWindowManager.isShizukuReady())
    }
    DisposableEffect(Unit) {
        val binderListener = Shizuku.OnBinderReceivedListener {
            liveShizukuReady = VirtualDisplayWindowManager.isShizukuReady()
        }
        val deadListener = Shizuku.OnBinderDeadListener {
            liveShizukuReady = false
        }
        val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            liveShizukuReady = grantResult == PackageManager.PERMISSION_GRANTED
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderListener)
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addRequestPermissionResultListener(permListener)
        }
        onDispose {
            runCatching {
                Shizuku.removeBinderReceivedListener(binderListener)
                Shizuku.removeBinderDeadListener(deadListener)
                Shizuku.removeRequestPermissionResultListener(permListener)
            }
        }
    }

    var showAppDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPhotoChoiceDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showWebAppDialog by remember { mutableStateOf(false) }

    val savedWaypoints = remember {
        mutableStateListOf<CanvasWaypoint>().apply { addAll(StorageManager.loadWaypoints(context)) }
    }
    var showWaypointsMenu by remember { mutableStateOf(false) }
    var showSaveWaypointDialog by remember { mutableStateOf(false) }
    var newWaypointName by remember { mutableStateOf("") }

    val webQuickPresets = remember {
        mutableStateListOf<WebQuickPreset>().apply { addAll(StorageManager.loadWebQuickPresets(context)) }
    }
    val installedBrowsers = remember { queryInstalledBrowsers(context) }
    val adBlockedCounts = remember { mutableStateMapOf<String, Int>() }

    var resizingItemId by remember { mutableStateOf<String?>(null) }
    var windowSessionRevision by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        when {
            resizingItemId != null -> resizingItemId = null
            showAppDrawer -> showAppDrawer = false
            showSettingsDialog -> showSettingsDialog = false
            showPhotoChoiceDialog -> showPhotoChoiceDialog = false
            showAudioDialog -> showAudioDialog = false
            showWebAppDialog -> showWebAppDialog = false
            showSaveWaypointDialog -> showSaveWaypointDialog = false
            showWaypointsMenu -> showWaypointsMenu = false
        }
    }

    var backgroundAlpha by remember { mutableFloatStateOf(StorageManager.getBackgroundAlpha(context)) }
    var drawerAlpha by remember { mutableFloatStateOf(StorageManager.getDrawerAlpha(context)) }
    var hapticStrength by remember { mutableFloatStateOf(StorageManager.getDrawerHapticStrength(context)) }
    var statusBarMode by remember { mutableStateOf(StorageManager.getStatusBarMode(context)) }
    var showSelfInDrawer by remember { mutableStateOf(StorageManager.getShowSelfInDrawer(context)) }

    var activeTool by remember { mutableStateOf(CanvasTool.PAN) }
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var viewportWidthPx by remember { mutableFloatStateOf(1080f) }
    var viewportHeightPx by remember { mutableFloatStateOf(2200f) }

    val lassoPoints = remember { mutableStateListOf<Offset>() }
    val selectedIds = remember { mutableStateListOf<String>() }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuScreenPos by remember { mutableStateOf(Offset.Zero) }
    var targetItemForMenu by remember { mutableStateOf<CanvasItem?>(null) }

    var editingItem by remember { mutableStateOf<CanvasItem?>(null) }
    var editTitleText by remember { mutableStateOf("") }
    var editContentText by remember { mutableStateOf("") }
    var editColorHex by remember { mutableLongStateOf(0x00000000L) }

    var activeSessionDialogItem by remember { mutableStateOf<CanvasItem?>(null) }
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    val activePlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { activePlayer.value?.release() } }
    }

    fun toggleAudioPlayback(item: CanvasItem) {
        val path = item.audioPath ?: return
        if (playingAudioId == item.id) {
            runCatching { activePlayer.value?.stop(); activePlayer.value?.release() }
            activePlayer.value = null
            playingAudioId = null
        } else {
            runCatching {
                activePlayer.value?.release()
                activePlayer.value = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    start()
                    setOnCompletionListener {
                        playingAudioId = null
                        release()
                        activePlayer.value = null
                    }
                }
                playingAudioId = item.id
            }.onFailure {
                Toast.makeText(context, "Impossibile riprodurre l'audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openAppAsCanvasWindow(app: AppInfo, worldX: Float? = null, worldY: Float? = null) {
        val wx = worldX ?: ((-cameraOffset.x + 80f) / zoomScale)
        val wy = worldY ?: ((-cameraOffset.y + 180f) / zoomScale)
        val winWidthDp = 300f
        val winHeightDp = 430f

        val newItem = CanvasItem(
            id = UUID.randomUUID().toString(),
            title = app.label,
            packageName = app.packageName,
            x = wx,
            y = wy,
            widthDp = winWidthDp,
            heightDp = winHeightDp,
            colorHex = 0xFF141822L,
            type = ItemType.APP_WINDOW
        )

        val wPx = (winWidthDp * density.density).roundToInt()
        val hPx = ((winHeightDp - 36f) * density.density).roundToInt()

        VirtualDisplayWindowManager.launchAppInWindowOrFallback(
            context = context,
            item = newItem,
            widthPx = wPx,
            heightPx = hPx,
            onVirtualDisplayCreated = {
                items.add(newItem)
                StorageManager.saveItems(context, items)
                windowSessionRevision++
            },
            onFallbackFreeformLaunched = {}
        )
    }

    fun openWebAppOnCanvas(
        title: String,
        rawUrl: String,
        browserPackage: String?,
        adBlockEnabled: Boolean = true
    ) {
        val normalizedUrl = normalizeWebUrl(rawUrl)
        val worldX = (-cameraOffset.x + 80f) / zoomScale
        val worldY = (-cameraOffset.y + 180f) / zoomScale
        val winWidthDp = 315f
        val winHeightDp = 440f

        val resolvedTitle = title.ifBlank {
            normalizedUrl.removePrefix("https://").removePrefix("http://").take(24)
        }

        val newItem = CanvasItem(
            id = UUID.randomUUID().toString(),
            title = resolvedTitle,
            url = normalizedUrl,
            browserPackage = browserPackage,
            adBlockEnabled = adBlockEnabled,
            x = worldX,
            y = worldY,
            widthDp = winWidthDp,
            heightDp = winHeightDp,
            colorHex = 0xFF141822L,
            type = ItemType.WEB_APP
        )

        items.add(newItem)
        StorageManager.saveItems(context, items)

        if (!browserPackage.isNullOrBlank() && VirtualDisplayWindowManager.isShizukuReady()) {
            val wPx = (winWidthDp * density.density).roundToInt()
            val hPx = ((winHeightDp - 36f) * density.density).roundToInt()
            VirtualDisplayWindowManager.launchAppInWindowOrFallback(
                context = context,
                item = newItem,
                widthPx = wPx,
                heightPx = hPx,
                initialUrl = normalizedUrl,
                onVirtualDisplayCreated = { windowSessionRevision++ },
                onFallbackFreeformLaunched = {}
            )
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            StorageManager.copyUriToInternalStorage(context, it, "snapshots", "jpg")?.let { savedPath ->
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Foto",
                        imagePath = savedPath,
                        x = (-cameraOffset.x + 180f) / zoomScale,
                        y = (-cameraOffset.y + 300f) / zoomScale,
                        widthDp = 190f,
                        heightDp = 175f,
                        type = ItemType.IMAGE_SNAPSHOT
                    )
                )
                StorageManager.saveItems(context, items)
            }
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            StorageManager.copyUriToInternalStorage(context, uri, "snapshots", "jpg")?.let { savedPath ->
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Scatto",
                        imagePath = savedPath,
                        x = (-cameraOffset.x + 180f) / zoomScale,
                        y = (-cameraOffset.y + 300f) / zoomScale,
                        widthDp = 190f,
                        heightDp = 175f,
                        type = ItemType.IMAGE_SNAPSHOT
                    )
                )
                StorageManager.saveItems(context, items)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val tempFile = File(context.cacheDir, "cam_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchQuickCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val tempFile = File(context.cacheDir, "cam_${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = StorageManager.getDisplayNameFromUri(context, it)
            StorageManager.copyUriToInternalStorage(context, it, "audio_memos", "m4a")?.let { savedPath ->
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = name,
                        audioPath = savedPath,
                        x = (-cameraOffset.x + 180f) / zoomScale,
                        y = (-cameraOffset.y + 300f) / zoomScale,
                        widthDp = 115f,
                        heightDp = 105f,
                        type = ItemType.AUDIO_MEMO
                    )
                )
                StorageManager.saveItems(context, items)
            }
        }
    }

    val fileLinkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            items.add(
                CanvasItem(
                    id = UUID.randomUUID().toString(),
                    title = StorageManager.getDisplayNameFromUri(context, it),
                    fileUri = it.toString(),
                    fileMimeType = context.contentResolver.getType(it) ?: "*/*",
                    x = (-cameraOffset.x + 180f) / zoomScale,
                    y = (-cameraOffset.y + 300f) / zoomScale,
                    widthDp = 110f,
                    heightDp = 105f,
                    type = ItemType.FILE_LINK
                )
            )
            StorageManager.saveItems(context, items)
        }
    }

    LaunchedEffect(incomingText, incomingImageUri, incomingAudioUri) {
        var added = false
        val worldX = (-cameraOffset.x + 170f) / zoomScale
        val worldY = (-cameraOffset.y + 280f) / zoomScale

        if (!incomingText.isNullOrBlank()) {
            val trimmed = incomingText.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                openWebAppOnCanvas("", trimmed, null, true)
            } else {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Nota",
                        content = trimmed,
                        x = worldX,
                        y = worldY,
                        widthDp = 210f,
                        heightDp = 145f,
                        type = ItemType.NOTE
                    )
                )
            }
            added = true
        }
        if (incomingImageUri != null) {
            StorageManager.copyUriToInternalStorage(context, incomingImageUri, "snapshots", "jpg")?.let {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Foto",
                        imagePath = it,
                        x = worldX + 35f,
                        y = worldY + 35f,
                        widthDp = 190f,
                        heightDp = 175f,
                        type = ItemType.IMAGE_SNAPSHOT
                    )
                )
                added = true
            }
        }
        if (incomingAudioUri != null) {
            StorageManager.copyUriToInternalStorage(context, incomingAudioUri, "audio_memos", "m4a")?.let {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Audio",
                        audioPath = it,
                        x = worldX + 45f,
                        y = worldY + 45f,
                        widthDp = 115f,
                        heightDp = 105f,
                        type = ItemType.AUDIO_MEMO
                    )
                )
                added = true
            }
        }
        if (added) {
            StorageManager.saveItems(context, items)
            onIncomingConsumed()
        }
    }

    fun openSystemWallpaperChooser() {
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Scegli sfondo"))
        }.onFailure {
            Toast.makeText(context, "Selettore sfondi non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    fun groupSelectedIntoSession() {
        val selectedObjects = items.filter { selectedIds.contains(it.id) }
        if (selectedObjects.size < 2) return
        items.add(
            CanvasItem(
                id = UUID.randomUUID().toString(),
                title = "Sessione (${selectedObjects.size})",
                content = selectedObjects.joinToString("\n") { "• ${it.title}" },
                x = selectedObjects.map { it.x }.average().toFloat(),
                y = selectedObjects.map { it.y }.average().toFloat(),
                widthDp = 115f,
                heightDp = 110f,
                type = ItemType.SESSION_GROUP,
                sessionPackages = selectedObjects.mapNotNull { it.packageName }.distinct()
            )
        )
        selectedIds.clear()
        StorageManager.saveItems(context, items)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                viewportWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
                viewportHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
            }
            .background(Color(0xFF0C0E14).copy(alpha = backgroundAlpha))
    ) {
        // LIVELLO 1: SFONDO + GESTURE PAN/ZOOM TELA
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (resizingItemId != null) resizingItemId = null
                            if (selectedIds.isNotEmpty()) selectedIds.clear()
                        },
                        onLongPress = { pressPos ->
                            targetItemForMenu = null
                            contextMenuScreenPos = pressPos
                            showContextMenu = true
                        }
                    )
                }
                .pointerInput(activeTool) {
                    if (activeTool == CanvasTool.PAN) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var pastSlop = false
                            var totalPan = Offset.Zero
                            val slop = viewConfiguration.touchSlop
                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                totalPan += panChange
                                if (!pastSlop && (pressedCount > 1 || totalPan.getDistance() > slop)) {
                                    pastSlop = true
                                }
                                if (pastSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val oldZoom = zoomScale
                                    val newZoom = (zoomScale * zoomChange).coerceIn(0.35f, 2.5f)
                                    val factor = newZoom / oldZoom
                                    cameraOffset = centroid - (centroid - cameraOffset) * factor + panChange
                                    zoomScale = newZoom
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                }
        ) {
            if (backgroundAlpha > 0.08f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = 84f * zoomScale
                    if (step > 18f) {
                        var x = cameraOffset.x % step
                        while (x < size.width) {
                            var y = cameraOffset.y % step
                            while (y < size.height) {
                                drawCircle(
                                    color = Color(0xFF283044).copy(alpha = backgroundAlpha),
                                    radius = 2.5f,
                                    center = Offset(x, y)
                                )
                                y += step
                            }
                            x += step
                        }
                    }
                }
            }
        }

        // LIVELLO 2: OGGETTI E FINESTRE SULLA TELA
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    transformOrigin = TransformOrigin(0f, 0f),
                    translationX = cameraOffset.x,
                    translationY = cameraOffset.y,
                    scaleX = zoomScale,
                    scaleY = zoomScale
                )
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIds.contains(item.id)
                val isResizing = resizingItemId == item.id

                val screenLeft = item.x * zoomScale + cameraOffset.x
                val screenTop = item.y * zoomScale + cameraOffset.y
                val screenRight = screenLeft + item.widthDp * density.density * zoomScale
                val screenBottom = screenTop + item.heightDp * density.density * zoomScale
                val isVisibleInViewport =
                    screenRight >= -120f && screenLeft <= viewportWidthPx + 120f &&
                    screenBottom >= -120f && screenTop <= viewportHeightPx + 120f

                val desktopLabelStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(Color(0xDD000000), Offset(1f, 2f), 4f)
                )

                val itemDragModifier = if (activeTool == CanvasTool.SELECT &&
                    item.type != ItemType.APP_WINDOW && item.type != ItemType.WEB_APP
                ) {
                    Modifier.pointerInput(item.id, activeTool) {
                        detectDragGestures(
                            onDragEnd = { StorageManager.saveItems(context, items) }
                        ) { change, dragAmount ->
                            change.consume()
                            val idx = items.indexOfFirst { it.id == item.id }
                            if (idx >= 0) {
                                items[idx] = items[idx].copy(
                                    x = items[idx].x + dragAmount.x,
                                    y = items[idx].y + dragAmount.y
                                )
                            }
                        }
                    }
                } else {
                    Modifier
                }

                val selectionModifier = if (isSelected || isResizing) {
                    Modifier.border(
                        width = 2.dp,
                        color = if (isResizing) Color(0xFF38BDF8) else Color(0xFF60A5FA),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(item.x.roundToInt(), item.y.roundToInt()) }
                        .size(item.widthDp.dp, item.heightDp.dp)
                        .then(selectionModifier)
                        .then(itemDragModifier)
                ) {
                    when (item.type) {
                        ItemType.APP_WINDOW -> {
                            @Suppress("UNUSED_VARIABLE")
                            val rev = windowSessionRevision
                            val isSessionLive = VirtualDisplayWindowManager.hasActiveSession(item.id)

                            LaunchedEffect(isVisibleInViewport, isSessionLive) {
                                VirtualDisplayWindowManager.getSession(item.id)?.let { session ->
                                    VirtualDisplayWindowManager.updateSurfaceAttachment(
                                        item.id,
                                        session.attachedSurface,
                                        isVisibleInViewport
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(10.dp, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF141822))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(Color(0xFF1E2433))
                                        .pointerInput(item.id) {
                                            detectDragGestures(
                                                onDragEnd = { StorageManager.saveItems(context, items) }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                val idx = items.indexOfFirst { it.id == item.id }
                                                if (idx >= 0) {
                                                    items[idx] = items[idx].copy(
                                                        x = items[idx].x + dragAmount.x,
                                                        y = items[idx].y + dragAmount.y
                                                    )
                                                }
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { VirtualDisplayWindowManager.injectBackKey(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val newFallback = !item.imeFallbackDisplay
                                            items[index] = item.copy(imeFallbackDisplay = newFallback)
                                            StorageManager.saveItems(context, items)
                                            VirtualDisplayWindowManager.getSession(item.id)?.let { s ->
                                                VirtualDisplayWindowManager.setDisplayImePolicy(
                                                    s.displayId,
                                                    if (newFallback) VirtualDisplayWindowManager.IME_POLICY_FALLBACK_DISPLAY
                                                    else VirtualDisplayWindowManager.IME_POLICY_LOCAL
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Keyboard,
                                            "Tastiera",
                                            tint = if (item.imeFallbackDisplay) Color(0xFF94A3B8) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { resizingItemId = if (isResizing) null else item.id },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.AspectRatio,
                                            "Ridimensiona",
                                            tint = if (isResizing) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            VirtualDisplayWindowManager.closeSession(item.id)
                                            items.removeAt(index)
                                            StorageManager.saveItems(context, items)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, "Chiudi", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (isSessionLive) {
                                        var renderedW by remember { mutableFloatStateOf(1f) }
                                        var renderedH by remember { mutableFloatStateOf(1f) }
                                        var touchDownTime by remember { mutableLongStateOf(0L) }

                                        AndroidView(
                                            factory = { ctx ->
                                                TextureView(ctx).apply {
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    VirtualDisplayWindowManager.getSession(item.id)?.textureView = this
                                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                                            VirtualDisplayWindowManager.updateSurfaceAttachment(item.id, Surface(st), isVisibleInViewport)
                                                        }
                                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                                            VirtualDisplayWindowManager.resizeSession(item.id, w, h)
                                                        }
                                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                                            VirtualDisplayWindowManager.updateSurfaceAttachment(item.id, null, false)
                                                            return true
                                                        }
                                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .onGloballyPositioned { coords ->
                                                    renderedW = coords.size.width.toFloat().coerceAtLeast(1f)
                                                    renderedH = coords.size.height.toFloat().coerceAtLeast(1f)
                                                }
                                                .pointerInput(item.id) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull() ?: continue
                                                            change.consume()
                                                            when {
                                                                change.pressed && !change.previousPressed -> {
                                                                    touchDownTime = SystemClock.uptimeMillis()
                                                                    VirtualDisplayWindowManager.injectMappedTouch(
                                                                        item.id, MotionEvent.ACTION_DOWN, touchDownTime,
                                                                        change.position.x, change.position.y, renderedW, renderedH
                                                                    )
                                                                }
                                                                change.pressed && change.previousPressed -> {
                                                                    VirtualDisplayWindowManager.injectMappedTouch(
                                                                        item.id, MotionEvent.ACTION_MOVE, touchDownTime,
                                                                        change.position.x, change.position.y, renderedW, renderedH
                                                                    )
                                                                }
                                                                !change.pressed && change.previousPressed -> {
                                                                    VirtualDisplayWindowManager.injectMappedTouch(
                                                                        item.id, MotionEvent.ACTION_UP, touchDownTime,
                                                                        change.position.x, change.position.y, renderedW, renderedH
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                        )
                                    } else {
                                        if (!item.lastSnapshotPath.isNullOrEmpty() && File(item.lastSnapshotPath).exists()) {
                                            AsyncImage(
                                                model = File(item.lastSnapshotPath),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color(0x880B0E17)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Button(
                                                onClick = {
                                                    val wPx = (item.widthDp * density.density).roundToInt()
                                                    val hPx = ((item.heightDp - 36f) * density.density).roundToInt()
                                                    VirtualDisplayWindowManager.launchAppInWindowOrFallback(
                                                        context = context,
                                                        item = item,
                                                        widthPx = wPx,
                                                        heightPx = hPx,
                                                        onVirtualDisplayCreated = { windowSessionRevision++ },
                                                        onFallbackFreeformLaunched = {}
                                                    )
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (liveShizukuReady) "Riprendi su Tela" else "Apri in Freeform", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ItemType.WEB_APP -> {
                            val targetUrl = item.url ?: "https://www.google.com"
                            val blockedCount = adBlockedCounts[item.id] ?: 0
                            val usesExternalBrowser = !item.browserPackage.isNullOrBlank()
                            @Suppress("UNUSED_VARIABLE")
                            val rev = windowSessionRevision
                            val isExternalSessionLive = usesExternalBrowser &&
                                VirtualDisplayWindowManager.hasActiveSession(item.id)

                            LaunchedEffect(isVisibleInViewport) {
                                if (!usesExternalBrowser) {
                                    VirtualDisplayWindowManager.activeWebViews[item.id]?.let { wv ->
                                        if (isVisibleInViewport) wv.onResume() else wv.onPause()
                                    }
                                } else {
                                    VirtualDisplayWindowManager.getSession(item.id)?.let { s ->
                                        VirtualDisplayWindowManager.updateSurfaceAttachment(
                                            item.id, s.attachedSurface, isVisibleInViewport
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(10.dp, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF141822))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(Color(0xFF1E2433))
                                        .pointerInput(item.id) {
                                            detectDragGestures(
                                                onDragEnd = { StorageManager.saveItems(context, items) }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                val idx = items.indexOfFirst { it.id == item.id }
                                                if (idx >= 0) {
                                                    items[idx] = items[idx].copy(
                                                        x = items[idx].x + dragAmount.x,
                                                        y = items[idx].y + dragAmount.y
                                                    )
                                                }
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (usesExternalBrowser) VirtualDisplayWindowManager.injectBackKey(item.id)
                                            else VirtualDisplayWindowManager.activeWebViews[item.id]?.let { if (it.canGoBack()) it.goBack() }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }

                                    if (!usesExternalBrowser) {
                                        IconButton(
                                            onClick = {
                                                VirtualDisplayWindowManager.activeWebViews[item.id]?.let { if (it.canGoForward()) it.goForward() }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Avanti", tint = Color.White, modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(
                                            onClick = { VirtualDisplayWindowManager.activeWebViews[item.id]?.reload() },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(Icons.Rounded.Refresh, "Ricarica", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                                        }
                                    }

                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                    )

                                    if (!usesExternalBrowser) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (item.adBlockEnabled) Color(0x33FB923C) else Color(0x2294A3B8))
                                                .clickable {
                                                    val newAdBlock = !item.adBlockEnabled
                                                    items[index] = item.copy(adBlockEnabled = newAdBlock)
                                                    StorageManager.saveItems(context, items)
                                                    VirtualDisplayWindowManager.activeWebViews[item.id]?.reload()
                                                }
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Shield,
                                                "AdBlock",
                                                tint = if (item.adBlockEnabled) Color(0xFFFB923C) else Color(0xFF64748B),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            if (item.adBlockEnabled && blockedCount > 0) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("$blockedCount", color = Color(0xFFFB923C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { resizingItemId = if (isResizing) null else item.id },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.AspectRatio,
                                            "Ridimensiona",
                                            tint = if (isResizing) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (usesExternalBrowser) VirtualDisplayWindowManager.closeSession(item.id)
                                            else VirtualDisplayWindowManager.activeWebViews.remove(item.id)?.destroy()
                                            items.removeAt(index)
                                            StorageManager.saveItems(context, items)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, "Chiudi", tint = Color(0xFFF87171), modifier = Modifier.size(15.dp))
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (usesExternalBrowser) {
                                        if (isExternalSessionLive) {
                                            var renderedW by remember { mutableFloatStateOf(1f) }
                                            var renderedH by remember { mutableFloatStateOf(1f) }
                                            var touchDownTime by remember { mutableLongStateOf(0L) }

                                            AndroidView(
                                                factory = { ctx ->
                                                    TextureView(ctx).apply {
                                                        layoutParams = ViewGroup.LayoutParams(
                                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                                            ViewGroup.LayoutParams.MATCH_PARENT
                                                        )
                                                        VirtualDisplayWindowManager.getSession(item.id)?.textureView = this
                                                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                                                VirtualDisplayWindowManager.updateSurfaceAttachment(item.id, Surface(st), isVisibleInViewport)
                                                            }
                                                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                                                VirtualDisplayWindowManager.resizeSession(item.id, w, h)
                                                            }
                                                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                                                VirtualDisplayWindowManager.updateSurfaceAttachment(item.id, null, false)
                                                                return true
                                                            }
                                                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .onGloballyPositioned { coords ->
                                                        renderedW = coords.size.width.toFloat().coerceAtLeast(1f)
                                                        renderedH = coords.size.height.toFloat().coerceAtLeast(1f)
                                                    }
                                                    .pointerInput(item.id) {
                                                        awaitPointerEventScope {
                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.firstOrNull() ?: continue
                                                                change.consume()
                                                                when {
                                                                    change.pressed && !change.previousPressed -> {
                                                                        touchDownTime = SystemClock.uptimeMillis()
                                                                        VirtualDisplayWindowManager.injectMappedTouch(
                                                                            item.id, MotionEvent.ACTION_DOWN, touchDownTime,
                                                                            change.position.x, change.position.y, renderedW, renderedH
                                                                        )
                                                                    }
                                                                    change.pressed && change.previousPressed -> {
                                                                        VirtualDisplayWindowManager.injectMappedTouch(
                                                                            item.id, MotionEvent.ACTION_MOVE, touchDownTime,
                                                                            change.position.x, change.position.y, renderedW, renderedH
                                                                        )
                                                                    }
                                                                    !change.pressed && change.previousPressed -> {
                                                                        VirtualDisplayWindowManager.injectMappedTouch(
                                                                            item.id, MotionEvent.ACTION_UP, touchDownTime,
                                                                            change.position.x, change.position.y, renderedW, renderedH
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                            )
                                        } else {
                                            val browserLabel = installedBrowsers
                                                .find { it.packageName == item.browserPackage }?.label
                                                ?: item.browserPackage ?: "Browser"
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(Color(0x990B0E17)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val wPx = (item.widthDp * density.density).roundToInt()
                                                        val hPx = ((item.heightDp - 36f) * density.density).roundToInt()
                                                        VirtualDisplayWindowManager.launchAppInWindowOrFallback(
                                                            context = context,
                                                            item = item,
                                                            widthPx = wPx,
                                                            heightPx = hPx,
                                                            initialUrl = targetUrl,
                                                            onVirtualDisplayCreated = { windowSessionRevision++ },
                                                            onFallbackFreeformLaunched = {}
                                                        )
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                                ) {
                                                    Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(if (liveShizukuReady) "Apri $browserLabel su Tela" else "Apri in $browserLabel", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    } else {
                                        AndroidView(
                                            factory = { ctx ->
                                                VirtualDisplayWindowManager.activeWebViews.getOrPut(item.id) {
                                                    createConfiguredWebView(
                                                        context = ctx,
                                                        initialUrl = targetUrl,
                                                        isAdBlockEnabled = { items.find { it.id == item.id }?.adBlockEnabled ?: true },
                                                        onAdBlocked = { adBlockedCounts[item.id] = (adBlockedCounts[item.id] ?: 0) + 1 },
                                                        onUrlOrTitleUpdated = { newUrl, newTitle ->
                                                            val idx = items.indexOfFirst { it.id == item.id }
                                                            if (idx >= 0) {
                                                                items[idx] = items[idx].copy(
                                                                    url = newUrl,
                                                                    title = newTitle.takeIf { it.isNotBlank() } ?: items[idx].title
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        ItemType.SYSTEM_WIDGET -> {
                            val widgetInfo = remember(item.appWidgetId) {
                                if (item.appWidgetId != -1) {
                                    runCatching { appWidgetManager.getAppWidgetInfo(item.appWidgetId) }.getOrNull()
                                } else null
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            targetItemForMenu = item
                                            contextMenuScreenPos = Offset(screenLeft, screenTop)
                                            showContextMenu = true
                                        }
                                    )
                            ) {
                                if (widgetInfo != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            runCatching {
                                                appWidgetHost.createView(ctx, item.appWidgetId, widgetInfo).apply {
                                                    setAppWidget(item.appWidgetId, widgetInfo)
                                                }
                                            }.getOrElse {
                                                TextView(ctx).apply {
                                                    text = item.title
                                                    setTextColor(android.graphics.Color.WHITE)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(item.title, style = desktopLabelStyle, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                        }

                        ItemType.NOTE -> {
                            val noteBg = if (item.colorHex == 0x00000000L) Color.Transparent
                            else Color(item.colorHex).copy(alpha = 0.72f)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(noteBg)
                                    .combinedClickable(
                                        onClick = {
                                            if (activeTool == CanvasTool.SELECT) {
                                                toggleItemSelection(selectedIds, item.id)
                                            } else {
                                                editingItem = item
                                                editTitleText = item.title
                                                editContentText = item.content
                                                editColorHex = item.colorHex
                                            }
                                        },
                                        onLongClick = {
                                            targetItemForMenu = item
                                            contextMenuScreenPos = Offset(screenLeft, screenTop)
                                            showContextMenu = true
                                        }
                                    )
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = desktopLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    Text(text = item.content, style = desktopLabelStyle.copy(fontSize = 13.sp))
                                }
                            }
                        }

                        ItemType.IMAGE_SNAPSHOT -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = {
                                            if (activeTool == CanvasTool.SELECT) {
                                                toggleItemSelection(selectedIds, item.id)
                                            } else {
                                                editingItem = item
                                                editTitleText = item.title
                                                editContentText = item.content
                                                editColorHex = item.colorHex
                                            }
                                        },
                                        onLongClick = {
                                            targetItemForMenu = item
                                            contextMenuScreenPos = Offset(screenLeft, screenTop)
                                            showContextMenu = true
                                        }
                                    )
                            ) {
                                if (!item.imagePath.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = File(item.imagePath),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Text(
                                    text = item.title,
                                    style = desktopLabelStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = {
                                            if (activeTool == CanvasTool.SELECT) {
                                                toggleItemSelection(selectedIds, item.id)
                                            } else {
                                                when (item.type) {
                                                    ItemType.APP_SHORTCUT -> item.packageName?.let { pkg ->
                                                        val appObj = appList.find { it.packageName == pkg }
                                                        if (appObj != null) launchApp(context, appObj) else launchPackage(context, pkg)
                                                    }
                                                    ItemType.SYSTEM_SHORTCUT -> openSystemOrCustomShortcut(context, item)
                                                    ItemType.FILE_LINK -> openLinkedFile(context, item)
                                                    ItemType.AUDIO_MEMO -> toggleAudioPlayback(item)
                                                    ItemType.SESSION_GROUP -> {
                                                        if (item.sessionPackages.size == 1) launchPackage(context, item.sessionPackages.first())
                                                        else if (item.sessionPackages.isNotEmpty()) activeSessionDialogItem = item
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            targetItemForMenu = item
                                            contextMenuScreenPos = Offset(screenLeft, screenTop)
                                            showContextMenu = true
                                        }
                                    )
                                    .padding(4.dp)
                            ) {
                                when (item.type) {
                                    ItemType.APP_SHORTCUT -> {
                                        val appIcon = remember(item.packageName, appList) {
                                            appList.find { it.packageName == item.packageName }
                                                ?.icon?.toBitmap(width = 120, height = 120)?.asImageBitmap()
                                        }
                                        if (appIcon != null) {
                                            Image(bitmap = appIcon, contentDescription = item.title, modifier = Modifier.size(56.dp))
                                        } else {
                                            Icon(Icons.Rounded.Apps, item.title, tint = Color(0xFF38BDF8), modifier = Modifier.size(52.dp))
                                        }
                                    }
                                    ItemType.SYSTEM_SHORTCUT -> {
                                        Box(
                                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xCC1E293B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.SettingsApplications, item.title, tint = Color(0xFFFB923C), modifier = Modifier.size(30.dp))
                                        }
                                    }
                                    ItemType.FILE_LINK -> {
                                        Box(
                                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xCC1E293B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Description, item.title, tint = Color(0xFF2DD4BF), modifier = Modifier.size(30.dp))
                                        }
                                    }
                                    ItemType.AUDIO_MEMO -> {
                                        val isPlaying = playingAudioId == item.id
                                        Box(
                                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFF43F5E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = item.title,
                                                tint = Color.White,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }
                                    ItemType.SESSION_GROUP -> {
                                        Box(
                                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC164E3F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Workspaces, item.title, tint = Color(0xFF34D399), modifier = Modifier.size(30.dp))
                                        }
                                    }
                                    else -> {}
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    style = desktopLabelStyle,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (isResizing) {
                        val minW = if (item.type == ItemType.APP_WINDOW || item.type == ItemType.WEB_APP) 220f else 80f
                        val minH = if (item.type == ItemType.APP_WINDOW || item.type == ItemType.WEB_APP) 240f else 80f
                        ResizeCornerHandle(Alignment.TopStart, { dx, dy ->
                            val curr = items[index]
                            val newW = (curr.widthDp - dx / density.density).coerceAtLeast(minW)
                            val newH = (curr.heightDp - dy / density.density).coerceAtLeast(minH)
                            items[index] = curr.copy(
                                x = curr.x + (curr.widthDp - newW) * density.density,
                                y = curr.y + (curr.heightDp - newH) * density.density,
                                widthDp = newW,
                                heightDp = newH
                            )
                        }) { StorageManager.saveItems(context, items) }

                        ResizeCornerHandle(Alignment.TopEnd, { dx, dy ->
                            val curr = items[index]
                            val newW = (curr.widthDp + dx / density.density).coerceAtLeast(minW)
                            val newH = (curr.heightDp - dy / density.density).coerceAtLeast(minH)
                            items[index] = curr.copy(
                                y = curr.y + (curr.heightDp - newH) * density.density,
                                widthDp = newW,
                                heightDp = newH
                            )
                        }) { StorageManager.saveItems(context, items) }

                        ResizeCornerHandle(Alignment.BottomStart, { dx, dy ->
                            val curr = items[index]
                            val newW = (curr.widthDp - dx / density.density).coerceAtLeast(minW)
                            val newH = (curr.heightDp + dy / density.density).coerceAtLeast(minH)
                            items[index] = curr.copy(
                                x = curr.x + (curr.widthDp - newW) * density.density,
                                widthDp = newW,
                                heightDp = newH
                            )
                        }) { StorageManager.saveItems(context, items) }

                        ResizeCornerHandle(Alignment.BottomEnd, { dx, dy ->
                            val curr = items[index]
                            items[index] = curr.copy(
                                widthDp = (curr.widthDp + dx / density.density).coerceAtLeast(minW),
                                heightDp = (curr.heightDp + dy / density.density).coerceAtLeast(minH)
                            )
                        }) { StorageManager.saveItems(context, items) }
                    }
                }
            }
        }

        // LIVELLO 3: STRUMENTO LAZO
        if (activeTool == CanvasTool.LASSO) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start -> lassoPoints.clear(); lassoPoints.add(start) },
                            onDrag = { change, _ -> change.consume(); lassoPoints.add(change.position) },
                            onDragEnd = {
                                if (lassoPoints.size > 2) {
                                    selectedIds.clear()
                                    for (obj in items) {
                                        val left = obj.x * zoomScale + cameraOffset.x
                                        val top = obj.y * zoomScale + cameraOffset.y
                                        val right = left + obj.widthDp * density.density * zoomScale
                                        val bottom = top + obj.heightDp * density.density * zoomScale
                                        val center = Offset((left + right) / 2f, (top + bottom) / 2f)
                                        if (isPointInPolygon(center, lassoPoints)) selectedIds.add(obj.id)
                                    }
                                }
                                lassoPoints.clear()
                                activeTool = if (selectedIds.isNotEmpty()) CanvasTool.SELECT else CanvasTool.PAN
                            }
                        )
                    }
            ) {
                if (lassoPoints.size > 1) {
                    val path = Path().apply {
                        moveTo(lassoPoints.first().x, lassoPoints.first().y)
                        for (i in 1 until lassoPoints.size) lineTo(lassoPoints[i].x, lassoPoints[i].y)
                        close()
                    }
                    drawPath(path, Color(0x2E38BDF8))
                    drawPath(path, Color(0xFF38BDF8), style = Stroke(width = 4f))
                }
            }
        }

        // LIVELLO 4: HUD SUPERIORE
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xB8161922)) {
                val posX = (-cameraOffset.x / zoomScale).roundToInt()
                val posY = (-cameraOffset.y / zoomScale).roundToInt()
                Text(
                    text = "X:$posX  Y:$posY  •  ${(zoomScale * 100).roundToInt()}%",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (resizingItemId != null) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF0284C7)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text("Trascina angoli", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = { resizingItemId = null },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Fine", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (selectedIds.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1E3A8A)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text("${selectedIds.size} sel.", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (selectedIds.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { groupSelectedIntoSession() },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Sessione", color = Color(0xFF6EE7B7), fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { selectedIds.clear() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, "Deseleziona", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // LIVELLO 5: BARRA STRUMENTI INFERIORE
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp, start = 12.dp, end = 12.dp)
                .border(1.dp, Color(0xFF2A2F3D), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF0151821),
            tonalElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .combinedClickable(
                                onClick = {
                                    cameraOffset = Offset.Zero
                                    zoomScale = 1f
                                },
                                onLongClick = { showWaypointsMenu = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MyLocation, "0,0 e Punti Salvati", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = showWaypointsMenu,
                        onDismissRequest = { showWaypointsMenu = false },
                        containerColor = Color(0xFF1C202B)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.AddLocationAlt, null, tint = Color(0xFF34D399)) },
                            text = {
                                val cx = (-cameraOffset.x / zoomScale).roundToInt()
                                val cy = (-cameraOffset.y / zoomScale).roundToInt()
                                Text("Salva punto corrente (X:$cx, Y:$cy)", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                            },
                            onClick = {
                                showWaypointsMenu = false
                                val cx = (-cameraOffset.x / zoomScale).roundToInt()
                                val cy = (-cameraOffset.y / zoomScale).roundToInt()
                                newWaypointName = "Punto ${savedWaypoints.size + 1} (X:$cx, Y:$cy)"
                                showSaveWaypointDialog = true
                            }
                        )
                        if (savedWaypoints.isNotEmpty()) {
                            HorizontalDivider(color = Color(0xFF2E3546))
                            savedWaypoints.forEach { wp ->
                                val wx = (-wp.offsetX / wp.zoomScale).roundToInt()
                                val wy = (-wp.offsetY / wp.zoomScale).roundToInt()
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Rounded.Place, null, tint = Color(0xFF60A5FA)) },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(wp.name, color = Color.White, fontSize = 13.sp)
                                                Text("X:$wx  Y:$wy  •  ${(wp.zoomScale * 100).roundToInt()}%", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }
                                            IconButton(
                                                onClick = {
                                                    savedWaypoints.remove(wp)
                                                    StorageManager.saveWaypoints(context, savedWaypoints)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Rounded.Close, "Rimuovi", tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        cameraOffset = Offset(wp.offsetX, wp.offsetY)
                                        zoomScale = wp.zoomScale
                                        showWaypointsMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                ToolButton(Icons.Rounded.OpenWith, "Tela", activeTool == CanvasTool.PAN) { activeTool = CanvasTool.PAN }
                ToolButton(Icons.Rounded.TouchApp, "Usa", activeTool == CanvasTool.INTERACT) { activeTool = CanvasTool.INTERACT }
                ToolButton(Icons.Rounded.NearMe, "Cursore", activeTool == CanvasTool.SELECT) { activeTool = CanvasTool.SELECT }
                ToolButton(Icons.Rounded.Gesture, "Lazo", activeTool == CanvasTool.LASSO) { activeTool = CanvasTool.LASSO }

                Box(modifier = Modifier.width(1.dp).height(26.dp).background(Color(0xFF2E3444)))

                ActionPillButton(Icons.Rounded.Apps, "App", Color(0xFF2563EB)) { showAppDrawer = true }
                ActionPillButton(Icons.Rounded.Public, "Web", Color(0xFF0D9488)) { showWebAppDialog = true }
                ActionPillButton(Icons.Rounded.NoteAdd, "Nota", Color(0xFF1E293B)) {
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = "Nuova Nota",
                            content = "Tocca per scrivere...",
                            x = (-cameraOffset.x + 190f) / zoomScale,
                            y = (-cameraOffset.y + 300f) / zoomScale,
                            widthDp = 210f,
                            heightDp = 140f,
                            type = ItemType.NOTE
                        )
                    )
                    StorageManager.saveItems(context, items)
                }
                ActionPillButton(Icons.Rounded.AddPhotoAlternate, "Foto", Color(0xFF1E293B)) { showPhotoChoiceDialog = true }
                ActionPillButton(Icons.Rounded.Mic, "Audio", Color(0xFF1E293B)) { showAudioDialog = true }
                ActionPillButton(Icons.Rounded.AttachFile, "File", Color(0xFF1E293B)) { fileLinkPickerLauncher.launch(arrayOf("*/*")) }
            }
        }

        // DIALOG SALVA COORDINATA
        if (showSaveWaypointDialog) {
            AlertDialog(
                onDismissRequest = { showSaveWaypointDialog = false },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text("Salva Posizione sulla Tela", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newWaypointName,
                        onValueChange = { newWaypointName = it },
                        label = { Text("Nome coordinata") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        savedWaypoints.add(
                            CanvasWaypoint(
                                id = UUID.randomUUID().toString(),
                                name = newWaypointName.ifBlank { "Punto salvato" },
                                offsetX = cameraOffset.x,
                                offsetY = cameraOffset.y,
                                zoomScale = zoomScale
                            )
                        )
                        StorageManager.saveWaypoints(context, savedWaypoints)
                        showSaveWaypointDialog = false
                        Toast.makeText(context, "Posizione salvata", Toast.LENGTH_SHORT).show()
                    }) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveWaypointDialog = false }) { Text("Annulla", color = Color(0xFF94A3B8)) }
                }
            )
        }

        // MENU CONTESTUALE
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            containerColor = Color(0xFF1C202B),
            offset = with(density) { DpOffset(contextMenuScreenPos.x.toDp(), contextMenuScreenPos.y.toDp()) }
        ) {
            if (targetItemForMenu != null) {
                val item = targetItemForMenu!!
                if (item.type == ItemType.APP_SHORTCUT && item.packageName != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.PictureInPictureAlt, null, tint = Color(0xFF38BDF8)) },
                        text = { Text("Apri in Finestra sulla Tela", color = Color(0xFF38BDF8)) },
                        onClick = {
                            appList.find { it.packageName == item.packageName }?.let { openAppAsCanvasWindow(it, item.x + 40f, item.y + 40f) }
                            showContextMenu = false
                        }
                    )
                }
                if (item.type != ItemType.APP_WINDOW && item.type != ItemType.WEB_APP && item.type != ItemType.SYSTEM_WIDGET) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Color.White) },
                        text = { Text("Modifica Nome / Testo", color = Color.White) },
                        onClick = {
                            editingItem = item
                            editTitleText = item.title
                            editContentText = item.content
                            editColorHex = item.colorHex
                            showContextMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.AspectRatio, null, tint = Color(0xFF38BDF8)) },
                    text = { Text("Ridimensiona", color = Color(0xFF38BDF8)) },
                    onClick = { resizingItemId = item.id; showContextMenu = false }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFF87171)) },
                    text = { Text("Elimina", color = Color(0xFFF87171)) },
                    onClick = {
                        cleanupItemResources(item, appWidgetHost)
                        items.remove(item)
                        selectedIds.remove(item.id)
                        if (resizingItemId == item.id) resizingItemId = null
                        StorageManager.saveItems(context, items)
                        showContextMenu = false
                    }
                )
            } else {
                val worldX = (contextMenuScreenPos.x - cameraOffset.x) / zoomScale
                val worldY = (contextMenuScreenPos.y - cameraOffset.y) / zoomScale
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Public, null, tint = Color(0xFF34D399)) },
                    text = { Text("Aggiungi Web App / Sito", color = Color.White) },
                    onClick = { showContextMenu = false; showWebAppDialog = true }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Widgets, null, tint = Color(0xFFF472B6)) },
                    text = { Text("Aggiungi Widget di Sistema", color = Color.White) },
                    onClick = { showContextMenu = false; onRequestPickSystemWidget() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Bolt, null, tint = Color(0xFFFB923C)) },
                    text = { Text("Aggiungi Scorciatoia di Sistema", color = Color.White) },
                    onClick = { showContextMenu = false; onRequestPickSystemShortcut() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.NoteAdd, null, tint = Color(0xFFFBBF24)) },
                    text = { Text("Nuova Nota", color = Color.White) },
                    onClick = {
                        items.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = "Nuova Nota",
                                content = "Tocca per scrivere...",
                                x = worldX,
                                y = worldY,
                                widthDp = 210f,
                                heightDp = 140f,
                                type = ItemType.NOTE
                            )
                        )
                        StorageManager.saveItems(context, items)
                        showContextMenu = false
                    }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Wallpaper, null, tint = Color(0xFF38BDF8)) },
                    text = { Text("Cambia Sfondo", color = Color.White) },
                    onClick = { showContextMenu = false; openSystemWallpaperChooser() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = Color(0xFF94A3B8)) },
                    text = { Text("Impostazioni Launcher", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        liveShizukuReady = VirtualDisplayWindowManager.isShizukuReady()
                        showSettingsDialog = true
                    }
                )
            }
        }

        if (showWebAppDialog) {
            WebAppLauncherDialog(
                quickPresets = webQuickPresets,
                installedBrowsers = installedBrowsers,
                onSaveQuickPreset = { presetName, presetUrl, browserPkg ->
                    val normalizedUrl = normalizeWebUrl(presetUrl)
                    val label = presetName.ifBlank { normalizedUrl.removePrefix("https://").removePrefix("http://").take(20) }
                    webQuickPresets.add(WebQuickPreset(UUID.randomUUID().toString(), label, normalizedUrl, browserPkg))
                    StorageManager.saveWebQuickPresets(context, webQuickPresets)
                },
                onDeleteQuickPreset = { preset ->
                    webQuickPresets.remove(preset)
                    StorageManager.saveWebQuickPresets(context, webQuickPresets)
                },
                onAddWebApp = { title, rawUrl, browserPkg, adBlockOn ->
                    openWebAppOnCanvas(title, rawUrl, browserPkg, adBlockOn)
                    showWebAppDialog = false
                },
                onDismiss = { showWebAppDialog = false }
            )
        }

        if (showPhotoChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoChoiceDialog = false },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text("Aggiungi Immagine", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showPhotoChoiceDialog = false; launchQuickCamera() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scatta foto al volo")
                        }
                        OutlinedButton(
                            onClick = { showPhotoChoiceDialog = false; galleryPickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scegli dalla Galleria")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhotoChoiceDialog = false }) { Text("Annulla", color = Color(0xFF94A3B8)) }
                }
            )
        }

        if (showAudioDialog) {
            AudioRecorderDialog(
                onAudioSaved = { filePath, label ->
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = label,
                            audioPath = filePath,
                            x = (-cameraOffset.x + 180f) / zoomScale,
                            y = (-cameraOffset.y + 300f) / zoomScale,
                            widthDp = 115f,
                            heightDp = 105f,
                            type = ItemType.AUDIO_MEMO
                        )
                    )
                    StorageManager.saveItems(context, items)
                    showAudioDialog = false
                },
                onPickFromStorage = { showAudioDialog = false; audioFilePickerLauncher.launch("audio/*") },
                onDismiss = { showAudioDialog = false }
            )
        }

        if (editingItem != null) {
            AlertDialog(
                onDismissRequest = { editingItem = null },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text("Modifica Elemento", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editTitleText,
                            onValueChange = { editTitleText = it },
                            label = { Text("Nome") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        if (editingItem?.type == ItemType.NOTE) {
                            OutlinedTextField(
                                value = editContentText,
                                onValueChange = { editContentText = it },
                                label = { Text("Testo Nota") },
                                minLines = 4,
                                maxLines = 8,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ElementColorOptions.forEach { (hex, _) ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (hex == 0x00000000L) Color(0xFF0F172A) else Color(hex))
                                            .border(
                                                width = if (editColorHex == hex) 2.5.dp else 1.dp,
                                                color = if (editColorHex == hex) Color.White else Color(0xFF475569),
                                                shape = CircleShape
                                            )
                                            .clickable { editColorHex = hex }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val idx = items.indexOfFirst { it.id == editingItem?.id }
                        if (idx >= 0) {
                            items[idx] = items[idx].copy(title = editTitleText, content = editContentText, colorHex = editColorHex)
                            StorageManager.saveItems(context, items)
                        }
                        editingItem = null
                    }) { Text("Salva") }
                },
                dismissButton = {
                    TextButton(onClick = { editingItem = null }) { Text("Annulla", color = Color(0xFF94A3B8)) }
                }
            )
        }

        if (activeSessionDialogItem != null) {
            val session = activeSessionDialogItem!!
            AlertDialog(
                onDismissRequest = { activeSessionDialogItem = null },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text(session.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        session.sessionPackages.forEach { pkg ->
                            val label = appList.find { it.packageName == pkg }?.label ?: pkg
                            Button(
                                onClick = { launchPackage(context, pkg); activeSessionDialogItem = null },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Apri $label") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeSessionDialogItem = null }) { Text("Chiudi", color = Color(0xFF94A3B8)) }
                }
            )
        }

        if (showSettingsDialog) {
            LauncherSettingsDialog(
                shizukuReady = liveShizukuReady,
                onRequestShizukuPermission = {
                    onRequestShizukuPermission()
                    liveShizukuReady = VirtualDisplayWindowManager.isShizukuReady()
                },
                backgroundAlpha = backgroundAlpha,
                onBackgroundAlphaChange = { backgroundAlpha = it; StorageManager.setBackgroundAlpha(context, it) },
                drawerAlpha = drawerAlpha,
                onDrawerAlphaChange = { drawerAlpha = it; StorageManager.setDrawerAlpha(context, it) },
                hapticStrength = hapticStrength,
                onHapticStrengthChange = { hapticStrength = it; StorageManager.setDrawerHapticStrength(context, it) },
                statusBarMode = statusBarMode,
                onStatusBarModeChange = { statusBarMode = it; StorageManager.setStatusBarMode(context, it); onStatusBarModeChanged(it) },
                showSelfInDrawer = showSelfInDrawer,
                onShowSelfInDrawerChange = { showSelfInDrawer = it; StorageManager.setShowSelfInDrawer(context, it); onShowSelfInDrawerChanged(it) },
                onAddSystemShortcut = { preset ->
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = preset.title,
                            shortcutAction = preset.action,
                            x = (-cameraOffset.x + 190f) / zoomScale,
                            y = (-cameraOffset.y + 300f) / zoomScale,
                            widthDp = 105f,
                            heightDp = 100f,
                            type = ItemType.SYSTEM_SHORTCUT
                        )
                    )
                    StorageManager.saveItems(context, items)
                    Toast.makeText(context, "${preset.title} aggiunta", Toast.LENGTH_SHORT).show()
                },
                onChangeWallpaper = { openSystemWallpaperChooser() },
                onAddSystemWidget = { onRequestPickSystemWidget() },
                onRestoreGuideNote = {
                    items.add(StorageManager.createDefaultGuideNote((-cameraOffset.x + 110f) / zoomScale, (-cameraOffset.y + 190f) / zoomScale))
                    StorageManager.saveItems(context, items)
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showAppDrawer) {
            AppDrawerOverlay(
                appList = appList,
                showSelfInDrawer = showSelfInDrawer,
                drawerAlpha = drawerAlpha,
                hapticStrength = hapticStrength,
                onLaunchApp = { app -> showAppDrawer = false; launchApp(context, app) },
                onOpenAppInCanvasWindow = { app -> showAppDrawer = false; openAppAsCanvasWindow(app) },
                onAddAppsToCanvas = { selectedApps ->
                    val baseWorldX = (-cameraOffset.x + 120f) / zoomScale
                    val baseWorldY = (-cameraOffset.y + 240f) / zoomScale
                    selectedApps.forEachIndexed { idx, app ->
                        items.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = app.label,
                                packageName = app.packageName,
                                x = baseWorldX + (idx % 3) * 310f,
                                y = baseWorldY + (idx / 3) * 310f,
                                widthDp = 105f,
                                heightDp = 105f,
                                type = ItemType.APP_SHORTCUT
                            )
                        )
                    }
                    StorageManager.saveItems(context, items)
                    showAppDrawer = false
                },
                onDismiss = { showAppDrawer = false }
            )
        }
    }
}

private fun normalizeWebUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
    }
}

private fun queryInstalledBrowsers(context: Context): List<InstalledBrowserInfo> = runCatching {
    val pm = context.packageManager
    val resolved = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")), PackageManager.MATCH_ALL)
    val seen = mutableSetOf<String>()
    resolved.mapNotNull { info ->
        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
        if (pkg == context.packageName || !seen.add(pkg)) return@mapNotNull null
        InstalledBrowserInfo(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm))
    }.sortedBy { it.label.lowercase() }
}.getOrDefault(emptyList())

private fun cleanupItemResources(item: CanvasItem, appWidgetHost: AppWidgetHost) {
    if (item.appWidgetId != -1) runCatching { appWidgetHost.deleteAppWidgetId(item.appWidgetId) }
    if (item.type == ItemType.APP_WINDOW || (item.type == ItemType.WEB_APP && !item.browserPackage.isNullOrBlank())) {
        VirtualDisplayWindowManager.closeSession(item.id)
    }
    if (item.type == ItemType.WEB_APP && item.browserPackage.isNullOrBlank()) {
        VirtualDisplayWindowManager.activeWebViews.remove(item.id)?.destroy()
    }
}

private fun toggleItemSelection(selectedIds: MutableList<String>, id: String) {
    if (selectedIds.contains(id)) selectedIds.remove(id)
    else {
        selectedIds.clear()
        selectedIds.add(id)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    initialUrl: String,
    isAdBlockEnabled: () -> Boolean,
    onAdBlocked: () -> Unit,
    onUrlOrTitleUpdated: (String, String) -> Unit
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, false) }
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = false
        cacheMode = WebSettings.LOAD_DEFAULT
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val reqUrl = request?.url?.toString()
            if (reqUrl != null && isAdBlockEnabled() && AdBlockerEngine.isAdOrTrackerUrl(reqUrl)) {
                onAdBlocked()
                return AdBlockerEngine.createEmptyBlockedResponse()
            }
            return super.shouldInterceptRequest(view, request)
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (isAdBlockEnabled()) view?.evaluateJavascript(AdBlockerEngine.COSMETIC_AD_HIDE_JS, null)
            if (url != null) onUrlOrTitleUpdated(url, view?.title ?: "")
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            val currUrl = view?.url
            if (!currUrl.isNullOrBlank() && !title.isNullOrBlank()) onUrlOrTitleUpdated(currUrl, title)
        }
    }
    loadUrl(initialUrl, mapOf("DNT" to "1", "Sec-GPC" to "1"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebAppLauncherDialog(
    quickPresets: List<WebQuickPreset>,
    installedBrowsers: List<InstalledBrowserInfo>,
    onSaveQuickPreset: (String, String, String?) -> Unit,
    onDeleteQuickPreset: (WebQuickPreset) -> Unit,
    onAddWebApp: (String, String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var selectedBrowserPkg by remember { mutableStateOf<String?>(null) }
    var adBlockEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181B24),
        titleContentColor = Color.White,
        title = { Text("Apri Web App sulla Tela", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL o ricerca web") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome finestra (opzionale)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { if (url.isNotBlank() && url != "https://") onSaveQuickPreset(title, url, selectedBrowserPkg) },
                    enabled = url.isNotBlank() && url != "https://",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salva questo sito nei rapidi", fontSize = 12.sp)
                }

                Text("Apri con:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedBrowserPkg == null,
                        onClick = { selectedBrowserPkg = null },
                        leadingIcon = { Icon(Icons.Rounded.Shield, null, tint = Color(0xFFFB923C), modifier = Modifier.size(16.dp)) },
                        label = { Text("Launcher + AdBlock", fontSize = 12.sp) }
                    )
                    installedBrowsers.forEach { browser ->
                        val iconBmp = remember(browser.packageName) { browser.icon.toBitmap(48, 48).asImageBitmap() }
                        FilterChip(
                            selected = selectedBrowserPkg == browser.packageName,
                            onClick = { selectedBrowserPkg = browser.packageName },
                            leadingIcon = { Image(bitmap = iconBmp, contentDescription = browser.label, modifier = Modifier.size(16.dp)) },
                            label = { Text(browser.label, fontSize = 12.sp) }
                        )
                    }
                }

                if (selectedBrowserPkg == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scudo AdBlock & Anti-Tracker", color = Color.White, fontSize = 12.sp)
                        Switch(checked = adBlockEnabled, onCheckedChange = { adBlockEnabled = it })
                    }
                }

                if (quickPresets.isNotEmpty()) {
                    Text("Opzioni rapide:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickPresets.forEach { preset ->
                            InputChip(
                                selected = false,
                                onClick = {
                                    onAddWebApp(preset.name, preset.url, preset.browserPackage ?: selectedBrowserPkg, adBlockEnabled)
                                },
                                label = { Text(preset.name, color = Color.White, fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        "Rimuovi",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(15.dp).clickable { onDeleteQuickPreset(preset) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank() && url != "https://") onAddWebApp(title, url.trim(), selectedBrowserPkg, adBlockEnabled)
            }) { Text("Apri sulla Tela") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = Color(0xFF94A3B8)) }
        }
    )
}

@Composable
private fun BoxScope.ResizeCornerHandle(
    alignment: Alignment,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .size(28.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun AudioRecorderDialog(
    onAudioSaved: (String, String) -> Unit,
    onPickFromStorage: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFilePath by remember { mutableStateOf<String?>(null) }
    var memoLabel by remember { mutableStateOf("Memo Vocale") }

    fun beginMic() {
        runCatching {
            val dir = File(context.filesDir, "audio_memos").apply { if (!exists()) mkdirs() }
            val file = File(dir, "memo_${UUID.randomUUID()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFilePath = file.absolutePath
            isRecording = true
        }.onFailure {
            Toast.makeText(context, "Errore avvio microfono", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginMic() else Toast.makeText(context, "Permesso microfono negato", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recorder?.release() } }
    }

    AlertDialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
        containerColor = Color(0xFF181B24),
        titleContentColor = Color.White,
        title = { Text("Audio & Memo Vocale", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = memoLabel,
                    onValueChange = { memoLabel = it },
                    label = { Text("Nome memo") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                if (isRecording) {
                    Button(
                        onClick = {
                            runCatching { recorder?.stop(); recorder?.release() }
                            recorder = null
                            isRecording = false
                            outputFilePath?.let { onAudioSaved(it, memoLabel.ifBlank { "Memo Vocale" }) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ferma e Salva Memo Vocale")
                    }
                } else {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginMic()
                            else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registra Memo Vocale")
                    }
                    OutlinedButton(onClick = onPickFromStorage, modifier = Modifier.fillMaxWidth()) {
                        Text("Scegli file audio dall'archivio")
                    }
                }
            }
        },
        confirmButton = {
            if (!isRecording) TextButton(onClick = onDismiss) { Text("Chiudi", color = Color(0xFF94A3B8)) }
        }
    )
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Color(0xFF2563EB) else Color.Transparent
    val fg = if (selected) Color.White else Color(0xFF94A3B8)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionPillButton(icon: ImageVector, label: String, containerColor: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openSystemOrCustomShortcut(context: Context, item: CanvasItem) {
    runCatching {
        if (!item.shortcutIntentUri.isNullOrEmpty()) {
            context.startActivity(Intent.parseUri(item.shortcutIntentUri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (!item.shortcutAction.isNullOrEmpty()) {
            context.startActivity(Intent(item.shortcutAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    }
    Toast.makeText(context, "Scorciatoia non avviabile", Toast.LENGTH_SHORT).show()
}

private fun openLinkedFile(context: Context, item: CanvasItem) {
    val uriStr = item.fileUri ?: return
    runCatching {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriStr), item.fileMimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(openIntent, "Apri file con"))
    }.onFailure {
        Toast.makeText(context, "Nessuna app trovata per questo file", Toast.LENGTH_SHORT).show()
    }
}

private fun launchApp(context: Context, app: AppInfo) {
    runCatching {
        if (app.componentName != null && app.userHandle != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startMainActivity(app.componentName, app.userHandle, null, null)
            return
        }
    }
    launchPackage(context, app.packageName)
}

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) context.startActivity(intent)
    else Toast.makeText(context, "App non avviabile direttamente", Toast.LENGTH_SHORT).show()
}

private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val denom = (yj - yi).let { if (it == 0f) 0.0001f else it }
        if (((yi > point.y) != (yj > point.y)) && (point.x < (xj - xi) * (point.y - yi) / denom + xi)) {
            inside = !inside
        }
        j = i
    }
    return inside
}
