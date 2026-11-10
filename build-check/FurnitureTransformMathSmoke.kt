import ru.mebel3d.app.core.FurnitureTransformMath

fun main() {
    check(FurnitureTransformMath.angularDelta(1f, 0f, 0f, 1f) == 90f)
    check(FurnitureTransformMath.angularDelta(-1f, 0f, 0f, -1f) == 90f)
    check(FurnitureTransformMath.angularDelta(0f, -1f, 1f, 0f) == 90f)
    check(FurnitureTransformMath.angleFromGesture(350, 1f, 0f, 0f, 1f) == 75) // 350 + 90, snapped to 15°
    check(FurnitureTransformMath.angleFromGesture(0, 1f, 0f, 0.9659f, 0.2588f) == 15)
    check(FurnitureTransformMath.heightDeltaFromScreen(-0.58f * kotlin.math.cos(Math.toRadians(32.0)).toFloat() * 100f, 1f, true, 32f) == 100)
    check(FurnitureTransformMath.heightDeltaFromScreen(100f, 1f, false) == -100)
    check(FurnitureTransformMath.normalize360(-15) == 345)
    check(FurnitureTransformMath.heightDeltaFromScreen(-0.58f * kotlin.math.cos(Math.toRadians(85.0)).toFloat() * 100f, 1f, true, 85f) == 100)
    check(runCatching { FurnitureTransformMath.heightDeltaFromScreen(1f, 0f, false) }.isFailure)
    check(runCatching { FurnitureTransformMath.heightDeltaFromScreen(1f, 1f, true, 90f) }.isFailure)
    check(runCatching { FurnitureTransformMath.angleFromGesture(0, 0f, 0f, 1f, 1f) }.isFailure)
    println("FurnitureTransformMathSmoke: 10 checks passed")
}
