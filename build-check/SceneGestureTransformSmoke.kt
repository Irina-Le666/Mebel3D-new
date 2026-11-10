import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val item = FurnitureItem("cab", "Шкаф", Size3Mm(1000, 600, 2000), Vec3Mm(2500, 2000), roomId = "r")
    val project = ProjectModel("p", "Тест", listOf(room), furniture = listOf(item), activeRoomId = "r")

    val rotation = SceneGestureTransform.beginRotation(item, 100f, 0f)
    check(SceneGestureTransform.rotationAt(rotation, 0f, 100f) == 90)
    check(SceneGestureTransform.rotationAt(rotation, 0f, -100f) == 270)
    check(SceneGestureTransform.rotationAt(rotation, 100f, 0f) == 0)
    check(SceneGestureTransform.rotationAt(rotation, 0f, 100f, 15) == 90)
    check(runCatching { SceneGestureTransform.rotationAt(rotation, 0f, 100f, 0) }.isFailure)
    check(runCatching { SceneGestureTransform.beginRotation(item, 0f, 0f) }.isFailure)

    val height = SceneGestureTransform.beginHeightChange(item)
    check(SceneGestureTransform.heightAt(height, -0.58f * kotlin.math.cos(Math.toRadians(32.0)).toFloat() * 0.058f * 100f, 0.058f, true, 2700, 32f) == 2100)
    check(SceneGestureTransform.heightAt(height, 0.58f * kotlin.math.cos(Math.toRadians(32.0)).toFloat() * 0.058f * 100f, 0.058f, true, 2700, 32f) == 1900)
    check(SceneGestureTransform.heightAt(height, -10000f, 1f, false, 2700) == 2700)
    check(runCatching { SceneGestureTransform.heightAt(height, 10f, 0f, true, 2700) }.isFailure)

    val rotated = SceneGestureTransform.applyRotation(project, rotation, 35)
    check(rotated.furniture.single().rotationDeg == 35)
    check(project.furniture.single().rotationDeg == 0)
    val taller = SceneGestureTransform.applyHeight(project, height, 2100)
    check(taller.furniture.single().size.height == 2100)
    check(taller.furniture.single().size.width == 1000 && taller.furniture.single().size.depth == 600)
    check(runCatching { SceneGestureTransform.applyHeight(project, height, 2800) }.isFailure)
    println("SceneGestureTransformSmoke: 14 checks passed")
}
