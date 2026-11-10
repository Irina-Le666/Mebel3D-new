import ru.mebel3d.app.core.ProjectHistory

fun main() {
    val h = ProjectHistory(listOf("room"), 3)
    check(!h.canUndo && !h.canRedo)
    h.commit(listOf("room", "sofa")); check(h.canUndo)
    h.commit(listOf("room", "sofa", "table")); check(h.current.size == 3)
    check(h.undo().size == 2 && h.canRedo)
    check(h.redo().size == 3)
    h.undo(); h.commit(listOf("room", "chair")); check(!h.canRedo)
    h.commit(listOf("room", "chair", "lamp")); h.commit(listOf("room", "chair", "lamp", "rug"))
    check(h.current.size == 4)
    h.undo(); h.undo(); check(h.current.size == 2) // bounded oldest snapshot was dropped
    h.reset(listOf("fresh")); check(!h.canUndo && !h.canRedo && h.current == listOf("fresh"))
    println("ProjectHistory: 8 assertions passed")
}
