package ru.mebel3d.app.core

import kotlin.math.roundToInt

/** Immutable baseline for direct manipulation of one cabinet board on the room canvas. */
object AssemblyPartGestureTransform {
    data class Gesture(
        val assemblyId: String,
        val partId: String,
        val initialAngleDeg: Int,
        val initialHeightMm: Int,
        val startVectorX: Float = 0f,
        val startVectorY: Float = 0f
    )

    fun begin(assembly: FurnitureAssembly, part: FurniturePart, pointerX: Float, pointerY: Float): Gesture {
        require(pointerX.isFinite() && pointerY.isFinite())
        require(pointerX != 0f || pointerY != 0f) { "Указатель находится в центре детали" }
        require(assembly.parts.any { it.id == part.id }) { "Деталь не принадлежит сборке" }
        return Gesture(assembly.id, part.id, FurnitureTransformMath.normalize360(part.rotationDeg),
            part.size.height, pointerX, pointerY)
    }

    fun angleAt(gesture: Gesture, pointerX: Float, pointerY: Float, snapStepDeg: Int = 1): Int {
        require(snapStepDeg in 1..90) { "Шаг фиксации угла должен быть от 1 до 90 градусов" }
        require(pointerX.isFinite() && pointerY.isFinite())
        val delta = FurnitureTransformMath.angularDelta(
            gesture.startVectorX, gesture.startVectorY, pointerX, pointerY)
        return FurnitureTransformMath.normalize360(
            ((gesture.initialAngleDeg + delta) / snapStepDeg).roundToInt() * snapStepDeg)
    }

    fun heightAt(gesture: Gesture, totalDragYpx: Float, scalePxPerMm: Float,
                 is3d: Boolean, minHeightMm: Int = 1, maxHeightMm: Int, cameraPitchDeg: Float = 32f): Int {
        require(totalDragYpx.isFinite() && scalePxPerMm.isFinite() && scalePxPerMm > 0f)
        require(minHeightMm > 0 && maxHeightMm >= minHeightMm)
        val delta = FurnitureTransformMath.heightDeltaFromScreen(totalDragYpx, scalePxPerMm, is3d, cameraPitchDeg)
        return (gesture.initialHeightMm + delta).coerceIn(minHeightMm, maxHeightMm)
    }
}
