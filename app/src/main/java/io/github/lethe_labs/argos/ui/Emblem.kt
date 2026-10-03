package io.github.lethe_labs.argos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lethe_labs.argos.ui.theme.ArgosColors
import kotlin.math.cos
import kotlin.math.sin

enum class EyeState { OPEN, CLOSED, BLIND }

data class EyeLook(val state: EyeState, val color: Color)

private const val VIEW = 320f
private const val CENTER = 160f
private const val RING = 118f

/**
 * The emblem: one eye per [Watcher] on a gold ring around a central eye, in
 * the same order, starting at the top. Drawn on a 320-unit grid scaled to [size].
 *
 * @param opening 0 to 1, opens the open eyes one after the other, the central
 *        one last. 1 shows them fully open.
 * @param glow strength of the halo behind open eyes, for a slow pulse.
 */
@Composable
fun Emblem(
    ring: List<EyeLook>,
    center: EyeLook,
    size: Dp,
    description: String,
    modifier: Modifier = Modifier,
    opening: Float = 1f,
    glow: Float = 1f,
) {
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val k = this.size.minDimension / VIEW
        fun p(x: Float, y: Float) = Offset(x * k, y * k)

        drawCircle(center.color.copy(alpha = 0.05f), 152f * k, p(CENTER, CENTER))
        drawCircle(ArgosColors.Gold.copy(alpha = 0.55f), RING * k, p(CENTER, CENTER), style = Stroke(1f * k))
        drawCircle(
            ArgosColors.Watch.copy(alpha = 0.25f), 88f * k, p(CENTER, CENTER),
            style = Stroke(1f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 5f * k))),
        )
        drawCircle(ArgosColors.Gold.copy(alpha = 0.35f), 62f * k, p(CENTER, CENTER), style = Stroke(0.8f * k))
        val step = 360.0 / ring.size
        for (i in ring.indices) {
            val a = Math.toRadians(-90.0 + step * i + step / 2)
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            drawLine(
                ArgosColors.Gold.copy(alpha = 0.35f),
                p(CENTER + 62f * c, CENTER + 62f * s), p(CENTER + RING * c, CENTER + RING * s), 0.8f * k,
            )
            drawCircle(ArgosColors.Gold.copy(alpha = 0.7f), 2.6f * k, p(CENTER + 88f * c, CENTER + 88f * s))
        }

        ring.forEachIndexed { i, look ->
            val a = Math.toRadians(-90.0 + step * i)
            val at = p(CENTER + RING * cos(a).toFloat(), CENTER + RING * sin(a).toFloat())
            drawCircle(ArgosColors.Night, 21f * k, at)
            drawCircle(ArgosColors.Line, 21f * k, at, style = Stroke(1f * k))
            drawEye(at, k, look, eyeOpening(opening, i, ring.size), glow)
        }
        drawEye(p(CENTER, CENTER), 3f * k, center, eyeOpening(opening, ring.size, ring.size), glow)
    }
}

private fun eyeOpening(opening: Float, index: Int, count: Int): Float =
    (opening * (count + 1) - index).coerceIn(0f, 1f)

/** One eye centred on [at], drawn on a 28-unit-wide almond scaled by [k]. */
private fun DrawScope.drawEye(at: Offset, k: Float, look: EyeLook, openness: Float, glow: Float) = translate(at.x, at.y) {
    val stroke = Stroke(1.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val lid = Path().apply {
        moveTo(-14f * k, 0f)
        quadraticBezierTo(0f, 9f * k, 14f * k, 0f)
    }
    when {
        look.state == EyeState.BLIND -> {
            drawPath(lid, look.color, style = stroke)
            drawLine(look.color, Offset(-12f * k, -9f * k), Offset(12f * k, 9f * k), 1.6f * k, StrokeCap.Round)
        }
        look.state == EyeState.CLOSED || openness < 0.05f -> {
            drawPath(lid, look.color, style = stroke)
            val lash = Stroke(1.4f * k, cap = StrokeCap.Round)
            listOf(-8f to 4.5f, 0f to 6.8f, 8f to 4.5f).forEach { (x, y) ->
                val dx = if (x < 0) -2f else if (x > 0) 2f else 0f
                drawLine(look.color, Offset(x * k, y * k), Offset((x + dx) * k, (y + 3.6f) * k), lash.width, StrokeCap.Round)
            }
        }
        else -> {
            val o = openness
            drawCircle(look.color.copy(alpha = 0.12f * o * glow), 15f * k, Offset.Zero)
            val almond = Path().apply {
                moveTo(-14f * k, 0f)
                quadraticBezierTo(0f, -11f * k * o, 14f * k, 0f)
                quadraticBezierTo(0f, 11f * k * o, -14f * k, 0f)
                close()
            }
            drawPath(almond, ArgosColors.Night)
            drawPath(almond, look.color, style = stroke)
            drawCircle(look.color, 5.2f * k * o, Offset.Zero)
            drawCircle(ArgosColors.Night, 2.2f * k * o, Offset.Zero)
            drawCircle(Color.White.copy(alpha = 0.8f * o), 1f * k, Offset(1.6f * k, -1.8f * k))
        }
    }
}

/** A watcher's line icon, tinted. */
@Composable
fun WatcherIcon(watcher: Watcher, tint: Color, size: Dp = 22.dp, description: String? = null) {
    val vector = remember(watcher) {
        ImageVector.Builder(watcher.name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(watcher.icon),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
    Icon(vector, contentDescription = description, tint = tint, modifier = Modifier.size(size))
}
