import ru.mebel3d.app.core.*

fun main() {
    val initial = ProjectModel("p1", "Дом")
    val session = ProjectSession(initial, 4)
    check(!session.isDirty && !session.canUndo)
    val room2 = RoomModel(id = "r2", name = "Кухня")
    session.edit { ProjectEditor.addRoom(it, room2) }
    check(session.isDirty && session.canUndo && session.project.activeRoomId == "r2")
    session.markSaved()
    check(!session.isDirty)
    session.edit { ProjectEditor.renameRoom(it, "r2", "Кухня-гостиная") }
    check(session.isDirty)
    check(session.undo().rooms.first { it.id == "r2" }.name == "Кухня")
    check(!session.isDirty && session.canRedo)
    check(session.redo().rooms.first { it.id == "r2" }.name == "Кухня-гостиная")
    check(session.isDirty)
    session.undo()
    session.edit { ProjectEditor.renameRoom(it, "r2", "Студия") }
    check(!session.canRedo)
    val before = session.project
    check(runCatching { session.edit { it.copy(activeRoomId = "missing") } }.isFailure)
    check(session.project == before)
    session.open(initial)
    check(!session.isDirty && !session.canUndo && !session.canRedo)
    // Import/project switching is a full session replacement, not a same-ID edit.
    val imported = ProjectModel("imported-project", "Импортированный интерьер", listOf(RoomModel(id = "imported-room")))
    session.open(imported)
    check(session.project.id == "imported-project")
    check(session.project.name == "Импортированный интерьер")
    check(!session.isDirty && !session.canUndo && !session.canRedo)

    // A drag that returns to its exact starting snapshot must not leave a phantom undo step.
    session.beginGesture()
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Временное имя") }
    check(session.canUndo && session.project.rooms.single().name == "Временное имя")
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Помещение 1") }
    session.endGesture()
    check(!session.canUndo && session.project.rooms.single().name == "Помещение 1")

    // A later edit in the same gesture after returning to baseline remains one undoable step.
    session.beginGesture()
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Черновик") }
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Помещение 1") }
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Финальный вариант") }
    session.endGesture()
    check(session.canUndo && session.project.rooms.single().name == "Финальный вариант")
    check(session.undo().rooms.single().name == "Помещение 1")
    check(!session.canUndo)

    // A cancelled pointer gesture restores the exact model and both history branches.
    session.redo()
    val beforeCancel = session.project
    val undoBeforeCancel = session.canUndo
    val redoBeforeCancel = session.canRedo
    session.beginGesture()
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Незавершённое перемещение") }
    check(session.project.rooms.single().name == "Незавершённое перемещение")
    check(session.cancelGesture() == beforeCancel)
    check(session.project == beforeCancel)
    check(session.canUndo == undoBeforeCancel && session.canRedo == redoBeforeCancel)

    // A normal command arriving during a pointer gesture commits the gesture first.
    // It must not strand ProjectHistory in an open transaction.
    val interleavedSession = ProjectSession(imported)
    interleavedSession.beginGesture()
    interleavedSession.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Перетаскивание") }
    interleavedSession.edit { ProjectEditor.renameRoom(it, "imported-room", "Командное изменение") }
    check(interleavedSession.project.rooms.single().name == "Командное изменение")
    check(interleavedSession.undo().rooms.single().name == "Перетаскивание")
    check(interleavedSession.undo().rooms.single().name == "Помещение 1")
    interleavedSession.edit { ProjectEditor.renameRoom(it, "imported-room", "После транзакции") }
    interleavedSession.beginGesture()
    interleavedSession.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Новый жест") }
    interleavedSession.cancelGesture()
    check(interleavedSession.project.rooms.single().name == "После транзакции")

    // Cancellation also works when a gesture made several intermediate updates.
    session.beginGesture()
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Шаг 1") }
    session.editGesture { ProjectEditor.renameRoom(it, "imported-room", "Шаг 2") }
    session.cancelGesture()
    check(session.project == beforeCancel)
    check(session.canUndo == undoBeforeCancel && session.canRedo == redoBeforeCancel)
    println("ProjectSessionSmoke: cancellation and history transaction assertions passed")
}
