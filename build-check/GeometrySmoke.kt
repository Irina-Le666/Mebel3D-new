import ru.mebel3d.app.core.*
fun main() {
    check(GeometryEngine.rotatedFootprint(Size3Mm(2400,900,850),90) == (900 to 2400))
    val room = GeometryEngine.addOpening(RoomModel(), WallOpening("w1",WallSide.NORTH,900,1400,900,1400,OpeningKind.WINDOW))
    check(room.openings.size == 1 && GeometryEngine.validateRoom(room).isEmpty())
    val outlet = EngineeringElement("socket-1","Розетка",EngineeringKind.OUTLET,Vec3Mm(1200,0,300),WallSide.NORTH)
    check(GeometryEngine.addEngineeringElement(room,outlet).engineering.size == 1)
    val bad = room.copy(engineering=listOf(outlet.copy(position=Vec3Mm(6000,0,300))))
    check(GeometryEngine.validateRoom(bad).any { it.code == GeometryIssueCode.INVALID_ENGINEERING })
    check(GeometryEngine.resizeRoom(room,6000,4000,2800).widthMm == 6000)
    val extremeRoom = RoomModel(id = "extreme", widthMm = Int.MAX_VALUE, depthMm = Int.MAX_VALUE, heightMm = Int.MAX_VALUE)
    val highFurniture = FurnitureItem("high", "Высокий объект", Size3Mm(100, 100, 1000),
        Vec3Mm(1000, 1000, Int.MAX_VALUE - 500), roomId = "extreme")
    check(GeometryEngine.validateFurniture(extremeRoom, listOf(highFurniture)).any { it.code == GeometryIssueCode.ABOVE_CEILING })
    val overflowingOpening = WallOpening("overflow", WallSide.NORTH, Int.MAX_VALUE - 5, 100, 0, 100, OpeningKind.WINDOW)
    check(GeometryEngine.validateRoom(extremeRoom.copy(openings = listOf(overflowingOpening))).any { it.code == GeometryIssueCode.INVALID_OPENING })
    check(GeometryEngine.wallSegments(extremeRoom.copy(openings = listOf(overflowingOpening)), WallSide.NORTH).single().lengthMm == Int.MAX_VALUE)

    val round = FurnitureItem("round-1", "Круглый стол", Size3Mm(1000, 1000, 750),
        Vec3Mm(2500, 1750, 0), footprintShape = FurnitureFootprintShape.CIRCLE)
    check(GeometryEngine.validateFurniture(room, listOf(round)).isEmpty())
    val overlappingRound = round.copy(id = "round-2", position = Vec3Mm(3300, 1750, 0))
    check(GeometryEngine.validateFurniture(room, listOf(round, overlappingRound)).any { it.code == GeometryIssueCode.COLLISION })
    val clearRound = round.copy(id = "round-3", position = Vec3Mm(3700, 1750, 0))
    check(GeometryEngine.validateFurniture(room, listOf(round, clearRound)).none { it.code == GeometryIssueCode.COLLISION })
    val circleNearWall = round.copy(position = Vec3Mm(400, 1750, 0))
    check(GeometryEngine.validateFurniture(room, listOf(circleNearWall)).any { it.code == GeometryIssueCode.OUT_OF_ROOM })
    check(round.copy(footprintShape = FurnitureFootprintShape.RECTANGLE).footprintShape == FurnitureFootprintShape.RECTANGLE)
    println("12 geometry smoke checks passed")
}
