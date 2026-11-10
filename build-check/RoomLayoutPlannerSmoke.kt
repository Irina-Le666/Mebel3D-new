import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    val room = RoomModel(id="r", widthMm=3000, depthMm=2400, heightMm=2600)
    val project = ProjectModel("p", "Тест", rooms=listOf(room), activeRoomId="r")
    val specs = listOf(
        FurnitureModuleSpec("a", "Тумба 600", FurnitureRoomCategory.KITCHEN, FurnitureModuleKind.BASE_CABINET, 600, 560, 900, countertop=CountertopKind.INDIVIDUAL),
        FurnitureModuleSpec("b", "Тумба 450", FurnitureRoomCategory.KITCHEN, FurnitureModuleKind.DRAWER_BASE, 450, 560, 900, countertop=CountertopKind.INDIVIDUAL)
    )
    val request = RoomLayoutRequest("r", WallSide.NORTH, specs, minimumAisleMm=900)
    val result = RoomLayoutPlanner.plan(project, request)
    check(result.placements.size == 2); checks++
    check(result.usedWallLengthMm == 1052); checks++
    check(result.placements[0].center.y == 280); checks++
    check(result.placements[1].center.x > result.placements[0].center.x); checks++
    check(result.issues.none { it.code == "FURNITURE_COLLISION" || it.code == "OUT_OF_ROOM" }); checks++
    val applied = RoomLayoutPlanner.apply(project, result, "kitchen")
    check(applied.assemblies.size == 2); checks++
    check(applied.assemblies.all { it.parts.isNotEmpty() }); checks++
    val northAssembly = applied.assemblies.first()
    check(northAssembly.position == Vec3Mm(600, 560, 0)) { "north assembly local origin maps to front corner while rear panel touches north wall" }; checks++
    val northBack = northAssembly.parts.first { it.name == "Задняя стенка" }
    check(northAssembly.position.y + northBack.localPosition.y + northBack.size.depth / 2 - northBack.size.depth / 2 == 0) { "north rear panel lies on north wall plane" }; checks++
    check(GeometryEngine.validateAssembly(northAssembly).isEmpty()) { "applied north assembly remains valid" }; checks++
    check(ProjectValidator.validate(applied).isEmpty()) { "applied north layout passes whole-project validation" }; checks++

    val blockedRoom = room.copy(openings=listOf(WallOpening("door", WallSide.NORTH, 400, 800, 0, 2100, OpeningKind.DOOR)))
    val blocked = RoomLayoutPlanner.plan(project.copy(rooms=listOf(blockedRoom)), request)
    check(blocked.issues.any { it.code == "OPENING_CONFLICT" }); checks++
    val shortRoom = room.copy(widthMm=900)
    val short = RoomLayoutPlanner.plan(project.copy(rooms=listOf(shortRoom)), request)
    check(short.issues.any { it.code == "INSUFFICIENT_WALL" }); checks++
    val existing = FurnitureItem("sofa", "Диван", Size3Mm(1000, 700, 800), Vec3Mm(600, 600), roomId="r")
    val collision = RoomLayoutPlanner.plan(project.copy(furniture=listOf(existing)), request)
    check(collision.issues.any { it.code == "FURNITURE_COLLISION" }); checks++
    val east = RoomLayoutPlanner.plan(project, request.copy(wallSide=WallSide.EAST))
    check(east.placements.first().rotationDeg == 270); checks++
    val eastApplied = RoomLayoutPlanner.apply(project, east, "east")
    val eastAssembly = eastApplied.assemblies.first()
    val eastBack = eastAssembly.parts.first { it.name == "Задняя стенка" }
    check(eastAssembly.position.x + eastBack.localPosition.x + eastBack.size.width / 2 + eastBack.size.depth / 2 == 3000) { "east rear panel lies on east wall plane" }; checks++
    check(eastAssembly.parts.first { it.name == "Боковина левая" }.rotationDeg == 270) { "east carcass board rotates with module" }; checks++
    check(ProjectValidator.validate(eastApplied).isEmpty()) { "applied east layout passes whole-project validation" }; checks++
    val west = RoomLayoutPlanner.plan(project, request.copy(wallSide=WallSide.WEST))
    check(west.placements.first().rotationDeg == 90 && west.placements.first().center.x == 280); checks++
    val westApplied = RoomLayoutPlanner.apply(project, west, "west")
    val westAssembly = westApplied.assemblies.first()
    val westBack = westAssembly.parts.first { it.name == "Задняя стенка" }
    check(westAssembly.position.x + westBack.localPosition.x + westBack.size.width / 2 - westBack.size.depth / 2 == 0) { "west rear panel lies on west wall plane" }; checks++
    check(ProjectValidator.validate(westApplied).isEmpty()) { "applied west layout passes whole-project validation" }; checks++
    val south = RoomLayoutPlanner.plan(project, request.copy(wallSide=WallSide.SOUTH))
    check(south.placements.first().rotationDeg == 0 && south.placements.first().center.y == 2120); checks++
    val southApplied = RoomLayoutPlanner.apply(project, south, "south")
    val southAssembly = southApplied.assemblies.first()
    val southBack = southAssembly.parts.first { it.name == "Задняя стенка" }
    check(southAssembly.position.y + southBack.localPosition.y + southBack.size.depth == 2400) { "south rear panel lies on south wall plane" }; checks++
    check(ProjectValidator.validate(southApplied).isEmpty()) { "applied south layout passes whole-project validation" }; checks++
    val noDoorAvoidance = RoomLayoutPlanner.plan(project.copy(rooms = listOf(blockedRoom)), request.copy(avoidDoors = false))
    check(noDoorAvoidance.issues.none { it.code == "OPENING_CONFLICT" }); checks++
    val engineeringRoom = room.copy(engineering = listOf(EngineeringElement("gas", "Газ", EngineeringKind.GAS, Vec3Mm(300, 200, 500), null, 30)))
    val engineeringBlocked = RoomLayoutPlanner.plan(project.copy(rooms = listOf(engineeringRoom)), request.copy(modules = listOf(specs.first()), startOffsetMm = 0))
    check(engineeringBlocked.issues.any { it.code == "ENGINEERING_CONFLICT" }); checks++
    val invalid = RoomLayoutPlanner.plan(project, request.copy(moduleGapMm=-1))
    check(invalid.issues.any { it.code == "INVALID_REQUEST" }); checks++

    val narrowRoom = room.copy(depthMm = 1200)
    val narrowRequest = request.copy(modules = listOf(specs.first()), minimumAisleMm = 900)
    val narrow = RoomLayoutPlanner.plan(project.copy(rooms = listOf(narrowRoom)), narrowRequest)
    check(narrow.issues.any { it.code == "NARROW_AISLE" }); checks++
    check(!narrow.isValid); checks++
    check(runCatching { RoomLayoutPlanner.apply(project, narrow, "bad") }.isFailure); checks++

    val hugeGap = RoomLayoutPlanner.plan(project, request.copy(moduleGapMm = Int.MAX_VALUE))
    check(hugeGap.issues.any { it.code == "INSUFFICIENT_WALL" || it.code == "INVALID_REQUEST" }); checks++
    check(!hugeGap.isValid); checks++

    val overflowOpeningRoom = room.copy(openings = listOf(
        WallOpening("bad-window", WallSide.NORTH, 100, Int.MAX_VALUE, 800, 1000, OpeningKind.WINDOW)
    ))
    val overflowOpening = RoomLayoutPlanner.plan(project.copy(rooms = listOf(overflowOpeningRoom)), request.copy(modules = listOf(specs.first()), startOffsetMm = 0))
    check(overflowOpening.issues.any { it.code == "OPENING_CONFLICT" } || overflowOpening.issues.any { it.code == "INSUFFICIENT_WALL" }); checks++

    val excessiveWallGap = RoomLayoutPlanner.plan(project, request.copy(modules = listOf(specs.first()), wallGapMm = Int.MAX_VALUE))
    check(excessiveWallGap.issues.any { it.code == "OUT_OF_ROOM" }); checks++
    check(!excessiveWallGap.isValid); checks++
    println("RoomLayoutPlannerSmoke: $checks checks passed")
}
