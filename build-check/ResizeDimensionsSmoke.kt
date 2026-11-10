import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val item = FurnitureItem("a", "Шкаф", Size3Mm(600, 500, 2100), Vec3Mm(1000, 1000), roomId = "r")
    val project = ProjectModel("p", "Тест", listOf(room), listOf(item), activeRoomId = "r")
    val resized = ProjectEditor.resizeFurniture(project, "a", 800, 600, 2200)
    check(resized.furniture.single().size == Size3Mm(800, 600, 2200))
    check(project.furniture.single().size == Size3Mm(600, 500, 2100)) // immutable source
    val dims = ProjectEditor.furnitureDimensions(resized, "a")
    check(dims.widthMm == 800 && dims.depthMm == 600 && dims.heightMm == 2200)
    check(dims.westClearanceMm == 600 && dims.eastClearanceMm == 3600)
    check(dims.northClearanceMm == 700 && dims.southClearanceMm == 2700)
    val rotated = resized.copy(furniture = resized.furniture.map { it.copy(rotationDeg = 90) })
    val rotatedDims = ProjectEditor.furnitureDimensions(rotated, "a")
    check(rotatedDims.widthMm == 600 && rotatedDims.depthMm == 800)
    check(runCatching { ProjectEditor.resizeFurniture(project, "a", -1, 600, 2200) }.isFailure)
    val nearWall = project.copy(furniture = listOf(item.copy(position = Vec3Mm(400, 1000))))
    check(runCatching { ProjectEditor.resizeFurniture(nearWall, "a", 1000, 600, 2200) }.isFailure)
    val overlapping = project.copy(furniture = listOf(item, FurnitureItem("b", "Стол", Size3Mm(700, 700, 700), Vec3Mm(1700, 1000), roomId = "r")))
    check(runCatching { ProjectEditor.resizeFurniture(overlapping, "a", 1200, 700, 2200) }.isFailure)
    println("ResizeDimensionsSmoke: 10 checks passed")
}
