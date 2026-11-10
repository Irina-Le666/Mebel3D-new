import ru.mebel3d.app.core.DragDeltaAccumulator

fun main() {
    var rx = 0f
    var ry = 0f
    var movedX = 0
    var movedY = 0
    repeat(10) {
        val delta = DragDeltaAccumulator.consume(0.35f, -0.27f, rx, ry)
        movedX += delta.dxMm
        movedY += delta.dyMm
        rx = delta.remainderX
        ry = delta.remainderY
    }
    check(movedX == 3) { "Positive fractional drag was lost: $movedX" }
    check(movedY == -2) { "Negative fractional drag was lost: $movedY" }
    check(kotlin.math.abs(rx - 0.5f) < 0.0001f)
    check(kotlin.math.abs(ry - -0.7f) < 0.0001f)

    val carried = DragDeltaAccumulator.consume(0.6f, 0.8f, rx, ry)
    check(carried.dxMm == 1)
    check(carried.dyMm == 0)
    check(runCatching { DragDeltaAccumulator.consume(Float.NaN, 0f) }.isFailure)
    println("DragDeltaAccumulatorSmoke: 7 checks passed")
}
