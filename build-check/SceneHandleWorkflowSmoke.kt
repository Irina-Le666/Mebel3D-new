import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val cabinet = FurnitureItem("cab", "Шкаф", Size3Mm(900, 600, 2100), Vec3Mm(2500, 2000), roomId = "r")
    val project = ProjectModel("p", "Проверка жестов", listOf(room), furniture = listOf(cabinet), activeRoomId = "r")

    val rotationSession = ProjectSession(project)
    val rotation = SceneGestureTransform.beginRotation(cabinet, 100f, 0f)
    rotationSession.beginGesture()
    listOf(20f, 45f, 80f, 100f).forEach { x ->
        val angle = SceneGestureTransform.rotationAt(rotation, x, 100f, 1)
        rotationSession.editGesture { SceneGestureTransform.applyRotation(it, rotation, angle) }
    }
    rotationSession.endGesture()
    check(rotationSession.project.furniture.single().rotationDeg == 45)
    check(rotationSession.undo().furniture.single().rotationDeg == 0) { "Поворот должен отменяться одним действием" }
    check(rotationSession.redo().furniture.single().rotationDeg == 45)

    val heightSession = ProjectSession(project)
    val height = SceneGestureTransform.beginHeightChange(cabinet)
    heightSession.beginGesture()
    listOf(-10f, -25f, -45f).forEach { drag ->
        val target = SceneGestureTransform.heightAt(height, drag, 0.1f, true, room.heightMm)
        heightSession.editGesture { SceneGestureTransform.applyHeight(it, height, target) }
    }
    heightSession.endGesture()
    check(heightSession.project.furniture.single().size.height == room.heightMm)
    check(heightSession.undo().furniture.single().size.height == 2100) { "Высота должна отменяться одним действием" }

    val blocked = cabinet.copy(position = Vec3Mm(450, 450))
    val blockedProject = project.copy(furniture = listOf(blocked))
    val blockedSession = ProjectSession(blockedProject)
    blockedSession.beginGesture()
    val blockedGesture = SceneGestureTransform.beginRotation(blocked, 100f, 0f)
    check(runCatching { blockedSession.editGesture { SceneGestureTransform.applyRotation(it, blockedGesture, 45) } }.isFailure)
    check(blockedSession.project == blockedProject) { "Отклонённый жест не должен менять проект" }
    blockedSession.endGesture()
    println("SceneHandleWorkflowSmoke: 8 checks passed")
}
