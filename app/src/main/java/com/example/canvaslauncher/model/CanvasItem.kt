package com.example.canvaslauncher.model

enum class ItemType(val badgeLabel: String, val accentHex: Long) {
    APP_SHORTCUT("APP", 0xFF38BDF8),
    NOTE("NOTA", 0xFFFBBF24),
    IMAGE_SNAPSHOT("SNAPSHOT", 0xFFA78BFA),
    SESSION_GROUP("SESSIONE", 0xFF34D399),
    SYSTEM_WIDGET("WIDGET", 0xFFF472B6),
    SYSTEM_SHORTCUT("SISTEMA", 0xFFFB923C)
}

data class CanvasItem(
    val id: String,
    val title: String,
    val content: String = "",
    val packageName: String? = null,
    val imagePath: String? = null,
    val appWidgetId: Int = -1,
    val shortcutAction: String? = null,
    val x: Float,
    val y: Float,
    val widthDp: Float = 185f,
    val heightDp: Float = 135f,
    val colorHex: Long = 0xFF1E222D,
    val type: ItemType = ItemType.NOTE,
    val sessionPackages: List<String> = emptyList()
)
