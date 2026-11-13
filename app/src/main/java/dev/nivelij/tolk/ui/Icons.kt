package dev.nivelij.tolk.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object Icons {
    val Close = stroke("M6.5 6.5l11 11M17.5 6.5l-11 11")
    val Swap = stroke("M5 8h13.5M15 4.5L18.5 8L15 11.5M19 16H5.5M9 12.5L5.5 16L9 19.5")
    val Camera = stroke("M3.5 9A2.5 2.5 0 0 1 6 6.5h1.8l1.5-2.2h5.4l1.5 2.2H18A2.5 2.5 0 0 1 20.5 9v8.5A2.5 2.5 0 0 1 18 20H6a2.5 2.5 0 0 1-2.5-2.5zM12 10a3.2 3.2 0 1 0 0 6.4a3.2 3.2 0 1 0 0-6.4")
    val Text = stroke("M4.5 7V5h15v2M12 5v14M9 19h6")
    val Settings = stroke("M4 7h9M17 7h3M15 4.8v4.4M4 17h3M11 17h9M9 14.8v4.4")
    val Copy = stroke("M9 8.5h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-10a1 1 0 0 1 1-1zM5 15.5v-11a1 1 0 0 1 1-1h9")
    val Explain = stroke("M12 3.5l1.9 5.1L19 10.5l-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9zM18.5 16.5l.7 1.8l1.8.7l-1.8.7l-.7 1.8l-.7-1.8l-1.8-.7l1.8-.7z")
    val Image = stroke("M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM4 15.5l4.5-4.5l4 4l2.5-2.5l5 5M15.5 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3")
    val ChevronDown = stroke("M7 10l5 5l5-5")
    val Search = stroke("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M20 20l-4.8-4.8")
    val Back = stroke("M15 5l-7 7l7 7")
    val Check = stroke("M5 12.5l4.5 4.5L19 7")
    val Retry = stroke("M4.5 12a7.5 7.5 0 1 0 2.3-5.4M4.5 4v4h4")
    val Plus = stroke("M12 5v14M5 12h14")
    val Share = stroke("M12 3.5v11M8 7.5l4-4l4 4M6 12.5v6a1.5 1.5 0 0 0 1.5 1.5h9a1.5 1.5 0 0 0 1.5-1.5v-6")

    val Bubbles = stroke("M4.5 4.5h9a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H9l-3.5 3v-3h-1a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2zM18.5 9h1a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-1v3l-3.5-3h-3a2 2 0 0 1-2-2v-.5", 1.8f)

    private fun stroke(path: String, width: Float = 1.7f) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
