package com.example.canvaslauncher.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvaslauncher.data.StatusBarMode
import kotlin.math.roundToInt

data class SystemShortcutPreset(
    val title: String,
    val description: String,
    val action: String
)

val AvailableSystemShortcuts = listOf(
    SystemShortcutPreset("Wi-Fi", "Impostazioni reti wireless", Settings.ACTION_WIFI_SETTINGS),
    SystemShortcutPreset("Bluetooth", "Dispositivi collegati", Settings.ACTION_BLUETOOTH_SETTINGS),
    SystemShortcutPreset("Schermo", "Luminosità e display", Settings.ACTION_DISPLAY_SETTINGS),
    SystemShortcutPreset("Batteria", "Risparmio energetico", Settings.ACTION_BATTERY_SAVER_SETTINGS),
    SystemShortcutPreset("Archiviazione", "Spazio e memoria", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SystemShortcutPreset("App Home", "Launcher predefinito", Settings.ACTION_HOME_SETTINGS),
    SystemShortcutPreset("Impostazioni", "Menu principale sistema", Settings.ACTION_SETTINGS)
)

val ElementColorOptions = listOf(
    0xFF1E222D to "Ardesia",
    0xFF1E3A5F to "Blu",
    0xFF164E3F to "Verde",
    0xFF4C1D4B to "Viola",
    0xFF5C2B1E to "Ambra",
    0xFF164E63 to "Ciano"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LauncherSettingsDialog(
    backgroundAlpha: Float,
    onBackgroundAlphaChange: (Float) -> Unit,
    drawerAlpha: Float,
    onDrawerAlphaChange: (Float) -> Unit,
    statusBarMode: StatusBarMode,
    onStatusBarModeChange: (StatusBarMode) -> Unit,
    showSelfInDrawer: Boolean,
    onShowSelfInDrawerChange: (Boolean) -> Unit,
    defaultElementColor: Long,
    onDefaultElementColorChange: (Long) -> Unit,
    onApplyColorToAllExisting: (Long) -> Unit,
    onAddSystemShortcut: (SystemShortcutPreset) -> Unit,
    onChangeWallpaper: () -> Unit,
    onAddSystemWidget: () -> Unit,
    onRestoreGuideNote: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161922),
        titleContentColor = Color.White,
        title = {
            Text("Impostazioni Canvas Launcher", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. TRASPARENZA SFONDO TELA
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacità sfondo tela", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${(backgroundAlpha * 100).roundToInt()}%", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = backgroundAlpha,
                        onValueChange = onBackgroundAlphaChange,
                        valueRange = 0f..1f
                    )
                }

                // 2. TRASPARENZA APP DRAWER
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacità menu App (Drawer)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${(drawerAlpha * 100).roundToInt()}%", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value = drawerAlpha,
                        onValueChange = onDrawerAlphaChange,
                        valueRange = 0.2f..1f
                    )
                }

                HorizontalDivider(color = Color(0xFF283042))

                // 3. BARRA DI STATO (STATUS BAR)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Barra di stato in alto (Status Bar)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusBarMode.entries.forEach { mode ->
                            FilterChip(
                                selected = statusBarMode == mode,
                                onClick = { onStatusBarModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                // 4. MOSTRA LAUNCHER NEL DRAWER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Mostra Canvas Launcher tra le app", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Rende visibile l'icona del launcher nel drawer", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Switch(
                        checked = showSelfInDrawer,
                        onCheckedChange = onShowSelfInDrawerChange
                    )
                }

                HorizontalDivider(color = Color(0xFF283042))

                // 5. COLORE DEGLI ELEMENTI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Colore predefinito degli elementi", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ElementColorOptions.forEach { (hex, _) ->
                            val isSelected = defaultElementColor == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF475569),
                                        shape = CircleShape
                                    )
                                    .clickable { onDefaultElementColorChange(hex) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onApplyColorToAllExisting(defaultElementColor) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Applica questo colore a tutti gli elementi", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF283042))

                // 6. SCORCIATOIE DI SISTEMA RAPIDE
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aggiungi scorciatoie di sistema alla tela", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvailableSystemShortcuts.forEach { preset ->
                            AssistChip(
                                onClick = { onAddSystemShortcut(preset) },
                                label = { Text("+ ${preset.title}", color = Color.White, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.SettingsApplications,
                                        contentDescription = null,
                                        tint = Color(0xFFFB923C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF283042))

                // 7. AZIONI RAPIDE & RIPRISTINO GUIDA
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onChangeWallpaper()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cambia Sfondo del Telefono")
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAddSystemWidget()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Widgets, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aggiungi Widget di Sistema")
                    }

                    TextButton(
                        onClick = {
                            onRestoreGuideNote()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ripristina Nota Guida & Roadmap", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fatto")
            }
        }
    )
}
