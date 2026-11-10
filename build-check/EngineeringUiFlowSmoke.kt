import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    val room = RoomModel(id = "r1", widthMm = 4000, depthMm = 3000, heightMm = 2700)
    val initial = ProjectModel("p1", "Инженерия", listOf(room), emptyList(), emptyList(), "r1")
    val session = ProjectSession(initial)
    val outlet = EngineeringElement("e1", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1200, 0, 300), WallSide.NORTH)
    session.edit { ProjectEditor.putEngineeringElement(it, "r1", outlet) }
    check(session.project.rooms.single().engineering.single() == outlet); checks++
    check(session.isDirty); checks++
    session.edit { ProjectEditor.updateEngineeringElement(it, "r1", outlet.copy(position = Vec3Mm(1500, 0, 300))) }
    check(session.project.rooms.single().engineering.single().position.x == 1500); checks++
    session.edit { ProjectEditor.removeEngineeringElement(it, "r1", "e1") }
    check(session.project.rooms.single().engineering.isEmpty()); checks++
    session.undo(); check(session.project.rooms.single().engineering.single().id == "e1"); checks++
    session.undo(); check(session.project.rooms.single().engineering.single().position.x == 1200); checks++
    session.redo(); check(session.project.rooms.single().engineering.single().position.x == 1500); checks++
    session.markSaved(); check(!session.isDirty); checks++
    println("EngineeringUiFlowSmoke: $checks checks passed")
}
