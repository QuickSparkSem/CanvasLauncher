package com.example.canvaslauncher.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenWith
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.example.canvaslauncher.data.AppInfo
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class CanvasTool { PAN, SELECT, LASSO }

private val CardPalette = listOf(
    0xFF1E222D, // Ardesia Scuro
    0xFF1E3A5F, // Blu Oceano
    0xFF164E3F, // Verde Smeraldo
    0xFF4C1D4B, // Viola Profondo
    0xFF5C2B1E  // Ambra Caldo
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasLauncherApp(
    items: MutableList<CanvasItem>,
    appList: List<AppInfo>,
    incomingText: String?,
    incomingImageUri: Uri?,
    onIncomingConsumed: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BackHandler(enabled = true) { }

    var activeTool by remember { mutableStateOf(CanvasTool.PAN) }

    // Stato Telecamera 2D (Pixel)
    var cameraOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    // Stato Cursore e Lazo
    var cursorPos by remember { mutableStateOf(Offset(360f, 560f)) }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    val selectedIds = remember { mutableStateListOf<String>() }
    var isDraggingSelected by remember { mutableStateOf(false) }

    // Menu Contestuale & Finestre
    var showAppDrawer by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuScreenPos by remember { mutableStateOf(Offset.Zero) }
    var targetItemForMenu by remember { mutableStateOf<CanvasItem?>(null) }

    // Finestra Modifica Scheda
    var editingItem by remember { mutableStateOf<CanvasItem?>(null) }
    var editTitleText by remember { mutableStateOf("") }
    var editContentText by remember { mutableStateOf("") }
    var editColorHex by remember { mutableLongStateOf(0xFF1E222D) }
    var editSizePreset by remember { mutableIntStateOf(1) } // 0 = Piccola, 1 = Media, 2 = Grande

    // Finestra Avvio Sessione di Lavoro
    var activeSessionDialogItem by remember { mutableStateOf<CanvasItem?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, uri)
            if (savedPath != null) {
                val worldCenterX = (-cameraOffset.x + 220f) / zoomScale
                val worldCenterY = (-cameraOffset.y + 340f) / zoomScale
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Snapshot Lavoro",
                        imagePath = savedPath,
                        x = worldCenterX,
                        y = worldCenterY,
                        widthDp = 220f,
                        heightDp = 180f,
                        type = ItemType.IMAGE_SNAPSHOT
                    )
                )
                StorageManager.saveItems(context, items)
            }
        }
    }

    LaunchedEffect(incomingText, incomingImageUri) {
        var added = false
        val worldX = (-cameraOffset.x + 200f) / zoomScale
        val worldY = (-cameraOffset.y + 300f) / zoomScale

        if (!incomingText.isNullOrBlank()) {
            items.add(
                CanvasItem(
                    id = UUID.randomUUID().toString(),
                    title = "Nota Ricevuta",
                    content = incomingText,
                    x = worldX,
                    y = worldY,
                    widthDp = 210f,
                    heightDp = 145f,
                    colorHex = 0xFF1E3A5F,
                    type = ItemType.NOTE
                )
            )
            added = true
        }
        if (incomingImageUri != null) {
            val savedPath = StorageManager.copyUriToInternalStorage(context, incomingImageUri)
            if (savedPath != null) {
                items.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = "Snapshot Ricevuto",
                        imagePath = savedPath,
                        x = worldX + 35f,
                        y = worldY + 35f,
                        widthDp = 220f,
                        heightDp = 180f,
                        type = ItemType.IMAGE_SNAPSHOT
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
                title = "Sessione (${selectedObjects.size} elem.)",
                content = summary,
                x = avgX,
                y = avgY,
                widthDp = 220f,
                heightDp = 160f,
                colorHex = 0xFF164E3F,
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
            .background(Color(0xFF0C0E14))
            // Supporto Mouse Fisico (Cursore continuo + Tasto Destro)
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
            // Gestione Pan/Zoom, Cursore e Lazo
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
        // --- GRIGLIA PUNTINATA DELLO SPAZIO INFINITO ---
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
                            color = Color(0xFF1F2430),
                            radius = 2.5f,
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
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
            items.forEach { item ->
                val isSelected = selectedIds.contains(item.id)
                val borderColor = if (isSelected) Color(0xFF60A5FA) else Color(0xFF2E3444)

                Card(
                    modifier = Modifier
                        .offset { IntOffset(item.x.roundToInt(), item.y.roundToInt()) }
                        .size(item.widthDp.dp, item.heightDp.dp)
                        .shadow(if (isSelected) 12.dp else 4.dp, RoundedCornerShape(16.dp))
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                if (activeTool == CanvasTool.SELECT) {
                                    if (selectedIds.contains(item.id)) {
                                        selectedIds.remove(item.id)
                                    } else {
                                        selectedIds.clear()
                                        selectedIds.add(item.id)
                                    }
                                } else {
                                    when (item.type) {
                                        ItemType.APP_SHORTCUT -> {
                                            item.packageName?.let { launchPackage(context, it) }
                                        }
                                        ItemType.SESSION_GROUP -> {
                                            if (item.sessionPackages.size == 1) {
                                                launchPackage(context, item.sessionPackages.first())
                                            } else if (item.sessionPackages.isNotEmpty()) {
                                                activeSessionDialogItem = item
                                            } else {
                                                openEditor(item) { t, c, col, sz ->
                                                    editingItem = item
                                                    editTitleText = t
                                                    editContentText = c
                                                    editColorHex = col
                                                    editSizePreset = sz
                                                }
                                            }
                                        }
                                        ItemType.NOTE, ItemType.IMAGE_SNAPSHOT -> {
                                            openEditor(item) { t, c, col, sz ->
                                                editingItem = item
                                                editTitleText = t
                                                editContentText = c
                                                editColorHex = col
                                                editSizePreset = sz
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                targetItemForMenu = item
                                val screenX = item.x * zoomScale + cameraOffset.x
                                val screenY = item.y * zoomScale + cameraOffset.y
                                contextMenuScreenPos = Offset(screenX, screenY)
                                showContextMenu = true
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1D355E) else Color(item.colorHex)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Badge Tipo Elemento
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(item.type.accentHex).copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.type.badgeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(item.type.accentHex)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Intestazione con icona App o Titolo
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.type == ItemType.APP_SHORTCUT && item.packageName != null) {
                                val appIcon = remember(item.packageName, appList) {
                                    appList.find { it.packageName == item.packageName }
                                        ?.icon?.toBitmap(width = 84, height = 84)?.asImageBitmap()
                                }
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .padding(end = 8.dp)
                                    )
                                }
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.type == ItemType.IMAGE_SNAPSHOT && !item.imagePath.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = File(item.imagePath),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else if (item.content.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // --- TRACCIATO LAZO ---
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

        // --- BARRA DI STATO SUPERIORE (HUD) ---
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
                color = Color(0xCC161922)
            ) {
                val posX = (-cameraOffset.x / zoomScale).roundToInt()
                val posY = (-cameraOffset.y / zoomScale).roundToInt()
                val zoomPct = (zoomScale * 100).roundToInt()
                Text(
                    text = "X:$posX  Y:$posY  •  $zoomPct%",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            if (selectedIds.isNotEmpty()) {
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

        // --- CURSORE VISIVO ---
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

        // --- BARRA STRUMENTI IN BASSO (DOCK SPAZIALE) ---
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
                    icon = Icons.Rounded.NoteAdd,
                    label = "Nota",
                    containerColor = Color(0xFF1E293B),
                    onClick = {
                        val worldX = (-cameraOffset.x + 220f) / zoomScale
                        val worldY = (-cameraOffset.y + 340f) / zoomScale
                        items.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = "Nuova Nota",
                                content = "Tocca per scrivere...",
                                x = worldX,
                                y = worldY,
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
                    onClick = { imagePickerLauncher.launch("image/*") }
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

        // --- MENU CONTESTUALE (TASTO DESTRO MOUSE O PRESSIONE PROLUNGATA) ---
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            containerColor = Color(0xFF1C202B),
            offset = with(density) {
                DpOffset(contextMenuScreenPos.x.toDp(), contextMenuScreenPos.y.toDp())
            }
        ) {
            if (targetItemForMenu != null) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = Color.White) },
                    text = { Text("Modifica Scheda", color = Color.White) },
                    onClick = {
                        val item = targetItemForMenu!!
                        openEditor(item) { t, c, col, sz ->
                            editingItem = item
                            editTitleText = t
                            editContentText = c
                            editColorHex = col
                            editSizePreset = sz
                        }
                        showContextMenu = false
                    }
                )
            }

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
                leadingIcon = { Icon(Icons.Rounded.NoteAdd, null, tint = Color.White) },
                text = { Text("Nuova Nota qui", color = Color.White) },
                onClick = {
                    val worldX = (contextMenuScreenPos.x - cameraOffset.x) / zoomScale
                    val worldY = (contextMenuScreenPos.y - cameraOffset.y) / zoomScale
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = "Nota Rapida",
                            content = "Tocca per scrivere...",
                            x = worldX,
                            y = worldY,
                            type = ItemType.NOTE
                        )
                    )
                    StorageManager.saveItems(context, items)
                    showContextMenu = false
                }
            )

            if (targetItemForMenu != null || selectedIds.isNotEmpty()) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFF87171)) },
                    text = { Text("Elimina", color = Color(0xFFF87171)) },
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            items.removeAll { selectedIds.contains(it.id) }
                            selectedIds.clear()
                        } else {
                            targetItemForMenu?.let { items.remove(it) }
                        }
                        StorageManager.saveItems(context, items)
                        showContextMenu = false
                    }
                )
            }
        }

        // --- FINESTRA MODIFICA SCHEDA (TESTO, COLORE E DIMENSIONE) ---
        if (editingItem != null) {
            AlertDialog(
                onDismissRequest = { editingItem = null },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text("Personalizza Scheda", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editTitleText,
                            onValueChange = { editTitleText = it },
                            label = { Text("Titolo") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = editContentText,
                            onValueChange = { editContentText = it },
                            label = { Text("Contenuto / Appunti") },
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Text("Colore scheda", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CardPalette.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (editColorHex == hex) 2.5.dp else 1.dp,
                                            color = if (editColorHex == hex) Color.White else Color(0xFF475569),
                                            shape = CircleShape
                                        )
                                        .clickable { editColorHex = hex }
                                )
                            }
                        }

                        Text("Dimensione", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Piccola", "Media", "Grande").forEachIndexed { index, label ->
                                FilterChip(
                                    selected = editSizePreset == index,
                                    onClick = { editSizePreset = index },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val idx = items.indexOfFirst { it.id == editingItem?.id }
                        if (idx >= 0) {
                            val (w, h) = when (editSizePreset) {
                                0 -> 150f to 105f
                                2 -> 250f to 200f
                                else -> 190f to 140f
                            }
                            items[idx] = items[idx].copy(
                                title = editTitleText,
                                content = editContentText,
                                colorHex = editColorHex,
                                widthDp = w,
                                heightDp = h
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

        // --- FINESTRA SESSIONE DI LAVORO ---
        if (activeSessionDialogItem != null) {
            val session = activeSessionDialogItem!!
            AlertDialog(
                onDismissRequest = { activeSessionDialogItem = null },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text(session.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "App raggruppate in questa sessione:",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
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

        if (showAppDrawer) {
            AppDrawerDialog(
                appList = appList,
                onAppSelected = { app ->
                    val worldX = (-cameraOffset.x + 220f) / zoomScale
                    val worldY = (-cameraOffset.y + 340f) / zoomScale
                    items.add(
                        CanvasItem(
                            id = UUID.randomUUID().toString(),
                            title = app.label,
                            packageName = app.packageName,
                            x = worldX,
                            y = worldY,
                            widthDp = 155f,
                            heightDp = 96f,
                            type = ItemType.APP_SHORTCUT,
                            colorHex = 0xFF1A2234
                        )
                    )
                    StorageManager.saveItems(context, items)
                    showAppDrawer = false
                },
                onDismiss = { showAppDrawer = false }
            )
        }
    }
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

private fun openEditor(
    item: CanvasItem,
    onReady: (String, String, Long, Int) -> Unit
) {
    val preset = when {
        item.widthDp <= 160f -> 0
        item.widthDp >= 230f -> 2
        else -> 1
    }
    onReady(item.title, item.content, item.colorHex, preset)
}

private fun launchPackage(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "App non trovata", Toast.LENGTH_SHORT).show()
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
