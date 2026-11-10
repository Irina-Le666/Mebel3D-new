import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, message: String) { check(value) { message }; checks++ }
    check(InteriorCatalogEngine.entries.size >= 35, "catalog breadth")
    check(InteriorCatalogEngine.search("диван").single().id == "sofa-2", "search by name")
    check(InteriorCatalogEngine.search("отдых").any { it.id == "sofa-2" }, "search by tag")
    check(InteriorCatalogEngine.search(category = FurnitureRoomCategory.BATHROOM).all { it.category == FurnitureRoomCategory.BATHROOM }, "category filter")
    check(InteriorCatalogEngine.styles.size >= 6, "style presets")
    val room = RoomModel(widthMm = 5000, depthMm = 4000, heightMm = 2700)
    val project = ProjectModel("p", "test", listOf(room), activeRoomId = room.id)
    val placed = InteriorCatalogEngine.place(project, "sofa-2", "sofa-1")
    check(placed.item != null && placed.issues.isEmpty(), "free placement")
    check(placed.item!!.roomId == room.id, "room ownership")
    check(placed.testedPositions == 1, "first candidate")
    val withSofa = project.copy(furniture = listOf(placed.item!!))
    val second = InteriorCatalogEngine.place(withSofa, "armchair", "chair-1")
    check(second.item != null, "find next free location")
    check(second.item!!.position != placed.item!!.position, "avoid overlap")
    check(InteriorCatalogEngine.place(project, "missing", "x").item == null, "unknown catalog id")
    check(runCatching { InteriorCatalogEngine.place(project, "sofa-2", "") }.isFailure, "blank id rejected")
    val styled = InteriorCatalogEngine.applyStyle(placed.item!!, "graphite")
    check(styled.materialId == "paint-graphite" && styled.colorArgb == 0xFF454A50, "style application")
    check(placed.item!!.materialId == "fabric-linen", "catalog default finish")
    println("InteriorCatalogSmoke: $checks checks passed")
}
