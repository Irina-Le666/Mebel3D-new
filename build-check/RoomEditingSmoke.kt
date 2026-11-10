import ru.mebel3d.app.core.*

fun main() {
    val base = RoomModel(id = "room-test", widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val resized = GeometryEngine.resizeRoom(base, 5200, 3600, 2800, 150)
    check(resized.widthMm == 5200 && resized.depthMm == 3600 && resized.heightMm == 2800 && resized.wallThicknessMm == 150)
    val withDoor = GeometryEngine.addOpening(resized, WallOpening("door-1", WallSide.NORTH, 500, 900, 0, 2100, OpeningKind.DOOR))
    check(GeometryEngine.validateRoom(withDoor).isEmpty())
    val withWindow = GeometryEngine.addOpening(withDoor, WallOpening("window-1", WallSide.EAST, 800, 1200, 900, 1200, OpeningKind.WINDOW))
    check(GeometryEngine.validateRoom(withWindow).isEmpty())
    check(GeometryEngine.wallSegments(withWindow, WallSide.NORTH).sumOf { it.lengthMm } == withWindow.widthMm)
    check(runCatching { GeometryEngine.addOpening(withWindow, WallOpening("bad-door", WallSide.NORTH, 700, 900, 0, 2100, OpeningKind.DOOR)) }.isFailure)
    check(runCatching { GeometryEngine.resizeRoom(base, -1, 3000, 2700) }.isFailure)
    println("Room editing: 6 assertions passed")
}
