package com.example.canvaslauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.canvaslauncher.data.AppColorCategory
import com.example.canvaslauncher.data.AppInfo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val AlphabetIndex = listOf("#") + ('A'..'Z').map { it.toString() }

fun triggerDrawerHapticTick(context: Context, strength: Float) {
    if (strength <= 0.01f) return
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return
        val durationMs = (6 + (strength * 28f)).toLong().coerceIn(6L, 35L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = (strength * 255f).toInt().coerceIn(1, 255)
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs,
                    if (vibrator.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerOverlay(
    appList: List<AppInfo>,
    showSelfInDrawer: Boolean,
    drawerAlpha: Float,
    hapticStrength: Float,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenAppInCanvasWindow: (AppInfo) -> Unit,
    onAddAppsToCanvas: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(AppColorCategory.ALL) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var drawerPullOffsetY by remember { mutableFloatStateOf(0f) }

    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedPackages = remember { mutableStateListOf<String>() }
    var actionTargetApp by remember { mutableStateOf<AppInfo?>(null) }

    // Assicura che l'icona di Canvas Launcher sia presente nel drawer come un'app normale
    val effectiveAppList = remember(appList, showSelfInDrawer) {
        val selfPkg = context.packageName
        val withoutSelf = appList.filterNot { it.packageName == selfPkg }
        if (!showSelfInDrawer) {
            withoutSelf
        } else {
            val existingSelf = appList.find { it.packageName == selfPkg }
            val selfEntry = existingSelf ?: runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(selfPkg, 0)
                AppInfo(
                    label = pm.getApplicationLabel(appInfo).toString(),
                    packageName = selfPkg,
                    icon = pm.getApplicationIcon(appInfo)
                )
            }.getOrNull()

            val combined = if (selfEntry != null) withoutSelf + selfEntry else withoutSelf
            combined.sortedBy { it.label.lowercase() }
        }
    }

    val filteredApps = remember(searchQuery, selectedColor, effectiveAppList) {
        effectiveAppList.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true)
            val matchesColor = selectedColor == AppColorCategory.ALL ||
                app.colorCategory == selectedColor
            matchesQuery && matchesColor
        }
    }

    val availableInitials = remember(filteredApps) {
        filteredApps.map { app ->
            val first = app.label.trim().firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z') first.toString() else "#"
        }.toSet()
    }

    fun scrollToLetterImmediate(letter: String) {
        if (activeLetter == letter) return
        activeLetter = letter
        triggerDrawerHapticTick(context, hapticStrength)
        val targetIndex = filteredApps.indexOfFirst { app ->
            val first = app.label.trim().firstOrNull()?.uppercaseChar()
            if (letter == "#") first == null || first !in 'A'..'Z'
            else first?.toString() == letter
        }
        if (targetIndex >= 0) {
            coroutineScope.launch { gridState.scrollToItem(targetIndex) }
        }
    }

    val swipeDownCloseConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && drawerPullOffsetY > 0f) {
                    val consumed = available.y.coerceAtLeast(-drawerPullOffsetY)
                    drawerPullOffsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val isAtTop = gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                if (isAtTop && available.y > 0f && source == NestedScrollSource.UserInput) {
                    drawerPullOffsetY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawerPullOffsetY > 160f || (drawerPullOffsetY > 30f && available.y > 800f)) {
                    drawerPullOffsetY = 0f
                    onDismiss()
                    return available
                }
                drawerPullOffsetY = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                drawerPullOffsetY = 0f
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, drawerPullOffsetY.roundToInt()) }
            .background(Color(0xFF0E1118).copy(alpha = drawerAlpha))
            .statusBarsPadding()
            .navigationBarsPadding()
            .nestedScroll(swipeDownCloseConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            // Intestazione trascinabile verso il basso per chiudere il drawer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var dragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragY = 0f },
                            onDragEnd = {
                                if (dragY > 100f) onDismiss()
                                drawerPullOffsetY = 0f
                            },
                            onDragCancel = { drawerPullOffsetY = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0f || dragY > 0f) {
                                dragY = (dragY + dragAmount).coerceAtLeast(0f)
                                drawerPullOffsetY = dragY
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF475569))
                )

                if (multiSelectMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E3A8A))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${selectedPackages.size} selezionate",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    val chosen = effectiveAppList.filter { selectedPackages.contains(it.packageName) }
                                    if (chosen.isNotEmpty()) onAddAppsToCanvas(chosen)
                                },
                                enabled = selectedPackages.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Aggiungi alla Home", color = Color.White, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = {
                                multiSelectMode = false
                                selectedPackages.clear()
                            }) {
                                Icon(Icons.Rounded.Close, "Annulla", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Cerca tra ${filteredApps.size} app...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, null, tint = Color(0xFF94A3B8))
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xAA181C26),
                                unfocusedContainerColor = Color(0xAA181C26),
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color(0xFF2B3242)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xAA181C26))
                                .border(1.dp, Color(0xFF2B3242), RoundedCornerShape(16.dp))
                        ) {
                            Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (multiSelectMode) "Tocca le app da aggiungere insieme"
                        else "Scorri giù per chiudere  •  Tieni premuto per opzioni",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    if (activeLetter != null) {
                        Text(
                            text = activeLetter!!,
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nessuna app trovata", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(start = 14.dp, end = 36.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = selectedPackages.contains(app.packageName)
                            val bitmap = remember(app.packageName) {
                                app.icon.toBitmap(width = 112, height = 112).asImageBitmap()
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (multiSelectMode) {
                                                if (isChecked) selectedPackages.remove(app.packageName)
                                                else selectedPackages.add(app.packageName)
                                            } else if (app.packageName == context.packageName) {
                                                onDismiss()
                                            } else {
                                                onLaunchApp(app)
                                            }
                                        },
                                        onLongClick = {
                                            if (!multiSelectMode) actionTargetApp = app
                                        }
                                    )
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .alpha(if (app.isArchived) 0.72f else 1f)
                                    )
                                    if (multiSelectMode && isChecked) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F172A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else if (app.isArchived) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F172A)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.label,
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Indice alfabetico laterale con vibrazione
                var indexHeightPx by remember { mutableFloatStateOf(1f) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                        .width(24.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x99141822))
                        .onGloballyPositioned { coords ->
                            indexHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
                        }
                        .pointerInput(filteredApps, hapticStrength) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    val idx = ((offset.y / indexHeightPx).coerceIn(0f, 0.999f) * AlphabetIndex.size).toInt()
                                    scrollToLetterImmediate(AlphabetIndex[idx])
                                },
                                onDragEnd = { activeLetter = null },
                                onDragCancel = { activeLetter = null }
                            ) { change, _ ->
                                change.consume()
                                val idx = ((change.position.y / indexHeightPx).coerceIn(0f, 0.999f) * AlphabetIndex.size).toInt()
                                scrollToLetterImmediate(AlphabetIndex[idx])
                            }
                        }
                ) {
                    AlphabetIndex.forEach { letter ->
                        val hasApps = availableInitials.contains(letter)
                        val isCurrent = activeLetter == letter
                        Text(
                            text = letter,
                            fontSize = 9.sp,
                            fontWeight = if (isCurrent || hasApps) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCurrent -> Color(0xFF38BDF8)
                                hasApps -> Color.White
                                else -> Color(0xFF475569)
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scrollToLetterImmediate(letter) }
                        )
                    }
                }
            }

            // Barra filtro colori
            Surface(
                color = Color(0xCC141822),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppColorCategory.entries.forEach { category ->
                        val isSelected = selectedColor == category
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 34.dp else 28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (category == AppColorCategory.ALL) {
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFFEF4444),
                                                Color(0xFFEAB308),
                                                Color(0xFF22C55E),
                                                Color(0xFF3B82F6),
                                                Color(0xFFA855F7),
                                                Color(0xFFEF4444)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color(category.swatchHex), Color(category.swatchHex))
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = category }
                        )
                    }
                }
            }
        }

        // Dialog opzioni app
        if (actionTargetApp != null) {
            val targetApp = actionTargetApp!!
            AlertDialog(
                onDismissRequest = { actionTargetApp = null },
                containerColor = Color(0xFF181B24),
                titleContentColor = Color.White,
                title = { Text(targetApp.label, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                actionTargetApp = null
                                onOpenAppInCanvasWindow(targetApp)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apri in Finestra sulla Tela")
                        }

                        Button(
                            onClick = {
                                actionTargetApp = null
                                onAddAppsToCanvas(listOf(targetApp))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AddToHomeScreen, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aggiungi Icona alla Tela")
                        }

                        OutlinedButton(
                            onClick = {
                                actionTargetApp = null
                                multiSelectMode = true
                                selectedPackages.clear()
                                selectedPackages.add(targetApp.packageName)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Checklist, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seleziona più app da aggiungere")
                        }

                        OutlinedButton(
                            onClick = {
                                actionTargetApp = null
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${targetApp.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Informazioni App")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { actionTargetApp = null }) {
                        Text("Chiudi", color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}
