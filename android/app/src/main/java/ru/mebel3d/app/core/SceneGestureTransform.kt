package ru.mebel3d.app.core

import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Immutable gesture transaction for direct scene manipulation.
 * Every update is calculated from the original snapshot, not from the previous pointer frame,
 * preventing cumulative rounding drift during long drags.
 */
object SceneGestureTransform {
    enum class Mode { ROTATE, CHANGE_HEIGHT }

    data class FurnitureGesture(
        val itemId: String,
        val mode: Mode,
        val initialAngleDeg: Int,
        val initialHeightMm: Int,
        val startVectorX: Float = 0f,
        val startVectorY: Float = 0f
    )

    fun beginRotation(item: FurnitureItem, pointerXFromCenter: Float, pointerYFromCenter: Float): FurnitureGesture {
        require(pointerXFromCenter.isFinite() && pointerYFromCenter.isFinite())
        require(pointerXFromCenter != 0f || pointerYFromCenter != 0f) { "Указатель находится в центре предмета" }
        return FurnitureGesture(item.id, Mode.ROTATE, item.rotationDeg, item.size.height,
            pointerXFromCenter, pointerYFromCenter)
    }

    fun beginHeightChange(item: FurnitureItem): FurnitureGesture =
        FurnitureGesture(item.id, Mode.CHANGE_HEIGHT, item.rotationDeg, item.size.height)

    /** Returns a normalized angle. snapStepDeg=1 gives free one-degree rotation. */
    fun rotationAt(gesture: FurnitureGesture, pointerXFromCenter: Float, pointerYFromCenter: Float,
                   snapStepDeg: Int = 1): Int {
        require(gesture.mode == Mode.ROTATE)
        require(snapStepDeg in 1..90) { "Шаг фиксации угла должен быть от 1 до 90 градусов" }
        val delta = FurnitureTransformMath.angularDelta(
            gesture.startVectorX, gesture.startVectorY, pointerXFromCenter, pointerYFromCenter)
        return FurnitureTransformMath.normalize360(
            ((gesture.initialAngleDeg + delta) / snapStepDeg).roundToInt() * snapStepDeg
        )
    }

    /** Returns target height from the gesture baseline, clamped to a safe physical range. */
    fun heightAt(gesture: FurnitureGesture, totalDragYpx: Float, scalePxPerMm: Float,
                 is3d: Boolean, roomHeightMm: Int, cameraPitchDeg: Float = 32f): Int {
        require(gesture.mode == Mode.CHANGE_HEIGHT)
        require(roomHeightMm > 0)
        val delta = FurnitureTransformMath.heightDeltaFromScreen(totalDragYpx, scalePxPerMm, is3d, cameraPitchDeg)
        return (gesture.initialHeightMm + delta).coerceIn(100, roomHeightMm)
    }

    /** Apply the computed result through the same validated editor operations as property editing. */
    fun applyRotation(project: ProjectModel, gesture: FurnitureGesture, targetAngleDeg: Int): ProjectModel {
        require(gesture.mode == Mode.ROTATE)
        return ProjectEditor.rotateFurnitureTo(project, gesture.itemId, targetAngleDeg)
    }

    fun applyHeight(project: ProjectModel, gesture: FurnitureGesture, targetHeightMm: Int): ProjectModel {
        require(gesture.mode == Mode.CHANGE_HEIGHT)
        return ProjectEditor.resizeFurnitureHeight(project, gesture.itemId, targetHeightMm)
    }
}
