import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val item = FurnitureItem("f", "Шкаф", Size3Mm(600, 600, 2100), Vec3Mm(1000, 1000, 0), roomId = "r")
    val base = ProjectModel("p", "test", listOf(room), listOf(item), emptyList(), "r")
    val moved = ProjectEditor.moveFurniture(base, "f", 1270, 1380)
    check(moved.furniture.single().position.x == 1250)
    check(moved.furniture.single().position.y == 1400)
    check(base.furniture.single().position == Vec3Mm(1000, 1000, 0))
    val rotated = ProjectEditor.rotateFurniture(moved, "f", 90)
    check(rotated.furniture.single().rotationDeg == 90)
    check(runCatching { ProjectEditor.moveFurniture(base, "f", 4900, 3900) }.isFailure)
    val second = item.copy(id = "f2", name = "Стол", position = Vec3Mm(2000, 1000, 0))
    val withSecond = base.copy(furniture = base.furniture + second)
    check(runCatching { ProjectEditor.moveFurniture(withSecond, "f", 2000, 1000) }.isFailure)
    val session = ProjectSession(base)
    session.edit { ProjectEditor.moveFurniture(it, "f", 1300, 1300) }
    check(session.undo().furniture.single().position == item.position)
    check(session.redo().furniture.single().position == Vec3Mm(1300, 1300, 0))
    println("DirectManipulationSmoke: 8 checks passed")
}
