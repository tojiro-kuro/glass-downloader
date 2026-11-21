package io.glassdown.app.ui.glass

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

object GlassPalette {
    val Obsidian = Color(0xFF0B0C0E)
    val ObsidianSoft = Color(0xFF121316)
    val Pearl = Color(0xFFF5F6F8)
    val Titanium = Color(0xFF9CA3AF)
    val ClearBase = Color(0xFF1A1C20)
    val RegularBase = Color(0xFF15171C)
    val Danger = Color(0xFFD1D5DB)
}

enum class GlassVariant(val base: Color, val alpha: Float) {
    Clear(GlassPalette.ClearBase, 0.50f),
    Regular(GlassPalette.RegularBase, 0.90f),
}

fun staticEdgeReflection(): Brush = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.24f),
    0.45f to Color.White.copy(alpha = 0.06f),
    1.0f to Color.White.copy(alpha = 0.04f),
)

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun Modifier.glassEffect(
    variant: GlassVariant = GlassVariant.Clear,
    shape: Shape = RoundedCornerShape(18.dp),
    interactive: Boolean = false,
): Modifier {
    val reduceMotion = rememberReduceMotion()
    val pressOffset = remember { mutableStateOf<Offset?>(null) }
    val pressed = pressOffset.value != null
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive && !reduceMotion) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 394f),
        label = "glassPressScale",
    )
    val fill = variant.base.copy(alpha = variant.alpha)

    var modifier = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(color = fill, shape = shape)
        .drawBehind {
            val offset = pressOffset.value
            if (offset != null && interactive) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = offset,
                        radius = size.minDimension * 0.62f,
                    ),
                )
            }
        }
        .border(width = 0.8.dp, brush = staticEdgeReflection(), shape = shape)

    if (interactive) {
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    pressOffset.value = if (change.pressed) change.position else null
                }
            }
        }
    }

    return modifier
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Regular,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassEffect(variant = variant, shape = shape),
        content = content,
    )
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    variant: GlassVariant = GlassVariant.Clear,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassEffect(variant = variant, shape = shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
