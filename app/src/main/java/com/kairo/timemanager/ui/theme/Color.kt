package com.kairo.timemanager.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand ramp — a deep violet primary with a warm amber accent for rewards and streaks.
val Violet10 = Color(0xFF1B0F3B)
val Violet20 = Color(0xFF2C1B5C)
val Violet30 = Color(0xFF3F2A85)
val Violet40 = Color(0xFF5B4BE1)
val Violet60 = Color(0xFF8B7BFF)
val Violet80 = Color(0xFFC7BEFF)
val Violet90 = Color(0xFFE6E1FF)
val Violet95 = Color(0xFFF3F0FF)

val Teal40 = Color(0xFF0E9F8E)
val Teal60 = Color(0xFF3FD5C0)
val Teal90 = Color(0xFFCFF6F0)

val Amber40 = Color(0xFFD98200)
val Amber60 = Color(0xFFFFB020)
val Amber90 = Color(0xFFFFE9BF)

val Rose40 = Color(0xFFC2334D)
val Rose60 = Color(0xFFFF6B81)
val Rose90 = Color(0xFFFFDDE2)

val Ink0 = Color(0xFF0E0B1A)
val Ink10 = Color(0xFF17132A)
val Ink20 = Color(0xFF221C3B)
val Ink30 = Color(0xFF2E2750)
val Paper = Color(0xFFF7F5FF)
val PaperAlt = Color(0xFFFFFFFF)

val KairoLightColors = lightColorScheme(
    primary = Violet40,
    onPrimary = Color.White,
    primaryContainer = Violet90,
    onPrimaryContainer = Violet10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF00332C),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF3D2500),
    error = Rose40,
    onError = Color.White,
    errorContainer = Rose90,
    onErrorContainer = Color(0xFF44000C),
    background = Paper,
    onBackground = Ink10,
    surface = Paper,
    onSurface = Ink10,
    surfaceVariant = Violet95,
    onSurfaceVariant = Color(0xFF4B4463),
    surfaceContainer = PaperAlt,
    surfaceContainerHigh = Color(0xFFFBFAFF),
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFB5AEC9),
    outlineVariant = Color(0xFFDDD8EC)
)

val KairoDarkColors = darkColorScheme(
    primary = Violet60,
    onPrimary = Violet10,
    primaryContainer = Violet30,
    onPrimaryContainer = Violet90,
    secondary = Teal60,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Teal90,
    tertiary = Amber60,
    onTertiary = Color(0xFF3D2500),
    tertiaryContainer = Color(0xFF6B4400),
    onTertiaryContainer = Amber90,
    error = Rose60,
    onError = Color(0xFF44000C),
    errorContainer = Color(0xFF7A1225),
    onErrorContainer = Rose90,
    background = Ink0,
    onBackground = Color(0xFFEAE5F7),
    surface = Ink0,
    onSurface = Color(0xFFEAE5F7),
    surfaceVariant = Ink20,
    onSurfaceVariant = Color(0xFFC5BEDA),
    surfaceContainer = Ink10,
    surfaceContainerHigh = Ink20,
    surfaceContainerHighest = Ink30,
    outline = Color(0xFF554D70),
    outlineVariant = Color(0xFF352E4D)
)

/** Palette offered in the block editor and used as fallback colours for categories. */
val TaskPalette = listOf(
    "#6C5CE7", "#0E9F8E", "#FFB020", "#FF6B81",
    "#3B82F6", "#22C55E", "#A855F7", "#F97316",
    "#EC4899", "#14B8A6", "#EF4444", "#64748B"
)

fun parseHexColor(hex: String, fallback: Color = Violet40): Color = runCatching {
    val cleaned = hex.trim().removePrefix("#")
    when (cleaned.length) {
        6 -> Color(("FF$cleaned").toLong(16))
        8 -> Color(cleaned.toLong(16))
        else -> fallback
    }
}.getOrDefault(fallback)

/** Deterministic colour for a category that has no explicit colour yet. */
fun colorForCategory(category: String): Color =
    parseHexColor(TaskPalette[(category.hashCode().let { if (it < 0) -it else it }) % TaskPalette.size])
