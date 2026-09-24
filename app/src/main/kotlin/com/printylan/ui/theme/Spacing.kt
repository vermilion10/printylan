package com.printylan.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp

/** Material 3 spacing scale, built on 8dp steps with a 4dp half step. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 16.dp
    val md = 24.dp
    val lg = 32.dp
    val xl = 48.dp

    /** Content never grows wider than this on large windows. */
    val maxContentWidth = 840.dp

    /** Screen margin per window width class: 16dp on compact, 24dp from medium up. */
    fun margin(widthSizeClass: WindowWidthSizeClass) =
        if (widthSizeClass == WindowWidthSizeClass.Compact) sm else md
}
