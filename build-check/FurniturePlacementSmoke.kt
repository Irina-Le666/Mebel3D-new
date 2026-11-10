import ru.mebel3d.app.core.*
fun main() {
    var checks = 0
    fun ck(v: Boolean, m: String) { check(v) { m }; checks++ }
    val room = RoomModel(id="r", widthMm=5000, depthMm=4000, heightMm=2700,
        openings=listOf(WallOpening("door", WallSide.NORTH, 3500, 900, 0, 2100, OpeningKind.DOOR)),
        engineering=listOf(EngineeringElement("water", "Вода", EngineeringKind.WATER, Vec3Mm(4000, 3900, 500), WallSide.SOUTH)))
    val p = ProjectModel("p", "Проект", listOf(room), activeRoomId="r")
    val sofa = FurnitureItem("s", "Диван", Size3Mm(1800, 800, 900), Vec3Mm(1000, 1000), roomId="r")
    val snapped = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa, "r", Vec3Mm(2000, 200), wallSnapToleranceMm=300))
    ck(snapped.isValid && snapped.snappedToWall == WallSide.NORTH, "north wall snap")
    ck(snapped.candidate!!.position.y == 400, "snap aligns furniture footprint, not center")
    ck(p.furniture.isEmpty(), "preview does not mutate project")
    val committed = FurniturePlacementEngine.apply(p, snapped)
    ck(committed.furniture.size == 1 && p.furniture.isEmpty(), "atomic commit")
    ck(runCatching { FurniturePlacementEngine.apply(committed, snapped) }.isFailure, "duplicate ID rejected")
    val blocked = FurniturePlacementEngine.preview(committed, FurniturePlacementRequest(sofa.copy(id="s2"), "r", snapped.candidate!!.position, wallSnapToleranceMm=0, gridMm=0))
    ck(blocked.issues.any { it.code == "FURNITURE_COLLISION" || it.code == "COLLISION" }, "collision detected")
    val opening = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa, "r", Vec3Mm(3500, 200), wallSnapToleranceMm=300))
    ck(opening.issues.any { it.code == "OPENING_CONFLICT" }, "door clearance detected")
    val engineering = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa.copy(id="water-item"), "r", Vec3Mm(4000, 3900), wallSnapToleranceMm=0, gridMm=0))
    ck(engineering.issues.any { it.code == "ENGINEERING_CONFLICT" }, "engineering conflict detected")
    val outside = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa.copy(id="out"), "r", Vec3Mm(4900, 2000), wallSnapToleranceMm=0, gridMm=0))
    ck(outside.issues.any { it.code == "OUT_OF_ROOM" }, "out of room rejected")
    val noRoom = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa, "missing", Vec3Mm(0,0)))
    ck(noRoom.issues.single().code == "ROOM_NOT_FOUND", "missing room rejected")
    val grid = FurniturePlacementEngine.preview(p, FurniturePlacementRequest(sofa, "r", Vec3Mm(1033, 1777), wallSnapToleranceMm=0, gridMm=50))
    val gridCandidate = grid.candidate ?: error("grid placement candidate missing")
    ck(gridCandidate.position.x == 1050 && gridCandidate.position.y == 1800, "grid snapping")
    println("FurniturePlacementSmoke: $checks checks passed")
}
