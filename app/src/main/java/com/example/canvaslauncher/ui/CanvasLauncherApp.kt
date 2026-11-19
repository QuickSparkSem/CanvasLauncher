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
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AttachFile
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
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shortcut
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
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
import com.example.canvaslauncher.data.StatusBarMode
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import com.example.canvaslauncher.shizuku.VirtualDisplayWindowManager
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class CanvasTool { PAN, SELECT, LASSO }

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

    var showAppDrawer by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPhotoChoiceDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showWebAppDialog by remember { mutableStateOf(false) }

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
        }
    }

    var backgroundAlpha by remember { mutableFloatStateOf(StorageManager.getBackgroundAlpha(context)) }
    var drawerAlpha by remember { mutableFloatStateOf(StorageManager.getDrawerAlpha(context)) }
    var statusBarMode by remember { mutableStateOf(StorageManager.getStatusBarMode(context)) }
    var showSelfInDrawer by remember { mutableStateOf(StorageManager.getShowSelfInDrawer(context)) }
    var defaultElementColor by remember { mutableLongStateOf(StorageManager.getDefaultElementColor(context)) }

    var activeTool by remember { mutableStateOf(CanvasTool.PAN) }

    // Stato Telecamera 2D e dimensioni viewport
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var viewportWidthPx by remember { mutableFloatStateOf(1080f) }
    var viewportHeightPx by remember { mutableFloatStateOf(2200f) }

    var cursorPos by remember { mutableStateOf(Offset(360f, 560f)) }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    val selectedIds = remember { mutableStateListOf<String>() }
    var isDraggingSelected by remember { mutableStateOf(false) }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuScreenPos by remember { mutableStateOf(Offset.Zero) }
    var targetItemForMenu by remember { mutableStateOf<CanvasItem?>(null) }

    var editingItem by remember { mutableStateOf<CanvasItem?>(null) }
    var editTitleText by remember { mutableStateOf("") }
    var editContentText by remember { mutableStateOf("") }
    var editColorHex by remember { mutableLongStateOf(defaultElementColor) }

    var activeSessionDialogItem by remember { mutableStateOf<CanvasItem?>(null) }

    // Riproduzione Audio
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    val activePlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activePlayer.value?.release()
            } catch (_: Exception) {
            }
        }
    }

    fun toggleAudioPlayback(item: CanvasItem) {
        val path = item.audioPath ?: return
        if (playingAudioId == item.id) {
            try {
                activePlayer.value?.stop()
                activePlayer.value?.release()
            } catch (_: Exception) {
            }
            activePlayer.value = null
            playingAudioId = null
        } else {
            try {
                activePlayer.value?.release()
                val player = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    start()
                    setOnCompletionListener {
                        playingAudioId = null
                        release()
                        activePlayer.value = null
                    }
                }
                activePlayer.value = player
                playingAudioId = item.id
            } catch (_: Exception) {
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
            onFallbackFreeformLaunched = {
                // In ripiego Freeform l'app si apre in finestra di sistema senza lasciare riquadri vuoti
            }
        )
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, uri, "snapshots", "jpg")
            if (savedPath != null) {
                val worldX = (-cameraOffset.x + 180f) / zoomScale
                val worldY = (-cameraOffset.y + 300f) / zoomScale
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Foto",
                        imagePath = savedPath,
                        x = worldX,
                        y = worldY,
                        widthDp = 190f,
                        heightDp = 175f,
                        colorHex = 0x00000000L,
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
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, uri, "snapshots", "jpg")
            if (savedPath != null) {
                val worldX = (-cameraOffset.x + 180f) / zoomScale
                val worldY = (-cameraOffset.y + 300f) / zoomScale
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Scatto",
                        imagePath = savedPath,
                        x = worldX,
                        y = worldY,
                        widthDp = 190f,
                        heightDp = 175f,
                        colorHex = 0x00000000L,
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
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
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
        if (uri != null) {
            val name = StorageManager.getDisplayNameFromUri(context, uri)
            val savedPath = StorageManager.copyUriToInternalStorage(context, uri, "audio_memos", "m4a")
            if (savedPath != null) {
                val worldX = (-cameraOffset.x + 180f) / zoomScale
                val worldY = (-cameraOffset.y + 300f) / zoomScale
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = name,
                        audioPath = savedPath,
                        x = worldX,
                        y = worldY,
                        widthDp = 115f,
                        heightDp = 105f,
                        colorHex = 0x00000000L,
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
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val displayName = StorageManager.getDisplayNameFromUri(context, uri)
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val worldX = (-cameraOffset.x + 180f) / zoomScale
            val worldY = (-cameraOffset.y + 300f) / zoomScale
            items.add(
                CanvasItem(
                    id = UUID.randomUUID().toString(),
                    title = displayName,
                    fileUri = uri.toString(),
                    fileMimeType = mimeType,
                    x = worldX,
                    y = worldY,
                    widthDp = 110f,
                    heightDp = 105f,
                    colorHex = 0x00000000L,
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
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = trimmed.removePrefix("https://").removePrefix("http://").take(26),
                        url = trimmed,
                        x = worldX,
                        y = worldY,
                        widthDp = 310f,
                        heightDp = 420f,
                        colorHex = 0xFF141822L,
                        type = ItemType.WEB_APP
                    )
                )
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
                        colorHex = 0x00000000L,
                        type = ItemType.NOTE
                    )
                )
            }
            added = true
        }
        if (incomingImageUri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, incomingImageUri, "snapshots", "jpg")
            if (savedPath != null) {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Foto",
                        imagePath = savedPath,
                        x = worldX + 35f,
                        y = worldY + 35f,
                        widthDp = 190f,
                        heightDp = 175f,
                        colorHex = 0x00000000L,
                        type = ItemType.IMAGE_SNAPSHOT
                    )
                )
                added = true
            }
        }
        if (incomingAudioUri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, incomingAudioUri, "audio_memos", "m4a")
            if (savedPath != null) {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Audio",
                        audioPath = savedPath,
                        x = worldX + 45f,
                        y = worldY + 45f,
                        widthDp = 115f,
                        heightDp = 105f,
                        colorHex = 0x00000000L,
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

    val currentZoom by rememberUpdatedState(zoomScale)
    val currentCamera by rememberUpdatedState(cameraOffset)

    fun openSystemWallpaperChooser() {
        try {
            val wallpaperIntent = Intent(Intent.ACTION_SET_WALLPAPER)
            context.startActivity(Intent.createChooser(wallpaperIntent, "Scegli sfondo"))
        } catch (_: Exception) {
            Toast.makeText(context, "Selettore sfondi non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    fun groupSelectedIntoSession() {
        val selectedObjects = items.filter { selectedIds.contains(it.id) }
        if (selectedObjects.size < 2) return
        val packages = selectedObjects.mapNotNull { it.packageName }.distinct()
        val summary = selectedObjects.joinToString("\n") { "• ${it.title}" }
        val avgX = selectedObjects.map { it.x }.average().toFloat()
        val avgY = selectedObjects.map { it.y }.average().toFloat()

        items.add(
            CanvasItem(
                id = UUID.randomUUID().toString(),
                title = "Sessione (${selectedObjects.size})",
                content = summary,
                x = avgX,
                y = avgY,
                widthDp = 115f,
                heightDp = 110f,
                colorHex = 0x00000000L,
                type = ItemType.SESSION_GROUP,
                sessionPackages = packages
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
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (resizingItemId != null) resizingItemId = null
                    },
                    onLongPress = { pressPos ->
                        val hit = findItemAtScreenPos(
                            screenPos = pressPos,
                            items = items,
                            cameraOffset = currentCamera,
                            zoomScale = currentZoom,
                            densityPx = density.density
                        )
                        targetItemForMenu = hit
                        contextMenuScreenPos = pressPos
                        showContextMenu = true
                    }
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val firstChange = event.changes.firstOrNull()
                        if (firstChange != null) {
                            if (event.type == PointerEventType.Move) {
                                cursorPos = firstChange.position
                            }
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                cursorPos = firstChange.position
                                contextMenuScreenPos = firstChange.position
                                targetItemForMenu = findItemAtScreenPos(
                                    screenPos = firstChange.position,
                                    items = items,
                                    cameraOffset = currentCamera,
                                    zoomScale = currentZoom,
                                    densityPx = density.density
                                )
                                showContextMenu = true
                            }
                        }
                    }
                }
            }
            .pointerInput(activeTool) {
                if (activeTool == CanvasTool.PAN) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldZoom = zoomScale
                        val newZoom = (zoomScale * zoom).coerceIn(0.35f, 2.5f)
                        val zoomFactor = newZoom / oldZoom
                        cameraOffset = centroid - (centroid - cameraOffset) * zoomFactor + pan
                        zoomScale = newZoom
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { start ->
                            cursorPos = start
                            if (activeTool == CanvasTool.LASSO) {
                                lassoPoints.clear()
                                lassoPoints.add(start)
                            } else if (activeTool == CanvasTool.SELECT) {
                                val hit = findItemAtScreenPos(
                                    screenPos = start,
                                    items = items,
                                    cameraOffset = currentCamera,
                                    zoomScale = currentZoom,
                                    densityPx = density.density
                                )
                                if (hit != null) {
                                    if (!selectedIds.contains(hit.id)) {
                                        selectedIds.clear()
                                        selectedIds.add(hit.id)
                                    }
                                    isDraggingSelected = true
                                } else {
                                    isDraggingSelected = false
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            cursorPos += dragAmount

                            if (activeTool == CanvasTool.LASSO) {
                                lassoPoints.add(cursorPos)
                            } else if (activeTool == CanvasTool.SELECT && isDraggingSelected && selectedIds.isNotEmpty()) {
                                val deltaWorldX = dragAmount.x / currentZoom
                                val deltaWorldY = dragAmount.y / currentZoom
                                for (i in items.indices) {
                                    if (selectedIds.contains(items[i].id)) {
                                        items[i] = items[i].copy(
                                            x = items[i].x + deltaWorldX,
                                            y = items[i].y + deltaWorldY
                                        )
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (activeTool == CanvasTool.LASSO && lassoPoints.size > 2) {
                                selectedIds.clear()
                                for (obj in items) {
                                    val wPx = obj.widthDp * density.density
                                    val hPx = obj.heightDp * density.density
                                    val left = obj.x * currentZoom + currentCamera.x
                                    val top = obj.y * currentZoom + currentCamera.y
                                    val right = left + wPx * currentZoom
                                    val bottom = top + hPx * currentZoom
                                    val center = Offset((left + right) / 2f, (top + bottom) / 2f)

                                    val checkPoints = listOf(
                                        center,
                                        Offset(left, top),
                                        Offset(right, top),
                                        Offset(left, bottom),
                                        Offset(right, bottom)
                                    )
                                    if (checkPoints.any { isPointInPolygon(it, lassoPoints) }) {
                                        selectedIds.add(obj.id)
                                    }
                                }
                                lassoPoints.clear()
                                if (selectedIds.isNotEmpty()) {
                                    activeTool = CanvasTool.SELECT
                                }
                            } else if (activeTool == CanvasTool.SELECT && isDraggingSelected) {
                                StorageManager.saveItems(context, items)
                                isDraggingSelected = false
                            }
                        }
                    )
                }
            }
    ) {
        // Griglia puntinata leggera
        if (backgroundAlpha > 0.08f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 84f * zoomScale
                if (step > 18f) {
                    val startX = cameraOffset.x % step
                    val startY = cameraOffset.y % step
                    var x = startX
                    while (x < size.width) {
                        var y = startY
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

        // --- TELA INFINITA 2D (WORLD SPACE) ---
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

                // Calcolo Viewport Culling (finestra visibile nello schermo o fuori campo)
                val screenLeft = item.x * zoomScale + cameraOffset.x
                val screenTop = item.y * zoomScale + cameraOffset.y
                val screenRight = screenLeft + item.widthDp * density.density * zoomScale
                val screenBottom = screenTop + item.heightDp * density.density * zoomScale
                val margin = 120f
                val isVisibleInViewport =
                    screenRight >= -margin &&
                    screenLeft <= viewportWidthPx + margin &&
                    screenBottom >= -margin &&
                    screenTop <= viewportHeightPx + margin

                val desktopLabelStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(
                        color = Color(0xDD000000),
                        offset = Offset(1f, 2f),
                        blurRadius = 4f
                    )
                )

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
                ) {
                    when (item.type) {
                        // 1. FINESTRA APP LIVE (VirtualDisplay + Shizuku + Surface Detaching + Snapshot Riavvio)
                        ItemType.APP_WINDOW -> {
                            @Suppress("UNUSED_VARIABLE")
                            val rev = windowSessionRevision
                            val isSessionLive = VirtualDisplayWindowManager.hasActiveSession(item.id)

                            LaunchedEffect(isVisibleInViewport, isSessionLive) {
                                val session = VirtualDisplayWindowManager.getSession(item.id)
                                if (session != null) {
                                    VirtualDisplayWindowManager.updateSurfaceAttachment(
                                        itemId = item.id,
                                        surface = session.attachedSurface,
                                        isVisibleOnScreen = isVisibleInViewport
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
                                // Barra titolo finestra con Indietro, IME, Ridimensiona e Chiudi
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(Color(0xFF1E2433))
                                        .padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { VirtualDisplayWindowManager.injectBackKey(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Indietro",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    )

                                    // Toggle tastiera IME (locale dentro finestra vs schermo telefono)
                                    IconButton(
                                        onClick = {
                                            val newFallback = !item.imeFallbackDisplay
                                            items[index] = item.copy(imeFallbackDisplay = newFallback)
                                            StorageManager.saveItems(context, items)
                                            val session = VirtualDisplayWindowManager.getSession(item.id)
                                            if (session != null) {
                                                VirtualDisplayWindowManager.setDisplayImePolicy(
                                                    session.displayId,
                                                    if (newFallback) VirtualDisplayWindowManager.IME_POLICY_FALLBACK_DISPLAY
                                                    else VirtualDisplayWindowManager.IME_POLICY_LOCAL
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                if (newFallback) "Tastiera sullo schermo principale"
                                                else "Tastiera dentro la mini-finestra",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Keyboard,
                                            contentDescription = "Policy Tastiera",
                                            tint = if (item.imeFallbackDisplay) Color(0xFF94A3B8) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            resizingItemId = if (isResizing) null else item.id
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = "Ridimensiona",
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
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Chiudi Finestra",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Contenuto finestra: TextureView live se VirtualDisplay attivo,
                                // altrimenti Screenshot salvato pre-riavvio con ripristino 1-tap
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
                                                        override fun onSurfaceTextureAvailable(
                                                            st: SurfaceTexture,
                                                            w: Int,
                                                            h: Int
                                                        ) {
                                                            val s = Surface(st)
                                                            VirtualDisplayWindowManager.updateSurfaceAttachment(
                                                                item.id,
                                                                s,
                                                                isVisibleInViewport
                                                            )
                                                        }

                                                        override fun onSurfaceTextureSizeChanged(
                                                            st: SurfaceTexture,
                                                            w: Int,
                                                            h: Int
                                                        ) {
                                                            VirtualDisplayWindowManager.resizeSession(item.id, w, h)
                                                        }

                                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                                            VirtualDisplayWindowManager.updateSurfaceAttachment(
                                                                item.id,
                                                                null,
                                                                false
                                                            )
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
                                                                        item.id,
                                                                        MotionEvent.ACTION_DOWN,
                                                                        touchDownTime,
                                                                        change.position.x,
                                                                        change.position.y,
                                                                        renderedW,
                                                                        renderedH
                                                                    )
                                                                }
                                                                change.pressed && change.previousPressed -> {
                                                                    VirtualDisplayWindowManager.injectMappedTouch(
                                                                        item.id,
                                                                        MotionEvent.ACTION_MOVE,
                                                                        touchDownTime,
                                                                        change.position.x,
                                                                        change.position.y,
                                                                        renderedW,
                                                                        renderedH
                                                                    )
                                                                }
                                                                !change.pressed && change.previousPressed -> {
                                                                    VirtualDisplayWindowManager.injectMappedTouch(
                                                                        item.id,
                                                                        MotionEvent.ACTION_UP,
                                                                        touchDownTime,
                                                                        change.position.x,
                                                                        change.position.y,
                                                                        renderedW,
                                                                        renderedH
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                        )
                                    } else {
                                        // Stato dopo un riavvio: mostra lo screenshot catturato prima dello spegnimento
                                        if (!item.lastSnapshotPath.isNullOrEmpty() && File(item.lastSnapshotPath).exists()) {
                                            AsyncImage(
                                                model = File(item.lastSnapshotPath),
                                                contentDescription = "Ultimo stato ${item.title}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x880B0E17)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Text(
                                                    text = "Stato salvato pre-riavvio",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
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
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF2563EB)
                                                    )
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (shizukuReady) "Riprendi su Tela" else "Apri in Freeform",
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. FINESTRA WEB APP / BROWSER LIVE (con Viewport Culling + Snapshot Riavvio)
                        ItemType.WEB_APP -> {
                            val targetUrl = item.url ?: "https://www.google.com"

                            LaunchedEffect(isVisibleInViewport) {
                                val wv = VirtualDisplayWindowManager.activeWebViews[item.id]
                                if (wv != null) {
                                    if (isVisibleInViewport) wv.onResume() else wv.onPause()
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
                                // Barra controlli WebView (Indietro, Avanti, Ricarica, Titolo, Ridimensiona, Chiudi)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .background(Color(0xFF1E2433))
                                        .padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val wv = VirtualDisplayWindowManager.activeWebViews[item.id]
                                            if (wv?.canGoBack() == true) wv.goBack()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Indietro",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val wv = VirtualDisplayWindowManager.activeWebViews[item.id]
                                            if (wv?.canGoForward() == true) wv.goForward()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = "Avanti",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            VirtualDisplayWindowManager.activeWebViews[item.id]?.reload()
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Ricarica",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            resizingItemId = if (isResizing) null else item.id
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AspectRatio,
                                            contentDescription = "Ridimensiona",
                                            tint = if (isResizing) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            VirtualDisplayWindowManager.activeWebViews.remove(item.id)?.destroy()
                                            items.removeAt(index)
                                            StorageManager.saveItems(context, items)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Chiudi Web App",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Mostra lo screenshot salvato sotto mentre la pagina ricarica dopo un riavvio
                                    if (!item.lastSnapshotPath.isNullOrEmpty() && File(item.lastSnapshotPath).exists()) {
                                        AsyncImage(
                                            model = File(item.lastSnapshotPath),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    AndroidView(
                                        factory = { ctx ->
                                            VirtualDisplayWindowManager.activeWebViews.getOrPut(item.id) {
                                                createConfiguredWebView(
                                                    context = ctx,
                                                    initialUrl = targetUrl,
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

                        // 3. WIDGET DI SISTEMA DIRETTAMENTE SULLA TELA (SENZA BOX)
                        ItemType.SYSTEM_WIDGET -> {
                            val widgetInfo = remember(item.appWidgetId) {
                                if (item.appWidgetId != -1) appWidgetManager.getAppWidgetInfo(item.appWidgetId) else null
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
                                            appWidgetHost.createView(ctx, item.appWidgetId, widgetInfo).apply {
                                                setAppWidget(item.appWidgetId, widgetInfo)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = item.title,
                                        style = desktopLabelStyle,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }
                        }

                        // 4. NOTA DIRETTAMENTE SULLA TELA (TESTO SCORREVOLE, SENZA BOX DI DEFAULT)
                        ItemType.NOTE -> {
                            val noteBg = if (item.colorHex == 0x00000000L) {
                                Color.Transparent
                            } else {
                                Color(item.colorHex).copy(alpha = 0.72f)
                            }
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
                                    style = desktopLabelStyle.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = item.content,
                                        style = desktopLabelStyle.copy(fontSize = 13.sp)
                                    )
                                }
                            }
                        }

                        // 5. FOTO / SNAPSHOT DIRETTAMENTE SULLA TELA CON NOME SOTTO (SENZA BOX)
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
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

                        // 6. ICONE DESKTOP PURE SENZA BOX (APP, SCORCIATOIE, FILE, AUDIO, SESSIONI)
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
                                                    ItemType.APP_SHORTCUT -> {
                                                        item.packageName?.let { pkg ->
                                                            val appObj = appList.find { it.packageName == pkg }
                                                            if (appObj != null) launchApp(context, appObj)
                                                            else launchPackage(context, pkg)
                                                        }
                                                    }
                                                    ItemType.SYSTEM_SHORTCUT -> openSystemOrCustomShortcut(context, item)
                                                    ItemType.FILE_LINK -> openLinkedFile(context, item)
                                                    ItemType.AUDIO_MEMO -> toggleAudioPlayback(item)
                                                    ItemType.SESSION_GROUP -> {
                                                        if (item.sessionPackages.size == 1) {
                                                            launchPackage(context, item.sessionPackages.first())
                                                        } else if (item.sessionPackages.isNotEmpty()) {
                                                            activeSessionDialogItem = item
                                                        }
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
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = item.title,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Apps,
                                                contentDescription = item.title,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(52.dp)
                                            )
                                        }
                                    }

                                    ItemType.SYSTEM_SHORTCUT -> {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xCC1E293B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SettingsApplications,
                                                contentDescription = item.title,
                                                tint = Color(0xFFFB923C),
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }

                                    ItemType.FILE_LINK -> {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xCC1E293B)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = item.title,
                                                tint = Color(0xFF2DD4BF),
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                    }

                                    ItemType.AUDIO_MEMO -> {
                                        val isPlaying = playingAudioId == item.id
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFF43F5E)),
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
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xCC164E3F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Workspaces,
                                                contentDescription = item.title,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(30.dp)
                                            )
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

                    // --- 4 MANIGLIE DI RIDIMENSIONAMENTO SUGLI ANGOLI PER QUALSIASI ELEMENTO/FINESTRA ---
                    if (isResizing) {
                        val minW = if (item.type == ItemType.APP_WINDOW || item.type == ItemType.WEB_APP) 220f else 80f
                        val minH = if (item.type == ItemType.APP_WINDOW || item.type == ItemType.WEB_APP) 240f else 80f

                        ResizeCornerHandle(
                            alignment = Alignment.TopStart,
                            onDragDelta = { dx, dy ->
                                val curr = items[index]
                                val dW = dx / density.density
                                val dH = dy / density.density
                                val newW = (curr.widthDp - dW).coerceAtLeast(minW)
                                val newH = (curr.heightDp - dH).coerceAtLeast(minH)
                                val actualDW = curr.widthDp - newW
                                val actualDH = curr.heightDp - newH
                                items[index] = curr.copy(
                                    x = curr.x + actualDW * density.density,
                                    y = curr.y + actualDH * density.density,
                                    widthDp = newW,
                                    heightDp = newH
                                )
                            },
                            onDragEnd = { StorageManager.saveItems(context, items) }
                        )

                        ResizeCornerHandle(
                            alignment = Alignment.TopEnd,
                            onDragDelta = { dx, dy ->
                                val curr = items[index]
                                val dW = dx / density.density
                                val dH = dy / density.density
                                val newW = (curr.widthDp + dW).coerceAtLeast(minW)
                                val newH = (curr.heightDp - dH).coerceAtLeast(minH)
                                val actualDH = curr.heightDp - newH
                                items[index] = curr.copy(
                                    y = curr.y + actualDH * density.density,
                                    widthDp = newW,
                                    heightDp = newH
                                )
                            },
                            onDragEnd = { StorageManager.saveItems(context, items) }
                        )

                        ResizeCornerHandle(
                            alignment = Alignment.BottomStart,
                            onDragDelta = { dx, dy ->
                                val curr = items[index]
                                val dW = dx / density.density
                                val dH = dy / density.density
                                val newW = (curr.widthDp - dW).coerceAtLeast(minW)
                                val newH = (curr.heightDp + dH).coerceAtLeast(minH)
                                val actualDW = curr.widthDp - newW
                                items[index] = curr.copy(
                                    x = curr.x + actualDW * density.density,
                                    widthDp = newW,
                                    heightDp = newH
                                )
                            },
                            onDragEnd = { StorageManager.saveItems(context, items) }
                        )

                        ResizeCornerHandle(
                            alignment = Alignment.BottomEnd,
                            onDragDelta = { dx, dy ->
                                val curr = items[index]
                                val dW = dx / density.density
                                val dH = dy / density.density
                                items[index] = curr.copy(
                                    widthDp = (curr.widthDp + dW).coerceAtLeast(minW),
                                    heightDp = (curr.heightDp + dH).coerceAtLeast(minH)
                                )
                            },
                            onDragEnd = { StorageManager.saveItems(context, items) }
                        )
                    }
                }
            }
        }

        // Tracciato Lazo
        if (lassoPoints.size > 1) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(lassoPoints.first().x, lassoPoints.first().y)
                    for (i in 1 until lassoPoints.size) {
                        lineTo(lassoPoints[i].x, lassoPoints[i].y)
                    }
                    close()
                }
                drawPath(path = path, color = Color(0x2E38BDF8))
                drawPath(path = path, color = Color(0xFF38BDF8), style = Stroke(width = 4f))
            }
        }

        // HUD superiore
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xB8161922)
            ) {
                val posX = (-cameraOffset.x / zoomScale).roundToInt()
                val posY = (-cameraOffset.y / zoomScale).roundToInt()
                val zoomPct = (zoomScale * 100).roundToInt()
                Text(
                    text = "X:$posX  Y:$posY  •  $zoomPct%",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (resizingItemId != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0284C7)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text("Trascina gli angoli", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E3A8A)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = "${selectedIds.size} sel.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedIds.size > 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { groupSelectedIntoSession() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Crea Sessione", color = Color(0xFF6EE7B7), fontSize = 12.sp)
                            }
                        }
                        IconButton(
                            onClick = { selectedIds.clear() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Deseleziona",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Cursore Visivo
        if (activeTool != CanvasTool.PAN) {
            Canvas(
                modifier = Modifier
                    .offset { IntOffset(cursorPos.x.roundToInt(), cursorPos.y.roundToInt()) }
                    .size(26.dp)
            ) {
                val pointerPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, 38f)
                    lineTo(11f, 29f)
                    lineTo(26f, 29f)
                    close()
                }
                drawPath(path = pointerPath, color = Color.White)
                drawPath(path = pointerPath, color = Color(0xFF0F172A), style = Stroke(width = 3f))
            }
        }

        // Barra strumenti inferiore
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
                ToolButton(
                    icon = Icons.Rounded.OpenWith,
                    label = "Tela",
                    selected = activeTool == CanvasTool.PAN,
                    onClick = { activeTool = CanvasTool.PAN }
                )
                ToolButton(
                    icon = Icons.Rounded.NearMe,
                    label = "Cursore",
                    selected = activeTool == CanvasTool.SELECT,
                    onClick = { activeTool = CanvasTool.SELECT }
                )
                ToolButton(
                    icon = Icons.Rounded.Gesture,
                    label = "Lazo",
                    selected = activeTool == CanvasTool.LASSO,
                    onClick = { activeTool = CanvasTool.LASSO }
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(Color(0xFF2E3444))
                )

                ActionPillButton(
                    icon = Icons.Rounded.Apps,
                    label = "App",
                    containerColor = Color(0xFF2563EB),
                    onClick = { showAppDrawer = true }
                )
                ActionPillButton(
                    icon = Icons.Rounded.Public,
                    label = "Web",
                    containerColor = Color(0xFF0D9488),
                    onClick = { showWebAppDialog = true }
                )
                ActionPillButton(
                    icon = Icons.Rounded.NoteAdd,
                    label = "Nota",
                    containerColor = Color(0xFF1E293B),
                    onClick = {
                        val worldX = (-cameraOffset.x + 190f) / zoomScale
                        val worldY = (-cameraOffset.y + 300f) / zoomScale
                        items.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = "Nuova Nota",
                                content = "Tocca per scrivere...",
                                x = worldX,
                                y = worldY,
                                widthDp = 210f,
                                heightDp = 140f,
                                colorHex = 0x00000000L,
                                type = ItemType.NOTE
                            )
                        )
                        StorageManager.saveItems(context, items)
                    }
                )
                ActionPillButton(
                    icon = Icons.Rounded.AddPhotoAlternate,
                    label = "Foto",
                    containerColor = Color(0xFF1E293B),
                    onClick = { showPhotoChoiceDialog = true }
                )
                ActionPillButton(
                    icon = Icons.Rounded.Mic,
                    label = "Audio",
                    containerColor = Color(0xFF1E293B),
                    onClick = { showAudioDialog = true }
                )
                ActionPillButton(
                    icon = Icons.Rounded.AttachFile,
                    label = "File",
                    containerColor = Color(0xFF1E293B),
                    onClick = { fileLinkPickerLauncher.launch(arrayOf("*/*")) }
                )
                IconButton(
                    onClick = {
                        cameraOffset = Offset.Zero
                        zoomScale = 1f
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Centra Tela",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- MENU CONTESTUALE SEPARATO: ELEMENTO vs SFONDO VUOTO ---
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            containerColor = Color(0xFF1C202B),
            offset = with(density) {
                DpOffset(contextMenuScreenPos.x.toDp(), contextMenuScreenPos.y.toDp())
            }
        ) {
            if (targetItemForMenu != null) {
                val item = targetItemForMenu!!

                if (item.type == ItemType.APP_SHORTCUT && item.packageName != null) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.PictureInPictureAlt, null, tint = Color(0xFF38BDF8)) },
                        text = { Text("Apri in Finestra sulla Tela", color = Color(0xFF38BDF8)) },
                        onClick = {
                            val appObj = appList.find { it.packageName == item.packageName }
                            if (appObj != null) {
                                openAppAsCanvasWindow(appObj, item.x + 40f, item.y + 40f)
                            }
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
                    text = { Text("Ridimensiona (Trascina Angoli)", color = Color(0xFF38BDF8)) },
                    onClick = {
                        resizingItemId = item.id
                        showContextMenu = false
                    }
                )

                if (selectedIds.size > 1) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Workspaces, null, tint = Color(0xFF34D399)) },
                        text = { Text("Cattura (${selectedIds.size}) in Sessione", color = Color(0xFF34D399)) },
                        onClick = {
                            groupSelectedIntoSession()
                            showContextMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFF87171)) },
                    text = { Text("Elimina", color = Color(0xFFF87171)) },
                    onClick = {
                        if (selectedIds.contains(item.id) && selectedIds.size > 1) {
                            items.filter { selectedIds.contains(it.id) }
                                .forEach { cleanupItemResources(it, appWidgetHost) }
                            items.removeAll { selectedIds.contains(it.id) }
                            selectedIds.clear()
                        } else {
                            cleanupItemResources(item, appWidgetHost)
                            items.remove(item)
                            selectedIds.remove(item.id)
                        }
                        if (resizingItemId == item.id) resizingItemId = null
                        StorageManager.saveItems(context, items)
                        showContextMenu = false
                    }
                )
            } else {
                // MENU DEDICATO ALLO SFONDO VUOTO
                val worldX = (contextMenuScreenPos.x - cameraOffset.x) / zoomScale
                val worldY = (contextMenuScreenPos.y - cameraOffset.y) / zoomScale

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Public, null, tint = Color(0xFF34D399)) },
                    text = { Text("Aggiungi Web App / Sito", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        showWebAppDialog = true
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Widgets, null, tint = Color(0xFFF472B6)) },
                    text = { Text("Aggiungi Widget di Sistema", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onRequestPickSystemWidget()
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Shortcut, null, tint = Color(0xFFFB923C)) },
                    text = { Text("Aggiungi Scorciatoia di Sistema", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        onRequestPickSystemShortcut()
                    }
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
                                colorHex = 0x00000000L,
                                type = ItemType.NOTE
                            )
                        )
                        StorageManager.saveItems(context, items)
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color(0xFFA78BFA)) },
                    text = { Text("Aggiungi Foto / Scatta", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        showPhotoChoiceDialog = true
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Mic, null, tint = Color(0xFFF43F5E)) },
                    text = { Text("Aggiungi Audio / Memo Vocale", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        showAudioDialog = true
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.AttachFile, null, tint = Color(0xFF2DD4BF)) },
                    text = { Text("Collega File dall'Archivio", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        fileLinkPickerLauncher.launch(arrayOf("*/*"))
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Wallpaper, null, tint = Color(0xFF38BDF8)) },
                    text = { Text("Cambia Sfondo", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        openSystemWallpaperChooser()
                    }
                )

                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = Color(0xFF94A3B8)) },
                    text = { Text("Impostazioni Launcher", color = Color.White) },
                    onClick = {
                        showContextMenu = false
                        showSettingsDialog = true
                    }
                )
            }
        }

        // --- DIALOG AGGIUNGI WEB APP ---
        if (showWebAppDialog) {
            WebAppLauncherDialog(
                onAddWebApp = { title, rawUrl ->
                    val normalizedUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                        rawUrl
                    } else if (rawUrl.contains(".") && !rawUrl.contains(" ")) {
                        "https://$rawUrl"
                    } else {
                        "https://www.google.com/search?q=${Uri.encode(rawUrl)}"
                    }
                    val worldX = (-cameraOffset.x + 80f) / zoomScale
                    val worldY = (-cameraOffset.y + 180f) / zoomScale
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = title.ifBlank { normalizedUrl.removePrefix("https://").take(22) },
                            url = normalizedUrl,
                            x = worldX,
                            y = worldY,
                            widthDp = 315f,
                            heightDp = 430f,
                            colorHex = 0xFF141822L,
                            type = ItemType.WEB_APP
                        )
                    )
                    StorageManager.saveItems(context, items)
                    showWebAppDialog = false
                },
                onDismiss = { showWebAppDialog = false }
            )
        }

        // --- SCELTA FOTO ---
        if (showPhotoChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoChoiceDialog = false },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text("Aggiungi Immagine", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                showPhotoChoiceDialog = false
                                launchQuickCamera()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scatta foto al volo")
                        }
                        OutlinedButton(
                            onClick = {
                                showPhotoChoiceDialog = false
                                galleryPickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scegli dalla Galleria")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhotoChoiceDialog = false }) {
                        Text("Annulla", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        // --- DIALOG AUDIO ---
        if (showAudioDialog) {
            AudioRecorderDialog(
                onAudioSaved = { filePath, label ->
                    val worldX = (-cameraOffset.x + 180f) / zoomScale
                    val worldY = (-cameraOffset.y + 300f) / zoomScale
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = label,
                            audioPath = filePath,
                            x = worldX,
                            y = worldY,
                            widthDp = 115f,
                            heightDp = 105f,
                            colorHex = 0x00000000L,
                            type = ItemType.AUDIO_MEMO
                        )
                    )
                    StorageManager.saveItems(context, items)
                    showAudioDialog = false
                },
                onPickFromStorage = {
                    showAudioDialog = false
                    audioFilePickerLauncher.launch("audio/*")
                },
                onDismiss = { showAudioDialog = false }
            )
        }

        // --- FINESTRA MODIFICA ---
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (editingItem?.type == ItemType.NOTE) {
                            OutlinedTextField(
                                value = editContentText,
                                onValueChange = { editContentText = it },
                                label = { Text("Testo Nota") },
                                minLines = 4,
                                maxLines = 8,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Text("Sfondo nota (opzionale)", color = Color(0xFF94A3B8), fontSize = 12.sp)
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
                            items[idx] = items[idx].copy(
                                title = editTitleText,
                                content = editContentText,
                                colorHex = editColorHex
                            )
                            StorageManager.saveItems(context, items)
                        }
                        editingItem = null
                    }) {
                        Text("Salva")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingItem = null }) {
                        Text("Annulla", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        // --- DIALOG SESSIONE ---
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
                                onClick = {
                                    launchPackage(context, pkg)
                                    activeSessionDialogItem = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Apri $label")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeSessionDialogItem = null }) {
                        Text("Chiudi", color = Color(0xFF94A3B8))
                    }
                }
            )
        }

        // --- IMPOSTAZIONI LAUNCHER ---
        if (showSettingsDialog) {
            LauncherSettingsDialog(
                shizukuReady = shizukuReady,
                onRequestShizukuPermission = onRequestShizukuPermission,
                backgroundAlpha = backgroundAlpha,
                onBackgroundAlphaChange = { newAlpha ->
                    backgroundAlpha = newAlpha
                    StorageManager.setBackgroundAlpha(context, newAlpha)
                },
                drawerAlpha = drawerAlpha,
                onDrawerAlphaChange = { newDrawerAlpha ->
                    drawerAlpha = newDrawerAlpha
                    StorageManager.setDrawerAlpha(context, newDrawerAlpha)
                },
                statusBarMode = statusBarMode,
                onStatusBarModeChange = { newMode ->
                    statusBarMode = newMode
                    StorageManager.setStatusBarMode(context, newMode)
                    onStatusBarModeChanged(newMode)
                },
                showSelfInDrawer = showSelfInDrawer,
                onShowSelfInDrawerChange = { newShow ->
                    showSelfInDrawer = newShow
                    StorageManager.setShowSelfInDrawer(context, newShow)
                    onShowSelfInDrawerChanged(newShow)
                },
                onAddSystemShortcut = { preset ->
                    val worldX = (-cameraOffset.x + 190f) / zoomScale
                    val worldY = (-cameraOffset.y + 300f) / zoomScale
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = preset.title,
                            shortcutAction = preset.action,
                            x = worldX,
                            y = worldY,
                            widthDp = 105f,
                            heightDp = 100f,
                            colorHex = 0x00000000L,
                            type = ItemType.SYSTEM_SHORTCUT
                        )
                    )
                    StorageManager.saveItems(context, items)
                    Toast.makeText(context, "${preset.title} aggiunta", Toast.LENGTH_SHORT).show()
                },
                onChangeWallpaper = { openSystemWallpaperChooser() },
                onAddSystemWidget = { onRequestPickSystemWidget() },
                onRestoreGuideNote = {
                    val worldX = (-cameraOffset.x + 110f) / zoomScale
                    val worldY = (-cameraOffset.y + 190f) / zoomScale
                    items.add(StorageManager.createDefaultGuideNote(worldX, worldY))
                    StorageManager.saveItems(context, items)
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // --- APP DRAWER OVERLAY ---
        if (showAppDrawer) {
            AppDrawerOverlay(
                appList = appList,
                drawerAlpha = drawerAlpha,
                onLaunchApp = { app ->
                    showAppDrawer = false
                    launchApp(context, app)
                },
                onOpenAppInCanvasWindow = { app ->
                    showAppDrawer = false
                    openAppAsCanvasWindow(app)
                },
                onAddAppsToCanvas = { selectedApps ->
                    val baseWorldX = (-cameraOffset.x + 120f) / zoomScale
                    val baseWorldY = (-cameraOffset.y + 240f) / zoomScale
                    selectedApps.forEachIndexed { idx, app ->
                        val col = idx % 3
                        val row = idx / 3
                        items.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = app.label,
                                packageName = app.packageName,
                                x = baseWorldX + col * 310f,
                                y = baseWorldY + row * 310f,
                                widthDp = 105f,
                                heightDp = 105f,
                                type = ItemType.APP_SHORTCUT,
                                colorHex = 0x00000000L
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

private fun cleanupItemResources(item: CanvasItem, appWidgetHost: AppWidgetHost) {
    if (item.appWidgetId != -1) {
        try {
            appWidgetHost.deleteAppWidgetId(item.appWidgetId)
        } catch (_: Exception) {
        }
    }
    if (item.type == ItemType.APP_WINDOW) {
        VirtualDisplayWindowManager.closeSession(item.id)
    }
    if (item.type == ItemType.WEB_APP) {
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
    onUrlOrTitleUpdated: (String, String) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) {
                    onUrlOrTitleUpdated(url, view?.title ?: "")
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val currUrl = view?.url
                if (!currUrl.isNullOrBlank() && !title.isNullOrBlank()) {
                    onUrlOrTitleUpdated(currUrl, title)
                }
            }
        }
        loadUrl(initialUrl)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebAppLauncherDialog(
    onAddWebApp: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }

    val quickSites = listOf(
        "YouTube" to "https://m.youtube.com",
        "ChatGPT" to "https://chatgpt.com",
        "Google Docs" to "https://docs.google.com",
        "Wikipedia" to "https://it.m.wikipedia.org",
        "Google" to "https://www.google.com"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181B24),
        titleContentColor = Color.White,
        title = { Text("Apri Web App sulla Tela", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL o ricerca web") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome finestra (opzionale)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Text("Web App rapide:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSites.forEach { (presetName, presetUrl) ->
                        AssistChip(
                            onClick = { onAddWebApp(presetName, presetUrl) },
                            label = { Text(presetName, color = Color.White, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank() && url != "https://") {
                        onAddWebApp(title, url.trim())
                    }
                }
            ) {
                Text("Apri sulla Tela")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = Color(0xFF94A3B8))
            }
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
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, dragAmount ->
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

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val dir = File(context.filesDir, "audio_memos")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "memo_${UUID.randomUUID()}.m4a")
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
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
            } catch (_: Exception) {
                Toast.makeText(context, "Errore avvio microfono", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permesso microfono negato", Toast.LENGTH_SHORT).show()
        }
    }

    fun startRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            try {
                val dir = File(context.filesDir, "audio_memos")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "memo_${UUID.randomUUID()}.m4a")
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
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
            } catch (_: Exception) {
                Toast.makeText(context, "Errore avvio microfono", Toast.LENGTH_SHORT).show()
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopAndSaveRecording() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        isRecording = false
        val path = outputFilePath
        if (path != null) {
            onAudioSaved(path, memoLabel.ifBlank { "Memo Vocale" })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (isRecording) {
                    Button(
                        onClick = { stopAndSaveRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ferma e Salva Memo Vocale")
                    }
                } else {
                    Button(
                        onClick = { startRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registra Memo Vocale")
                    }

                    OutlinedButton(
                        onClick = onPickFromStorage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scegli file audio dall'archivio")
                    }
                }
            }
        },
        confirmButton = {
            if (!isRecording) {
                TextButton(onClick = onDismiss) {
                    Text("Chiudi", color = Color(0xFF94A3B8))
                }
            }
        }
    )
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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
        Icon(imageVector = icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openSystemOrCustomShortcut(context: Context, item: CanvasItem) {
    try {
        if (!item.shortcutIntentUri.isNullOrEmpty()) {
            val parsed = Intent.parseUri(item.shortcutIntentUri, Intent.URI_INTENT_SCHEME)
            parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(parsed)
            return
        }
        if (!item.shortcutAction.isNullOrEmpty()) {
            context.startActivity(
                Intent(item.shortcutAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
    } catch (_: Exception) {
    }
    Toast.makeText(context, "Scorciatoia non avviabile", Toast.LENGTH_SHORT).show()
}

private fun openLinkedFile(context: Context, item: CanvasItem) {
    val uriStr = item.fileUri ?: return
    try {
        val uri = Uri.parse(uriStr)
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.fileMimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(openIntent, "Apri file con"))
    } catch (_: Exception) {
        Toast.makeText(context, "Nessuna app trovata per questo file", Toast.LENGTH_SHORT).show()
    }
}

private fun launchApp(context: Context, app: AppInfo) {
    try {
        if (app.componentName != null && app.userHandle != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startMainActivity(app.componentName, app.userHandle, null, null)
            return
        }
    } catch (_: Exception) {
    }
    launchPackage(context, app.packageName)
}

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "App non avviabile direttamente", Toast.LENGTH_SHORT).show()
    }
}

private fun findItemAtScreenPos(
    screenPos: Offset,
    items: List<CanvasItem>,
    cameraOffset: Offset,
    zoomScale: Float,
    densityPx: Float
): CanvasItem? {
    for (i in items.indices.reversed()) {
        val obj = items[i]
        val left = obj.x * zoomScale + cameraOffset.x
        val top = obj.y * zoomScale + cameraOffset.y
        val right = left + (obj.widthDp * densityPx * zoomScale)
        val bottom = top + (obj.heightDp * densityPx * zoomScale)
        if (screenPos.x in left..right && screenPos.y in top..bottom) {
            return obj
        }
    }
    return null
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
        val intersect = ((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / denom + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}
