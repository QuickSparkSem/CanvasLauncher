package com.example.canvaslauncher.model

enum class ItemType(val badgeLabel: String, val accentHex: Long) {
    APP_SHORTCUT("APP", 0xFF38BDF8),
    NOTE("NOTA", 0xFFFBBF24),
    IMAGE_SNAPSHOT("SNAPSHOT", 0xFFA78BFA),
    SESSION_GROUP("SESSIONE", 0xFF34D399)
}

data class CanvasItem(
    val id: String,
    val title: String,
    val content: String = "",
    val packageName: String? = null,
    val imagePath: String? = null,
    val x: Float,
    val y: Float,
    val widthDp: Float = 180f,
    val heightDp: Float = 130f,
    val colorHex: Long = 0xFF1E222D,
    val type: ItemType = ItemType.NOTE,
    val sessionPackages: List<String> = emptyList()
)
