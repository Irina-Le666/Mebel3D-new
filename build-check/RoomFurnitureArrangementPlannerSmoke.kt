package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun check(value: Boolean, label: String) { check(value) { label }; checks++ }
    val room = RoomModel(id = "living", widthMm = 4200, depthMm = 3200, heightMm = 2700,
        openings = listOf(WallOpening("front-door", WallSide.NORTH, 1500, 900, 0, 2100, OpeningKind.DOOR)),
        engineering = listOf(EngineeringElement("water", "Вода", EngineeringKind.WATER,
            Vec3Mm(500, 0, 500), WallSide.NORTH, 20)))
    val otherRoom = RoomModel(id = "bedroom", widthMm = 3000, depthMm = 2800, heightMm = 2600)
    val sofa = FurnitureItem("sofa", "Диван", Size3Mm(1800, 800, 850), Vec3Mm(1950, 500), roomId = "living")
    val table = FurnitureItem("table", "Стол", Size3Mm(900, 700, 750), Vec3Mm(1950, 500), roomId = "living")
    val bed = FurnitureItem("bed", "Кровать", Size3Mm(1600, 2000, 600), Vec3Mm(1500, 1400), roomId = "bedroom")
    val project = ProjectModel("p", "Дом", listOf(room, otherRoom), listOf(sofa, table, bed), activeRoomId = "living")

    val plan = RoomFurnitureArrangementPlanner.plan(project, gridMm = 100)
    check(plan.isComplete, "all feasible furniture should receive a placement")
    check(plan.suggestions.size == 2, "only active-room furniture should be planned")
    check(plan.movedCount > 0, "overlapping/access-blocking furniture should be repositioned")
    check(project.furniture.first { it.id == "sofa" }.position == sofa.position, "planning must not mutate source")
    val preview = plan.suggestions.map { suggestion ->
        (if (suggestion.furnitureId == "sofa") sofa else table).copy(position = suggestion.to)
    }
    check(GeometryEngine.validateFurniture(room, preview).isEmpty(), "preview must be collision-free and in room")
    check(RoomPlanningDiagnostics.inspect(room, preview).isEmpty(), "preview should preserve door and engineering access")

    val applied = ProjectEditor.applyFurnitureArrangement(project, plan)
    check(applied.furniture.first { it.id == "bed" } == bed, "other-room furniture must remain unchanged")
    check(applied.furniture.filter { it.roomId == "living" } == preview, "apply must atomically apply preview")
    check(runCatching { ProjectEditor.applyFurnitureArrangement(project, plan.copy(suggestions = emptyList())) }.isFailure,
        "stale/incomplete plans must be rejected")
    check(runCatching { RoomFurnitureArrangementPlanner.plan(project, gridMm = 10) }.isFailure,
        "too-fine grid must be rejected")

    val impossibleRoom = RoomModel(id = "tiny", widthMm = 500, depthMm = 500, heightMm = 2200)
    val impossibleItem = FurnitureItem("huge", "Большой", Size3Mm(900, 900, 900), Vec3Mm(250, 250), roomId = "tiny")
    val impossible = RoomFurnitureArrangementPlanner.plan(
        ProjectModel("tiny-project", "Tiny", listOf(impossibleRoom), listOf(impossibleItem)), gridMm = 100)
    check(!impossible.isComplete && impossible.unresolvedFurnitureIds == listOf("huge"),
        "objects larger than room must be reported instead of silently resized")
    check(impossible.suggestions.single().to == impossibleItem.position,
        "unresolved object must retain its original preview position")
    println("RoomFurnitureArrangementPlannerSmoke: $checks checks passed")
}
