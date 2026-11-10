package ru.mebel3d.app.core

import kotlin.math.atan2
import kotlin.math.roundToInt

/** Gesture math for free furniture rotation and vertical resizing on the room canvas. */
object FurnitureTransformMath {
    /** Signed angular difference in [-180, 180], stable across the 0/360 boundary. */
    fun angularDelta(startX: Float, startY: Float, currentX: Float, currentY: Float): Float {
        require(listOf(startX, startY, currentX, currentY).all(Float::isFinite)) { "Некорректные координаты жеста" }
        require((startX != 0f || startY != 0f) && (currentX != 0f || currentY != 0f)) { "Точка вращения совпадает с центром" }
        val start = Math.toDegrees(atan2(startY.toDouble(), startX.toDouble()))
        val current = Math.toDegrees(atan2(currentY.toDouble(), currentX.toDouble()))
        return normalizeSigned((current - start).toFloat())
    }

    fun angleFromGesture(startAngleDeg: Int, startX: Float, startY: Float, currentX: Float, currentY: Float,
                         snapStepDeg: Int = 15): Int {
        require(snapStepDeg in 1..90) { "Шаг фиксации угла должен быть от 1 до 90 градусов" }
        val raw = startAngleDeg + angularDelta(startX, startY, currentX, currentY)
        return normalize360((raw / snapStepDeg).roundToInt() * snapStepDeg)
    }

    /** Converts vertical screen movement to a height delta. Dragging upward increases height. */
    fun heightDeltaFromScreen(dragYpx: Float, scalePxPerMm: Float, is3d: Boolean, cameraPitchDeg: Float = 32f): Int {
        require(scalePxPerMm > 0f && scalePxPerMm.isFinite()) { "Некорректный масштаб сцены" }
        require(dragYpx.isFinite()) { "Некорректное перемещение" }
        require(!is3d || (cameraPitchDeg in 5f..85f && cameraPitchDeg.isFinite())) { "Некорректный угол камеры" }
        val verticalProjection = if (is3d) 0.58f * kotlin.math.cos(Math.toRadians(cameraPitchDeg.toDouble())).toFloat() else 1f
        return (-dragYpx / (scalePxPerMm * verticalProjection)).roundToInt()
    }

    fun normalize360(degrees: Int): Int = ((degrees % 360) + 360) % 360
    fun normalizeSigned(degrees: Float): Float {
        var value = degrees % 360f
        if (value > 180f) value -= 360f
        if (value < -180f) value += 360f
        return value
    }
}
