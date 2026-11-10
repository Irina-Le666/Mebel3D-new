import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val base = ProjectModel("p", "test", listOf(room), emptyList(), emptyList(), "r")
    val door = WallOpening("d", WallSide.NORTH, 500, 900, 0, 2100, OpeningKind.DOOR)
    val withDoor = base.copy(rooms = listOf(GeometryEngine.addOpening(room, door)))
    val movedDoor = door.copy(offsetMm = 800, widthMm = 1000)
    val editedDoor = ProjectEditor.updateOpening(withDoor, "r", movedDoor)
    check(editedDoor.rooms.single().openings.single() == movedDoor)
    check(runCatching { ProjectEditor.updateOpening(withDoor, "r", door.copy(offsetMm = 4800)) }.isFailure)
    check(ProjectEditor.removeOpening(editedDoor, "r", "d").rooms.single().openings.isEmpty())

    val outlet = EngineeringElement("e", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1000, 0, 300), WallSide.NORTH, 20)
    val withOutlet = ProjectEditor.putEngineeringElement(base, "r", outlet)
    val changed = outlet.copy(name = "Розетка двойная", position = Vec3Mm(1500, 0, 450), diameterMm = 30)
    val edited = ProjectEditor.updateEngineeringElement(withOutlet, "r", changed)
    check(edited.rooms.single().engineering.single() == changed)
    check(runCatching { ProjectEditor.updateEngineeringElement(withOutlet, "r", changed.copy(position = Vec3Mm(6000, 100, 450))) }.isFailure)
    val session = ProjectSession(withOutlet)
    session.edit { ProjectEditor.updateEngineeringElement(it, "r", changed).copy(name = it.name) }
    check(session.project.rooms.single().engineering.single() == changed)
    check(session.undo().rooms.single().engineering.single() == outlet)
    check(session.redo().rooms.single().engineering.single() == changed)
    println("SelectedElementsEditingSmoke: 9 checks passed")
}
