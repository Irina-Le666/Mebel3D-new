import ru.mebel3d.app.core.*

fun main() {
    var n = 0
    fun ck(v: Boolean, msg: String) { check(v) { msg }; n++ }
    val room = RoomModel(id="r", widthMm=5000, depthMm=4000, heightMm=2700,
        openings=listOf(WallOpening("door", WallSide.NORTH, 3500, 900, 0, 2100, OpeningKind.DOOR),
            WallOpening("window", WallSide.EAST, 1000, 1200, 900, 1200, OpeningKind.WINDOW)),
        engineering=listOf(EngineeringElement("water", "Вода", EngineeringKind.WATER, Vec3Mm(4000, 3000, 500))))
    val project = ProjectModel("p", "P", listOf(room), activeRoomId="r")
    val item = FurnitureItem("a", "Шкаф", Size3Mm(600, 500, 2200), Vec3Mm(1000,1000), roomId="r")
    val north = FurniturePlacementEngine.preview(project, FurniturePlacementRequest(item,"r",Vec3Mm(1500,100),desiredRotationDeg=180,wallSnapToleranceMm=200))
    ck(north.isValid && north.snappedToWall == WallSide.NORTH, "north wall snapping")
    val northCandidate = north.candidate ?: error("north placement candidate missing")
    ck(northCandidate.rotationDeg == 180, "preserves closest 180-degree orientation")
    ck(northCandidate.position.y == 250, "north wall footprint alignment")
    val east = FurniturePlacementEngine.preview(project, FurniturePlacementRequest(item.copy(id="b"),"r",Vec3Mm(4800,2000),desiredRotationDeg=270,wallSnapToleranceMm=300,gridMm=0))
    ck(east.snappedToWall == WallSide.EAST && east.candidate!!.rotationDeg == 270, "east wall keeps nearest 270 orientation")
    val below = FurniturePlacementEngine.preview(project,FurniturePlacementRequest(item.copy(id="c"),"r",Vec3Mm(1000,1000,-1),wallSnapToleranceMm=0,gridMm=0))
    ck(below.issues.any { it.code == "BELOW_FLOOR" }, "negative elevation rejected")
    val window = FurniturePlacementEngine.preview(project,FurniturePlacementRequest(item.copy(id="d"),"r",Vec3Mm(4900,1600),wallSnapToleranceMm=0,gridMm=0))
    ck(window.issues.any { it.code == "OPENING_CONFLICT" && it.relatedId == "window" }, "window opening conflict")
    val high = FurniturePlacementEngine.preview(project,FurniturePlacementRequest(item.copy(id="e"),"r",Vec3Mm(1000,1000,400),wallSnapToleranceMm=0,gridMm=0))
    ck(high.isValid, "valid elevated placement")
    val committed = FurniturePlacementEngine.apply(project, high)
    ck(committed.furniture.size == 1 && project.furniture.isEmpty(), "atomic commit")
    ck(runCatching { FurniturePlacementEngine.apply(committed, high) }.isFailure, "stale duplicate preview rejected")
    val missing = FurniturePlacementEngine.preview(project,FurniturePlacementRequest(item,"missing",Vec3Mm(0,0)))
    ck(missing.issues.single().code == "ROOM_NOT_FOUND", "missing room")
    println("FurniturePlacementAdvancedSmoke: $n checks passed")
}
