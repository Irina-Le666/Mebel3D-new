package ru.mebel3d.app.core

/**
 * Carries fractional millimetre movement between pointer events.
 *
 * Compose may deliver many small drag deltas; converting each event to Int independently
 * silently discarded every movement smaller than one millimetre. This accumulator emits
 * whole millimetres while retaining the fraction for the next event.
 */
object DragDeltaAccumulator {
    data class Delta(val dxMm: Int, val dyMm: Int, val remainderX: Float, val remainderY: Float)

    fun consume(dxMm: Float, dyMm: Float, remainderX: Float = 0f, remainderY: Float = 0f): Delta {
        require(dxMm.isFinite() && dyMm.isFinite() && remainderX.isFinite() && remainderY.isFinite()) {
            "Перемещение должно быть конечным числом"
        }
        val totalX = dxMm + remainderX
        val totalY = dyMm + remainderY
        val wholeX = totalX.toInt()
        val wholeY = totalY.toInt()
        return Delta(wholeX, wholeY, totalX - wholeX, totalY - wholeY)
    }
}
