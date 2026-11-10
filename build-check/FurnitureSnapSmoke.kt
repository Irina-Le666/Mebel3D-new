import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val cabinet = FurnitureItem("a", "Шкаф", Size3Mm(600, 500, 2100), Vec3Mm(335, 1000), roomId = "r")
    val table = FurnitureItem("b", "Стол", Size3Mm(800, 600, 750), Vec3Mm(2000, 2000), roomId = "r")
    val p = ProjectModel("p", "Тест", listOf(room), listOf(cabinet, table), activeRoomId = "r")

    val wallSnap = ProjectEditor.snapFurnitureToNearby(p, "a", toleranceMm = 100)
    check(wallSnap.snapped)
    check(wallSnap.target == "Западная стена")
    check(wallSnap.project.furniture.first { it.id == "a" }.position.x == 300)

    val nearTable = p.copy(furniture = p.furniture.map {
        if (it.id == "a") it.copy(position = Vec3Mm(1250, 2000)) else it
    })
    val neighborSnap = ProjectEditor.snapFurnitureToNearby(nearTable, "a", toleranceMm = 100)
    check(neighborSnap.snapped)
    check(neighborSnap.target == "Слева от Стол")
    check(neighborSnap.project.furniture.first { it.id == "a" }.position.x == 1300)

    val tooFar = ProjectEditor.snapFurnitureToNearby(p, "a", toleranceMm = 10)
    check(!tooFar.snapped)
    check(tooFar.project == p)

    val invalid = p.copy(furniture = p.furniture.map {
        if (it.id == "a") it.copy(position = Vec3Mm(310, 1000)) else it
    })
    val collisionCheck = ProjectEditor.snapFurnitureToNearby(invalid, "a", toleranceMm = 100)
    check(collisionCheck.snapped)
    check(GeometryEngine.validateFurniture(room, collisionCheck.project.furniture).isEmpty())

    check(runCatching { ProjectEditor.snapFurnitureToNearby(p, "a", -1) }.isFailure)
    println("FurnitureSnapSmoke: 8 checks passed")
}
