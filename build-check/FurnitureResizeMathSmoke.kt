import ru.mebel3d.app.core.FurnitureResizeMath

fun main() {
    val topRight = FurnitureResizeMath.fromScreenDelta(100f, 0f, 1f, false, 0f, 0)
    check(topRight.first == 100 && topRight.second == 0)

    val rotated90 = FurnitureResizeMath.fromScreenDelta(0f, 100f, 1f, false, 0f, 90)
    check(rotated90.first == 100 && rotated90.second == 0) { "Поворот мебели должен преобразовывать оси: $rotated90" }

    val inverse = FurnitureResizeMath.fromScreenDelta(100f, 0f, 1f, false, 0f, 90)
    check(inverse.first == 0 && inverse.second == -100) { "Неверная локальная ось: $inverse" }

    val camera = FurnitureResizeMath.fromScreenDelta(58f, 0f, 1f, true, 0f, 0)
    check(camera.first == 100 && camera.second == 0) { "Неверная 3D-проекция: $camera" }

    val cameraYaw = FurnitureResizeMath.fromScreenDelta(0f, 0.58f * kotlin.math.sin(Math.toRadians(32.0)).toFloat() * 100f, 1f, true, 0f, 0, 32f)
    check(cameraYaw.first == 0 && cameraYaw.second == 100) { "Неверная вертикальная 3D-проекция при изометрии: $cameraYaw" }
    val topView = FurnitureResizeMath.fromScreenDelta(0f, 0.58f * kotlin.math.sin(Math.toRadians(85.0)).toFloat() * 100f, 1f, true, 0f, 0, 85f)
    check(topView.first == 0 && topView.second == 100) { "Изменение угла камеры должно сохранять точность изменения глубины: $topView" }
    check(runCatching { FurnitureResizeMath.fromScreenDelta(1f, 1f, 1f, true, 0f, 0, 90f) }.isFailure)

    check(runCatching { FurnitureResizeMath.fromScreenDelta(1f, 1f, 0f, false, 0f, 0) }.isFailure)

    val subMillimetre = FurnitureResizeMath.fromScreenDeltaFloat(0.25f, 0f, 1f, false, 0f, 0)
    check(kotlin.math.abs(subMillimetre.first - 0.25f) < 0.0001f && subMillimetre.second == 0f) {
        "Resize math must preserve fractional millimetres: $subMillimetre"
    }
    var remainderX = 0f
    var accumulated = 0
    repeat(4) {
        val delta = ru.mebel3d.app.core.DragDeltaAccumulator.consume(subMillimetre.first, 0f, remainderX, 0f)
        accumulated += delta.dxMm
        remainderX = delta.remainderX
    }
    check(accumulated == 1 && kotlin.math.abs(remainderX) < 0.0001f) {
        "Four quarter-millimetre resize events must produce exactly 1 mm: $accumulated / $remainderX"
    }

    val resized = FurnitureResizeMath.resizedDimensions(800, 600, 125, -75)
    check(resized == (925 to 525)) { "Размеры после перетаскивания не рассчитаны: $resized" }
    val minimum = FurnitureResizeMath.resizedDimensions(120, 140, -500, -500)
    check(minimum == (100 to 100)) { "Размеры должны ограничиваться минимумом 100 мм: $minimum" }
    val overflowSafe = FurnitureResizeMath.resizedDimensions(Int.MAX_VALUE, 100, 50, 0)
    check(overflowSafe.first == Int.MAX_VALUE) { "Переполнение размера не обработано" }
    check(runCatching { FurnitureResizeMath.resizedDimensions(0, 100, 1, 1) }.isFailure)
    println("FurnitureResizeMathSmoke: 10 checks passed")
}
