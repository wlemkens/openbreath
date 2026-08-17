package io.github.wlemkens.openbreath

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hue, saturation and brightness, because picking a colour by eye means moving one thing at a
 * time and on RGB every axis drags the other two along with it.
 *
 * This was `android.graphics.Color.colorToHSV`, which is the last thing that kept the settings
 * screen on one platform. It is arithmetic and always was — the same arithmetic every graphics
 * library has — so it lives here now and both platforms get the same colour for the same slider
 * positions rather than each rounding it their own way.
 *
 * Hue is degrees 0..360 (360 folds back to 0), saturation and brightness are 0..1. The stored
 * value is opaque ARGB, which is what [Config.cueColor] has always held.
 */
internal fun argbToHsv(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val span = max - min

    // grey has no hue to speak of, and 0 is the conventional answer rather than a meaningful one
    val hue = when {
        span == 0f -> 0f
        max == r -> 60f * (((g - b) / span) % 6f)
        max == g -> 60f * (((b - r) / span) + 2f)
        else -> 60f * (((r - g) / span) + 4f)
    }

    return floatArrayOf(
        if (hue < 0f) hue + 360f else hue,
        if (max == 0f) 0f else span / max,
        max,
    )
}

/** The inverse of [argbToHsv], always fully opaque — the cue colour has no alpha to carry. */
internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    val chroma = v * s
    val second = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val match = v - chroma

    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(chroma, second, 0f)
        1 -> Triple(second, chroma, 0f)
        2 -> Triple(0f, chroma, second)
        3 -> Triple(0f, second, chroma)
        4 -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }

    fun byte(component: Float) = ((component + match) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}
