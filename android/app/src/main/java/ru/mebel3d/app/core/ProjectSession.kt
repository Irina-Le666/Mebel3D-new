package ru.mebel3d.app.core

/** Coordinates immutable project edits, bounded undo/redo and the saved-state marker. */
class ProjectSession(initial: ProjectModel, historyCapacity: Int = 100) {
    private val history = ProjectHistory(initial, historyCapacity)
    private var savedSnapshot: ProjectModel = initial
    private var gestureActive: Boolean = false
    private var gestureHasChanges: Boolean = false

    val project: ProjectModel get() = history.current
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo
    val isDirty: Boolean get() = project != savedSnapshot

    /** The transform must either return a complete valid snapshot or throw without changing session state. */
    fun edit(transform: (ProjectModel) -> ProjectModel): ProjectModel {
        // A non-pointer edit must close (not abandon) an in-flight gesture transaction.
        // Otherwise ProjectHistory remains transaction-locked and the next gesture fails.
        endGesture()
        val candidate = transform(project)
        require(candidate.id == project.id) { "ID проекта нельзя менять в рамках редактирования" }
        ProjectValidator.requireValid(candidate)
        history.commit(candidate)
        return project
    }

    /** Begin one undoable pointer gesture. All subsequent gesture edits replace its snapshot. */
    fun beginGesture() {
        if (gestureActive) endGesture()
        history.beginTransaction()
        gestureActive = true
        gestureHasChanges = false
    }

    /** Apply a validated intermediate state without flooding undo history during dragging. */
    fun editGesture(transform: (ProjectModel) -> ProjectModel): ProjectModel {
        require(gestureActive) { "Жест редактирования не начат" }
        val candidate = transform(project)
        require(candidate.id == project.id) { "ID проекта нельзя менять в рамках редактирования" }
        ProjectValidator.requireValid(candidate)
        if (!gestureHasChanges) {
            val changed = candidate != project
            history.commit(candidate)
            gestureHasChanges = changed
        } else {
            history.replaceCurrent(candidate)
        }
        return project
    }

    /** Finish the pointer gesture; the entire drag is now one undo operation. */
    fun endGesture() {
        if (gestureActive) history.commitTransaction()
        gestureActive = false
        gestureHasChanges = false
    }

    /** Roll back a cancelled pointer gesture, including its undo/redo branches. */
    fun cancelGesture(): ProjectModel {
        if (gestureActive) history.cancelTransaction()
        gestureActive = false
        gestureHasChanges = false
        return project
    }

    fun undo(): ProjectModel { endGesture(); return history.undo() }
    fun redo(): ProjectModel { endGesture(); return history.redo() }

    /** Call only after persistence reports a successful save. */
    fun markSaved() { savedSnapshot = project }

    /** Loading or switching projects starts a clean history and saved state. */
    fun open(project: ProjectModel) {
        ProjectValidator.requireValid(project)
        history.reset(project)
        savedSnapshot = project
        endGesture()
    }
}
