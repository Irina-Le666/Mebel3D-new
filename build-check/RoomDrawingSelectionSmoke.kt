package ru.mebel3d.app.core

object RoomDrawingSelectionSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val first = PlanFurnitureProjection("a", "Стол", listOf(
            DrawingPoint2(0.0, 0.0), DrawingPoint2(1000.0, 0.0),
            DrawingPoint2(1000.0, 600.0), DrawingPoint2(0.0, 600.0)), 0, 700)
        val second = PlanFurnitureProjection("b", "Стул", listOf(
            DrawingPoint2(200.0, 100.0), DrawingPoint2(500.0, 100.0),
            DrawingPoint2(500.0, 400.0), DrawingPoint2(200.0, 400.0)), 0, 800)
        val plan = RoomPlanProjection(3000, 2000, listOf(first, second), listOf(
            PlanOpeningProjection("north-door", OpeningKind.DOOR, WallSide.NORTH, 800, 1700),
            PlanOpeningProjection("east-window", OpeningKind.WINDOW, WallSide.EAST, 400, 1200)
        ), listOf(
            PlanEngineeringProjection("outlet", EngineeringKind.OUTLET, DrawingPoint2(250.0, 0.0), 300, WallSide.NORTH),
            PlanEngineeringProjection("tap", EngineeringKind.WATER, DrawingPoint2(3000.0, 900.0), 600, WallSide.EAST)
        ))
        check(RoomDrawingSelection.selectPlanFurniture(plan, 250.0, 200.0) == "b")
        check(RoomDrawingSelection.selectPlanFurniture(plan, 900.0, 500.0) == "a")
        check(RoomDrawingSelection.selectPlanFurniture(plan, 1200.0, 500.0) == null)
        check(RoomDrawingSelection.selectPlanFurniture(plan, 0.0, 300.0) == "a")
        check(RoomDrawingSelection.selectPlanOpening(plan, 1200.0, 5.0, 20.0) == "north-door")
        check(RoomDrawingSelection.selectPlanOpening(plan, 2995.0, 800.0, 20.0) == "east-window")
        check(RoomDrawingSelection.selectPlanOpening(plan, 100.0, 100.0, 20.0) == null)
        check(RoomDrawingSelection.selectPlanEngineering(plan, 255.0, 4.0, 10.0) == "outlet")
        check(RoomDrawingSelection.selectPlanEngineering(plan, 2800.0, 800.0, 10.0) == null)

        val elevation = WallElevationProjection(WallSide.NORTH, 3000, 2700, emptyList(), listOf(
            ElevationOpeningProjection("window", OpeningKind.WINDOW, DrawingRect(1000.0, 900.0, 2200.0, 2100.0))
        ), listOf(ElevationEngineeringProjection("switch", EngineeringKind.SWITCH, 500, 1200, 0)), listOf(
            ElevationFurnitureProjection("a", "Шкаф", DrawingRect(0.0, 0.0, 1200.0, 2200.0)),
            ElevationFurnitureProjection("b", "Полка", DrawingRect(200.0, 900.0, 800.0, 1400.0))
        ), emptyList(), emptyList())
        check(RoomDrawingSelection.selectElevationFurniture(elevation, 400.0, 1000.0) == "b")
        check(RoomDrawingSelection.selectElevationFurniture(elevation, 1000.0, 1000.0) == "a")
        check(RoomDrawingSelection.selectElevationFurniture(elevation, 1600.0, 1000.0) == null)
        check(RoomDrawingSelection.selectElevationFurniture(elevation, 400.0, 2500.0) == null)
        check(RoomDrawingSelection.selectElevationOpening(elevation, 1500.0, 1500.0) == "window")
        check(RoomDrawingSelection.selectElevationOpening(elevation, 2500.0, 1500.0) == null)
        check(RoomDrawingSelection.selectElevationEngineering(elevation, 505.0, 1198.0, 10.0) == "switch")
        check(RoomDrawingSelection.selectElevationEngineering(elevation, 900.0, 1200.0, 10.0) == null)

        val door = WallOpening("door-1", WallSide.NORTH, 1000, 800, 0, 2000, OpeningKind.DOOR)
        val outlet = EngineeringElement("outlet-1", "Розетка", EngineeringKind.OUTLET, Vec3Mm(500, 0, 300), WallSide.NORTH)
        val room = RoomModel(id = "room-1", widthMm = 3000, depthMm = 2000, heightMm = 2700,
            openings = listOf(door), engineering = listOf(outlet))
        var project = ProjectModel("p", "Схема", rooms = listOf(room), activeRoomId = room.id)
        project = ProjectEditor.updateOpening(project, room.id, door.copy(widthMm = 900))
        project = ProjectEditor.updateEngineeringElement(project, room.id, outlet.copy(position = Vec3Mm(700, 0, 350)))
        val updatedRoom = project.rooms.single()
        val updatedDrawings = RoomDrawingProjection.build(updatedRoom, emptyList())
        check(updatedRoom.openings.single().id == "door-1" && updatedRoom.openings.single().widthMm == 900)
        check(updatedRoom.engineering.single().id == "outlet-1" && updatedRoom.engineering.single().position.x == 700)
        check(updatedDrawings.plan.openings.single().id == "door-1" && updatedDrawings.plan.openings.single().endMm == 1900)
        check(updatedDrawings.plan.engineering.single().id == "outlet-1" && updatedDrawings.plan.engineering.single().point.xMm == 700.0)
        check(updatedDrawings.elevations.getValue(WallSide.NORTH).openings.single().bounds.widthMm == 900.0)
        check(updatedDrawings.elevations.getValue(WallSide.NORTH).engineering.single().alongMm == 700)

        // Direct schematic movement uses the same room model and keeps wall constraints.
        project = ProjectEditor.moveOpening(project, room.id, "door-1", 1200)
        project = ProjectEditor.moveEngineeringElement(project, room.id, "outlet-1", 1137, 0, 417, gridMm = 10)
        val moved = project.rooms.single()
        check(moved.openings.single().offsetMm == 1200)
        check(moved.engineering.single().position == Vec3Mm(1140, 0, 420))
        val movedDrawings = RoomDrawingProjection.build(moved, emptyList())
        check(movedDrawings.plan.openings.single().startMm == 1200)
        check(movedDrawings.plan.engineering.single().point.xMm == 1140.0)
        check(movedDrawings.elevations.getValue(WallSide.NORTH).engineering.single().elevationMm == 420)
        project = ProjectEditor.moveEngineeringOnWall(project, room.id, "outlet-1", 1401, 505, 10)
        check(project.rooms.single().engineering.single().position == Vec3Mm(1400, 0, 510))
        val free = EngineeringElement("free", "Свободная точка", EngineeringKind.PIPE, Vec3Mm(100, 100, 100))
        project = ProjectEditor.putEngineeringElement(project, room.id, free)
        project = ProjectEditor.moveEngineeringElement(project, room.id, "free", 9999, -50, 9999, gridMm = 0)
        check(project.rooms.single().engineering.first { it.id == "free" }.position == Vec3Mm(3000, 0, 2700))
        check(runCatching { ProjectEditor.moveOpening(project, room.id, "door-1", 2900) }.isFailure)
        check(runCatching { ProjectEditor.moveEngineeringOnWall(project, room.id, "free", 100, 100) }.isFailure)
        println("RoomDrawingSelectionSmoke: 31 checks passed")
    }
}
