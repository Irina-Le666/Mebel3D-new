package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun check(value: Boolean, label: String) { check(value) { label }; checks++ }
    val room = RoomModel(widthMm = 5000, depthMm = 3500, heightMm = 2700,
        openings = listOf(
            WallOpening("door", WallSide.NORTH, 1000, 900, 0, 2100, OpeningKind.DOOR),
            WallOpening("window", WallSide.EAST, 900, 1200, 900, 1200, OpeningKind.WINDOW)
        ),
        engineering = listOf(EngineeringElement("water", "Вода", EngineeringKind.WATER, Vec3Mm(2500, 0, 500), WallSide.NORTH, 20)))
    fun item(id: String, x: Int, y: Int, z: Int = 0, w: Int = 600, d: Int = 600, h: Int = 800, angle: Int = 0) =
        FurnitureItem(id, id, Size3Mm(w, d, h), Vec3Mm(x, y, z), angle)

    val door = RoomPlanningDiagnostics.inspect(room, listOf(item("door-block", 1450, 350)))
    check(door.any { it.kind == PlanningAdviceKind.BLOCKED_DOOR && it.relatedId == "door" }, "door access should be reported")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("away", 3500, 2500))).isEmpty(), "remote furniture should not be reported")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("above", 1450, 350, z = 2200))).none { it.kind == PlanningAdviceKind.BLOCKED_DOOR }, "furniture above door should not block it")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("window-block", 4650, 1500, z = 900))).any { it.kind == PlanningAdviceKind.OBSTRUCTED_WINDOW }, "window obstruction should be reported")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("below-window", 4650, 1500, z = 0, h = 800))).none { it.kind == PlanningAdviceKind.OBSTRUCTED_WINDOW }, "low furniture below sill should not block window")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("water-block", 2500, 250))).any { it.kind == PlanningAdviceKind.BLOCKED_ENGINEERING_ACCESS }, "engineering access should be reported")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("not-water", 3500, 250))).none { it.kind == PlanningAdviceKind.BLOCKED_ENGINEERING_ACCESS }, "remote furniture should not block engineering")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("rotated", 1900, 350, w = 1000, d = 250, angle = 45))).any { it.kind == PlanningAdviceKind.BLOCKED_DOOR }, "rotated footprint should be tested")
    check(RoomPlanningDiagnostics.inspect(room, emptyList()).isEmpty(), "empty scene should have no advice")
    check(runCatching { RoomPlanningDiagnostics.inspect(room, emptyList(), doorClearanceMm = -1) }.isFailure, "negative clearance must be rejected")
    check(RoomPlanningDiagnostics.inspect(room, listOf(item("touch-edge", 1450, 1300))).none { it.kind == PlanningAdviceKind.BLOCKED_DOOR }, "edge contact beyond clearance should not be reported")
    val southRoom = RoomModel(widthMm = 5000, depthMm = 3500, openings = listOf(WallOpening("south-door", WallSide.SOUTH, 1000, 900, 0, 2100, OpeningKind.DOOR)))
    check(RoomPlanningDiagnostics.inspect(southRoom, listOf(item("south-block", 1450, 3250))).any { it.relatedId == "south-door" }, "south wall clearance should be oriented inward")
    val westRoom = RoomModel(widthMm = 5000, depthMm = 3500, openings = listOf(WallOpening("west-door", WallSide.WEST, 1000, 900, 0, 2100, OpeningKind.DOOR)))
    check(RoomPlanningDiagnostics.inspect(westRoom, listOf(item("west-block", 350, 1450))).any { it.relatedId == "west-door" }, "west wall clearance should be oriented inward")
    val eastRoom = RoomModel(widthMm = 5000, depthMm = 3500, engineering = listOf(EngineeringElement("east-gas", "Газ", EngineeringKind.GAS, Vec3Mm(5000, 1500, 500), WallSide.EAST, 20)))
    check(RoomPlanningDiagnostics.inspect(eastRoom, listOf(item("east-access", 4850, 1500))).any { it.relatedId == "east-gas" }, "east engineering access should be detected")
    println("RoomPlanningDiagnosticsSmoke: $checks checks passed")
}
