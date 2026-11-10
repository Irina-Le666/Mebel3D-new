import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, message: String) { check(value) { message }; checks++ }
    val base = RoomPhotoReference("p1", "content://room/photo", WallSide.NORTH, 2000, 1000, 4000, 2500)
    val door = PhotoAnnotation("a1", PhotoAnnotationKind.DOOR, 100, 200, 900, 2100, "Дверь")
    val marked = GeometryEngine.addPhotoAnnotation(base, door)
    check(marked.annotations.size == 1, "annotation added")
    check(GeometryEngine.annotationBoundsMm(marked, door) == listOf(200, 400, 1800, 4200), "pixel-to-mm conversion")
    check(runCatching { GeometryEngine.addPhotoAnnotation(marked, door) }.isFailure, "duplicate ID rejected")
    check(runCatching { GeometryEngine.addPhotoAnnotation(base, door.copy(xPx = -1)) }.isFailure, "negative coordinate rejected")
    check(runCatching { GeometryEngine.addPhotoAnnotation(base, door.copy(xPx = 3500, widthPx = 600)) }.isFailure, "right overflow rejected")
    check(runCatching { GeometryEngine.addPhotoAnnotation(base, door.copy(yPx = 2400, heightPx = 200)) }.isFailure, "bottom overflow rejected")
    val window = GeometryEngine.addPhotoAnnotation(marked, door.copy(id = "a2", kind = PhotoAnnotationKind.WINDOW))
    check(window.annotations.size == 2, "multiple marks")
    check(GeometryEngine.removePhotoAnnotation(window, "a1").annotations.single().id == "a2", "annotation removed")
    check(runCatching { GeometryEngine.removePhotoAnnotation(base, "missing") }.isFailure, "missing mark rejected")
    val project = ProjectModel("pr", "Test", listOf(RoomModel(id = "r", photos = listOf(base))), activeRoomId = "r")
    val edited = ProjectEditor.addPhotoAnnotation(project, "r", "p1", door)
    check(edited.rooms.single().photos.single().annotations.single() == door, "project editor persists annotation in model")
    val removed = ProjectEditor.removePhotoAnnotation(edited, "r", "p1", "a1")
    check(removed.rooms.single().photos.single().annotations.isEmpty(), "project editor removes annotation")
    val changed = GeometryEngine.updatePhotoAnnotation(edited.rooms.single().photos.single(), door.copy(kind = PhotoAnnotationKind.WINDOW, label = "Новое окно"))
    check(changed.annotations.single().kind == PhotoAnnotationKind.WINDOW && changed.annotations.single().label == "Новое окно", "annotation metadata updated")
    check(runCatching { GeometryEngine.updatePhotoAnnotation(changed, door.copy(xPx = 3900, widthPx = 200)) }.isFailure, "updated annotation bounds validated")
    val projectChanged = ProjectEditor.updatePhotoAnnotation(edited, "r", "p1", door.copy(label = "Исправленная дверь"))
    check(projectChanged.rooms.single().photos.single().annotations.single().label == "Исправленная дверь", "project editor updates annotation")
    println("PhotoAnnotationSmoke: $checks checks passed")
}
