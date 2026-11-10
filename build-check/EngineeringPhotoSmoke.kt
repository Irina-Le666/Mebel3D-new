import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(ok: Boolean, message: String) { check(ok) { message }; checks++ }
    val room = RoomModel(id = "r1", widthMm = 4000, depthMm = 3000)
    val gas = EngineeringElement("gas", "Газ", EngineeringKind.GAS, Vec3Mm(100, 0, 1200), WallSide.NORTH)
    val added = GeometryEngine.addEngineeringElement(room, gas)
    check(added.engineering.size == 1, "add marker")
    val moved = GeometryEngine.updateEngineeringElement(added, gas.copy(position = Vec3Mm(200, 0, 1200)))
    check(moved.engineering.single().position.x == 200, "update marker")
    val copied = GeometryEngine.duplicateEngineeringElement(moved, "gas", "gas-copy")
    check(copied.engineering.size == 2 && copied.engineering.last().id == "gas-copy", "duplicate marker")
    val removed = GeometryEngine.removeEngineeringElement(copied, "gas")
    check(removed.engineering.single().id == "gas-copy", "remove marker")
    check(runCatching { GeometryEngine.duplicateEngineeringElement(copied, "gas", "gas-copy") }.isFailure, "duplicate ID rejected")
    check(runCatching { GeometryEngine.updateEngineeringElement(added, gas.copy(position = Vec3Mm(5000, 0, 0))) }.isFailure, "invalid position rejected")
    val project = ProjectModel("p", "Проект", listOf(room))
    val withMarker = ProjectEditor.putEngineeringElement(project, "r1", gas)
    check(withMarker.rooms.single().engineering.size == 1, "project add marker")
    val editedProject = ProjectEditor.updateEngineeringElement(withMarker, "r1", gas.copy(name = "Газовая труба"))
    check(editedProject.rooms.single().engineering.single().name == "Газовая труба", "project update marker")
    val cleared = ProjectEditor.removeEngineeringElement(editedProject, "r1", "gas")
    check(cleared.rooms.single().engineering.isEmpty(), "project remove marker")
    val photo = RoomPhotoReference("photo", "content://room/photo", WallSide.NORTH)
    val calibrated = GeometryEngine.calibratePhoto(photo, 2000, 800)
    check(kotlin.math.abs(GeometryEngine.millimetresPerPixel(calibrated) - 2.5) < 0.0001, "photo scale")
    check(runCatching { GeometryEngine.calibratePhoto(photo, 0, 800) }.isFailure, "invalid calibration rejected")
    check(runCatching { GeometryEngine.millimetresPerPixel(photo) }.isFailure, "uncalibrated photo rejected")
    println("EngineeringPhotoSmoke: $checks checks passed")
}
