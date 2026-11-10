import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val item = FurnitureItem(id = "f", roomId = "r", name = "Тумба", position = Vec3Mm(1000, 1000, 0), size = Size3Mm(500, 500, 700))
    val initial = ProjectModel("p", "Проект", listOf(room), listOf(item), activeRoomId = "r")
    val session = ProjectSession(initial)
    session.beginGesture()
    session.editGesture { ProjectEditor.moveFurniture(it, "f", 1200, 1000, gridMm = 1) }
    session.editGesture { ProjectEditor.moveFurniture(it, "f", 1400, 1100, gridMm = 1) }
    session.editGesture { ProjectEditor.moveFurniture(it, "f", 1600, 1200, gridMm = 1) }
    session.endGesture()
    check(session.project.furniture.single().position.x == 1600)
    check(session.canUndo)
    val undone = session.undo()
    check(undone.furniture.single().position == item.position) { "Одно undo должно отменить весь drag" }
    check(session.canRedo)
    val redone = session.redo()
    check(redone.furniture.single().position.x == 1600)

    session.beginGesture()
    session.editGesture { ProjectEditor.moveFurniture(it, "f", 1800, 1200, gridMm = 1) }
    session.endGesture()
    check(session.undo().furniture.single().position.x == 1600)
    println("ProjectGestureSmoke: 8 assertions passed")
}
