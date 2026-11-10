package ru.mebel3d.app.core

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Converts a screen drag into local furniture width/depth deltas. */
object FurnitureResizeMath {
    /** Applies local width/depth deltas and enforces the editor's minimum footprint. */
    fun resizedDimensions(currentWidthMm: Int, currentDepthMm: Int, deltaWidthMm: Int, deltaDepthMm: Int, minimumMm: Int = 100): Pair<Int, Int> {
        require(currentWidthMm > 0 && currentDepthMm > 0) { "Исходные размеры должны быть положительными" }
        require(minimumMm > 0) { "Минимальный размер должен быть положительным" }
        return (currentWidthMm.toLong() + deltaWidthMm).coerceAtLeast(minimumMm.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() to
            (currentDepthMm.toLong() + deltaDepthMm).coerceAtLeast(minimumMm.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun fromScreenDelta(
        dragXpx: Float,
        dragYpx: Float,
        scalePxPerMm: Float,
        is3d: Boolean,
        cameraYawDeg: Float,
        furnitureRotationDeg: Int,
        cameraPitchDeg: Float = 32f
    ): Pair<Int, Int> {
        require(scalePxPerMm > 0f && scalePxPerMm.isFinite()) { "Некорректный масштаб сцены" }
        val sx: Float
        val sy: Float
        if (is3d) {
            val yaw = Math.toRadians(cameraYawDeg.toDouble())
            val horizontal = dragXpx / (0.58f * scalePxPerMm)
            require(cameraPitchDeg in 5f..85f && cameraPitchDeg.isFinite()) { "Некорректный угол камеры" }
            val verticalScale = 0.58f * kotlin.math.sin(Math.toRadians(cameraPitchDeg.toDouble())).toFloat()
            val vertical = dragYpx / (verticalScale * scalePxPerMm)
            sx = (horizontal * cos(yaw) + vertical * sin(yaw)).toFloat()
            sy = (-horizontal * sin(yaw) + vertical * cos(yaw)).toFloat()
        } else {
            sx = dragXpx / scalePxPerMm
            sy = dragYpx / scalePxPerMm
        }
        // Convert room-axis movement into the furniture's own width/depth axes.
        val angle = Math.toRadians(furnitureRotationDeg.toDouble())
        val localX = sx * cos(angle) + sy * sin(angle)
        val localY = -sx * sin(angle) + sy * cos(angle)
        return localX.roundToInt() to localY.roundToInt()
    }
}
