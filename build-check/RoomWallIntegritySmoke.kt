import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "wall-check", widthMm = 4200, depthMm = 3100, heightMm = 2700)
    val wallElements = listOf(
        EngineeringElement("n", "Розетка север", EngineeringKind.OUTLET, Vec3Mm(1200, 0, 300), WallSide.NORTH),
        EngineeringElement("s", "Вывод юг", EngineeringKind.WATER, Vec3Mm(800, room.depthMm, 500), WallSide.SOUTH),
        EngineeringElement("e", "Газ восток", EngineeringKind.GAS, Vec3Mm(room.widthMm, 1700, 900), WallSide.EAST),
        EngineeringElement("w", "Вывод запад", EngineeringKind.PIPE, Vec3Mm(0, 2100, 600), WallSide.WEST)
    )
    check(GeometryEngine.validateRoom(room.copy(engineering = wallElements)).isEmpty())
    check(GeometryEngine.wallPosition(room, WallSide.NORTH, 1200, 300) == Vec3Mm(1200, 0, 300))
    check(GeometryEngine.wallPosition(room, WallSide.SOUTH, 800, 500) == Vec3Mm(800, room.depthMm, 500))
    check(GeometryEngine.wallPosition(room, WallSide.EAST, 1700, 900) == Vec3Mm(room.widthMm, 1700, 900))
    check(GeometryEngine.wallPosition(room, WallSide.WEST, 2100, 600) == Vec3Mm(0, 2100, 600))
    check(runCatching { GeometryEngine.wallPosition(room, WallSide.EAST, room.depthMm + 1, 100) }.isFailure)
    check(GeometryEngine.validateRoom(room.copy(engineering = listOf(wallElements[0].copy(position = Vec3Mm(1200, room.depthMm, 300))))).any {
        it.code == GeometryIssueCode.INVALID_ENGINEERING && it.objectId == "n"
    })
    check(GeometryEngine.validateRoom(room.copy(engineering = listOf(wallElements[2].copy(position = Vec3Mm(0, 1700, 900))))).any {
        it.code == GeometryIssueCode.INVALID_ENGINEERING && it.objectId == "e"
    })
    val interior = EngineeringElement("i", "Напольный вывод", EngineeringKind.WATER, Vec3Mm(2000, 1500, 200))
    check(GeometryEngine.validateRoom(room.copy(engineering = listOf(interior))).isEmpty())
    val duplicate = room.copy(openings = listOf(WallOpening("same", WallSide.NORTH, 100, 800, 0, 2100, OpeningKind.DOOR)),
        engineering = listOf(wallElements[0].copy(id = "same")))
    check(GeometryEngine.validateRoom(duplicate).any { it.code == GeometryIssueCode.DUPLICATE_ROOM_ELEMENT_ID && it.objectId == "same" })
    check(GeometryEngine.validateRoom(room.copy(engineering = listOf(wallElements[0].copy(diameterMm = Int.MAX_VALUE)))).any {
        it.code == GeometryIssueCode.INVALID_ENGINEERING
    })
    println("RoomWallIntegritySmoke: wall-side coordinates, interior markers, duplicate IDs and diameter limits passed")
}
