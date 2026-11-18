package com.example.canvaslauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.canvaslauncher.data.AppColorCategory
import com.example.canvaslauncher.data.AppInfo
import kotlinx.coroutines.launch

private val AlphabetIndex = listOf("#") + ('A'..'Z').map { it.toString() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerOverlay(
    appList: List<AppInfo>,
    onLaunchApp: (AppInfo) -> Unit,
    onAddAppToCanvas: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(AppColorCategory.ALL) }
    var activeLetter by remember { mutableStateOf<String?>(null) }

    // Stato onda luminosa al clic su un colore
    val waveProgress = remember { Animatable(0f) }
    var waveColor by remember { mutableStateOf(Color.Transparent) }
    var waveCenter by remember { mutableStateOf(Offset(500f, 1800f)) }

    val filteredApps = remember(searchQuery, selectedColor, appList) {
        appList.filter { app ->
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

    fun jumpToLetter(letter: String) {
        activeLetter = letter
        val targetIndex = filteredApps.indexOfFirst { app ->
            val first = app.label.trim().firstOrNull()?.uppercaseChar()
            if (letter == "#") {
                first == null || first !in 'A'..'Z'
            } else {
                first?.toString() == letter
            }
        }
        if (targetIndex >= 0) {
            coroutineScope.launch {
                gridState.animateScrollToItem(targetIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Sfondo pulito e semi-opaco che lascia intravedere gli elementi nella home sottostante
            .background(Color(0xD90E1118))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- EFFETTO ONDA LUMINOSA DI COLORE ---
        if (waveProgress.value > 0f && waveProgress.value < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.height * 1.35f
                val currentRadius = maxRadius * waveProgress.value
                val fadeAlpha = (1f - waveProgress.value).coerceIn(0f, 1f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            waveColor.copy(alpha = 0.04f * fadeAlpha),
                            waveColor.copy(alpha = 0.34f * fadeAlpha),
                            waveColor.copy(alpha = 0.0f)
                        ),
                        center = waveCenter,
                        radius = (currentRadius + 160f).coerceAtLeast(10f)
                    ),
                    radius = currentRadius + 160f,
                    center = waveCenter
                )

                drawCircle(
                    color = waveColor.copy(alpha = 0.55f * fadeAlpha),
                    radius = currentRadius,
                    center = waveCenter,
                    style = Stroke(width = 14f * (1f - waveProgress.value * 0.5f))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
        ) {
            // --- BARRA SUPERIORE RICERCA & CHIUSURA ---
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
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xCC181C26),
                        unfocusedContainerColor = Color(0xCC181C26),
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
                        .background(Color(0xCC181C26))
                        .border(1.dp, Color(0xFF2B3242), RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Chiudi menu app",
                        tint = Color.White
                    )
                }
            }

            // Suggerimento rapido
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tocca per aprire  •  Tieni premuto per fissare sulla tela",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                if (activeLetter != null) {
                    Text(
                        text = "Lettera: $activeLetter",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- GRIGLIA APP + INDICE LATERALE A-Z ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nessuna app trovata con questo filtro",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 36.dp, // Spazio per l'indice A-Z
                            top = 6.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val bitmap = remember(app.packageName) {
                                app.icon.toBitmap(width = 108, height = 108).asImageBitmap()
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x80181C26))
                                    .combinedClickable(
                                        onClick = { onLaunchApp(app) },
                                        onLongClick = { onAddAppToCanvas(app) }
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .alpha(if (app.isArchived) 0.72f else 1f)
                                    )
                                    if (app.isArchived) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F172A))
                                                .border(1.dp, Color(0xFF38BDF8), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = "App archiviata",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.label,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 5.dp)
                                )
                            }
                        }
                    }
                }

                // --- INDICE LATERALE CLICCABILE E SCORREVOLE DA A A Z ---
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
                        .background(Color(0xB3141822))
                        .border(1.dp, Color(0xFF283042), RoundedCornerShape(12.dp))
                        .onGloballyPositioned { coords ->
                            indexHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
                        }
                        .pointerInput(filteredApps) {
                            detectVerticalDragGestures(
                                onDragEnd = { activeLetter = null },
                                onDragCancel = { activeLetter = null }
                            ) { change, _ ->
                                change.consume()
                                val ratio = (change.position.y / indexHeightPx).coerceIn(0f, 0.999f)
                                val idx = (ratio * AlphabetIndex.size).toInt()
                                jumpToLetter(AlphabetIndex[idx])
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
                                .clickable { jumpToLetter(letter) }
                        )
                    }
                }
            }

            // --- BARRA INFERIORE DI SELEZIONE APP PER COLORE ---
            Surface(
                color = Color(0xE6141822),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppColorCategory.entries.forEach { category ->
                            val isSelected = selectedColor == category
                            var swatchCenter by remember { mutableStateOf(Offset(500f, 1800f)) }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        swatchCenter = Offset(
                                            pos.x + coords.size.width / 2f,
                                            pos.y + coords.size.height / 2f
                                        )
                                    }
                                    .clickable {
                                        selectedColor = category
                                        waveCenter = swatchCenter
                                        waveColor = if (category == AppColorCategory.ALL) {
                                            Color(0xFF38BDF8)
                                        } else {
                                            Color(category.swatchHex)
                                        }
                                        coroutineScope.launch {
                                            waveProgress.snapTo(0f)
                                            // Onda luminosa fluida e non troppo veloce (950 ms)
                                            waveProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(
                                                    durationMillis = 950,
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                        }
                                    }
                            ) {
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
                                                    listOf(
                                                        Color(category.swatchHex),
                                                        Color(category.swatchHex)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color(0xFF475569),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = category.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
