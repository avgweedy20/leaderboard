package com.scoreboard.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ScoreBoardIcons {

    val Stopwatch: ImageVector by lazy {
        ImageVector.Builder(
            name = "Stopwatch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                // Circle
                moveTo(12f, 21f)
                arcTo(8f, 8f, 0f, true, false, 12f, 5f)
                arcTo(8f, 8f, 0f, true, false, 12f, 21f)
                close()
                // Top stem
                moveTo(12f, 5f)
                lineTo(12f, 3f)
                moveTo(10f, 3f)
                lineTo(14f, 3f)
                // Hands
                moveTo(12f, 9f)
                lineTo(12f, 13f)
                lineTo(15f, 16f)
            }
        }.build()
    }

    val Cricket: ImageVector by lazy {
        ImageVector.Builder(
            name = "Cricket",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                // Bat & Wickets
                moveTo(5f, 19f)
                lineTo(19f, 5f)
                moveTo(15f, 5f)
                lineTo(19f, 9f)
                moveTo(14f, 18f)
                lineTo(14f, 21f)
                moveTo(17f, 18f)
                lineTo(17f, 21f)
                moveTo(20f, 18f)
                lineTo(20f, 21f)
                moveTo(13f, 18f)
                lineTo(21f, 18f)
                // Ball
                moveTo(8f, 8f)
                arcTo(2f, 2f, 0f, true, false, 8f, 7.99f)
            }
        }.build()
    }

    val Football: ImageVector by lazy {
        ImageVector.Builder(
            name = "Football",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                arcTo(9f, 9f, 0f, true, false, 12f, 21f)
                close()
                moveTo(12f, 7f)
                lineTo(15.5f, 9.5f)
                lineTo(15.5f, 13.5f)
                lineTo(12f, 16f)
                lineTo(8.5f, 13.5f)
                lineTo(8.5f, 9.5f)
                close()
            }
        }.build()
    }

    val Basketball: ImageVector by lazy {
        ImageVector.Builder(
            name = "Basketball",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                arcTo(9f, 9f, 0f, true, false, 12f, 21f)
                close()
                moveTo(3f, 12f)
                lineTo(21f, 12f)
                moveTo(12f, 3f)
                lineTo(12f, 21f)
            }
        }.build()
    }

    val Trophy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trophy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(6f, 3f)
                lineTo(18f, 3f)
                lineTo(18f, 9f)
                arcTo(6f, 6f, 0f, false, true, 6f, 9f)
                close()
                moveTo(12f, 15f)
                lineTo(12f, 19f)
                moveTo(8f, 21f)
                lineTo(16f, 21f)
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(3f, 4f)
                lineTo(21f, 4f)
                lineTo(21f, 21f)
                lineTo(3f, 21f)
                close()
                moveTo(16f, 2f)
                lineTo(16f, 6f)
                moveTo(8f, 2f)
                lineTo(8f, 6f)
                moveTo(3f, 9f)
                lineTo(21f, 9f)
            }
        }.build()
    }

    val BracketTree: ImageVector by lazy {
        ImageVector.Builder(
            name = "BracketTree",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(3f, 5f)
                lineTo(9f, 5f)
                lineTo(9f, 9f)
                lineTo(3f, 9f)
                close()
                moveTo(3f, 15f)
                lineTo(9f, 15f)
                lineTo(9f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(15f, 10f)
                lineTo(21f, 10f)
                lineTo(21f, 14f)
                lineTo(15f, 14f)
                close()
                moveTo(9f, 7f)
                lineTo(12f, 7f)
                lineTo(12f, 17f)
                lineTo(9f, 17f)
                moveTo(12f, 12f)
                lineTo(15f, 12f)
            }
        }.build()
    }

    val Shield: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shield",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 11f)
                arcTo(10f, 10f, 0f, false, false, 12f, 21f)
                arcTo(10f, 10f, 0f, false, false, 20f, 11f)
                lineTo(20f, 5f)
                close()
            }
        }.build()
    }

    val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(12f, 16f)
                arcTo(4f, 4f, 0f, true, false, 12f, 8f)
                arcTo(4f, 4f, 0f, true, false, 12f, 16f)
                close()
                moveTo(12f, 2f)
                lineTo(12f, 4f)
                moveTo(12f, 20f)
                lineTo(12f, 22f)
                moveTo(2f, 12f)
                lineTo(4f, 12f)
                moveTo(20f, 12f)
                lineTo(22f, 12f)
            }
        }.build()
    }

    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "Moon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(21f, 12.79f)
                arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
                arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
                close()
            }
        }.build()
    }

    val Upload: ImageVector by lazy {
        ImageVector.Builder(
            name = "Upload",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(21f, 15f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                lineTo(3f, 15f)
                moveTo(17f, 8f)
                lineTo(12f, 3f)
                lineTo(7f, 8f)
                moveTo(12f, 3f)
                lineTo(12f, 15f)
            }
        }.build()
    }

    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Unspecified),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(23f, 4f)
                lineTo(23f, 10f)
                lineTo(17f, 10f)
                moveTo(1f, 20f)
                lineTo(1f, 14f)
                lineTo(7f, 14f)
                moveTo(3.51f, 9f)
                arcTo(9f, 9f, 0f, false, true, 18.36f, 5.64f)
                lineTo(23f, 10f)
                moveTo(1f, 14f)
                lineTo(5.64f, 18.36f)
                arcTo(9f, 9f, 0f, false, false, 20.49f, 15f)
            }
        }.build()
    }
}
