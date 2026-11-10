import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val item = FurnitureItem("a", "Шкаф", Size3Mm(1200, 600, 2000), Vec3Mm(2500, 2000), roomId = "r")
    val project = ProjectModel("p", "Проект", listOf(room), furniture = listOf(item), activeRoomId = "r")
    val rotated = ProjectEditor.rotateFurnitureTo(project, "a", 37)
    check(rotated.furniture.single().rotationDeg == 37)
    check(project.furniture.single().rotationDeg == 0) { "Исходный снимок изменён" }
    val wrapped = ProjectEditor.rotateFurnitureTo(rotated, "a", -15)
    check(wrapped.furniture.single().rotationDeg == 345)
    val taller = ProjectEditor.resizeFurnitureHeight(project, "a", 2200)
    check(taller.furniture.single().size.height == 2200)
    check(taller.furniture.single().size.width == item.size.width && taller.furniture.single().size.depth == item.size.depth)
    check(taller.furniture.single().position == item.position)
    check(runCatching { ProjectEditor.resizeFurnitureHeight(project, "a", 2800) }.isFailure)
    val edgeItem = item.copy(position = Vec3Mm(400, 300))
    val edgeProject = project.copy(furniture = listOf(edgeItem))
    check(runCatching { ProjectEditor.rotateFurnitureTo(edgeProject, "a", 45) }.isFailure)
    check(runCatching { ProjectEditor.rotateFurnitureTo(project, "missing", 90) }.isFailure)
    println("FurnitureTransformEditorSmoke: 9 checks passed")
}
