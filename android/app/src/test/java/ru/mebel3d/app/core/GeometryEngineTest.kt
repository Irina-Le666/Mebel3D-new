package ru.mebel3d.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryEngineTest {
    @Test fun quarterTurnSwapsFootprint() {
        assertEquals(900 to 2400, GeometryEngine.rotatedFootprint(Size3Mm(2400, 900, 850), 90))
    }

    @Test fun detectsFurnitureOutsideRoom() {
        val item = FurnitureItem("1", "Шкаф", Size3Mm(1000, 600, 2200), Vec3Mm(4900, 3000))
        assertTrue(GeometryEngine.validateFurniture(RoomModel(), listOf(item)).any { it.code == GeometryIssueCode.OUT_OF_ROOM })
    }

    @Test fun rotatedFurnitureBoundaryUsesUnroundedMillimetreGeometry() {
        // The rounded 45-degree footprint is 1416 mm, but its exact half-extent
        // is slightly greater than 708 mm. A centre at 708 must therefore be rejected.
        val item = FurnitureItem("edge", "Шкаф", Size3Mm(1001, 1002, 2000), Vec3Mm(708, 1500), 45)
        assertTrue(GeometryEngine.validateFurniture(RoomModel(), listOf(item))
            .any { it.code == GeometryIssueCode.OUT_OF_ROOM && it.objectId == "edge" })
    }

    @Test fun rotatedFurnitureFitsWhenExactFootprintClearsWall() {
        val item = FurnitureItem("inside", "Шкаф", Size3Mm(1001, 1002, 2000), Vec3Mm(709, 1500), 45)
        assertTrue(GeometryEngine.validateFurniture(RoomModel(), listOf(item))
            .none { it.code == GeometryIssueCode.OUT_OF_ROOM })
    }

    @Test fun detectsCollision() {
        val a = FurnitureItem("a", "Стол", Size3Mm(1200, 700, 750), Vec3Mm(2000, 1500))
        val b = FurnitureItem("b", "Стул", Size3Mm(500, 500, 900), Vec3Mm(2100, 1500))
        assertTrue(GeometryEngine.validateFurniture(RoomModel(), listOf(a, b)).any { it.code == GeometryIssueCode.COLLISION })
    }

    @Test fun planProjectionUsesRotatedFurnitureFootprintInRoomCoordinates() {
        val room = RoomModel(widthMm = 5000, depthMm = 3500)
        val item = FurnitureItem("table", "Стол", Size3Mm(1200, 600, 750), Vec3Mm(2000, 1500), 90)
        val plan = RoomDrawingProjection.plan(room, listOf(item))
        val projected = plan.furniture.single()
        assertEquals("table", projected.id)
        assertEquals(600.0, projected.polygon.maxOf { it.xMm } - projected.polygon.minOf { it.xMm }, 0.001)
        assertEquals(1200.0, projected.polygon.maxOf { it.yMm } - projected.polygon.minOf { it.yMm }, 0.001)
        assertEquals("table", RoomDrawingSelection.selectPlanFurniture(plan, 2000.0, 1500.0))
    }

    @Test fun planAndElevationShareOpeningIdentityAndMillimetreBounds() {
        val room = RoomModel(openings = listOf(
            WallOpening("window-n", WallSide.NORTH, 700, 1300, 900, 1200, OpeningKind.WINDOW)
        ))
        val drawings = RoomDrawingProjection.build(room, emptyList())
        val planOpening = drawings.plan.openings.single()
        val elevationOpening = drawings.elevations.getValue(WallSide.NORTH).openings.single()
        assertEquals(planOpening.id, elevationOpening.id)
        assertEquals(700.0, elevationOpening.bounds.leftMm, 0.0)
        assertEquals(2000.0, elevationOpening.bounds.rightMm, 0.0)
        assertEquals(900.0, elevationOpening.bounds.bottomMm, 0.0)
        assertEquals(2100.0, elevationOpening.bounds.topMm, 0.0)
    }

    @Test fun wallMountedEngineeringPointAppearsInPlanAndMatchingElevationOnly() {
        val marker = EngineeringElement("socket", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1250, 0, 350), WallSide.NORTH)
        val drawings = RoomDrawingProjection.build(RoomModel(engineering = listOf(marker)), emptyList())
        assertEquals("socket", drawings.plan.engineering.single().id)
        assertEquals("socket", drawings.elevations.getValue(WallSide.NORTH).engineering.single().id)
        assertTrue(drawings.elevations.getValue(WallSide.SOUTH).engineering.isEmpty())
        assertTrue(drawings.elevations.getValue(WallSide.EAST).engineering.isEmpty())
        assertTrue(drawings.elevations.getValue(WallSide.WEST).engineering.isEmpty())
    }

    @Test fun wallElevationProjectsFurnitureTouchingThatWallWithExactHeight() {
        val room = RoomModel()
        val item = FurnitureItem("base", "Тумба", Size3Mm(800, 500, 900), Vec3Mm(1200, 250))
        val north = RoomDrawingProjection.elevation(room, listOf(item), WallSide.NORTH)
        val south = RoomDrawingProjection.elevation(room, listOf(item), WallSide.SOUTH)
        assertEquals(1, north.furniture.size)
        assertEquals(0, south.furniture.size)
        assertEquals(0.0, north.furniture.single().bounds.bottomMm, 0.0)
        assertEquals(900.0, north.furniture.single().bounds.topMm, 0.0)
    }

    @Test fun cabinetHasSelectableParts() {
        val parts = GeometryEngine.createCabinetParts(800, 500, 2100, 18)
        assertEquals(6, parts.size)
        assertTrue(parts.any { it.id == "shelf" })
    }
    @Test fun validatesAndAddsWindowOpening() {
        val room = RoomModel()
        val window = WallOpening("w1", WallSide.NORTH, 900, 1400, 900, 1400, OpeningKind.WINDOW)
        val updated = GeometryEngine.addOpening(room, window)
        assertEquals(1, updated.openings.size)
        assertTrue(GeometryEngine.validateRoom(updated).isEmpty())
    }

    @Test fun rejectsOpeningOutsideWall() {
        val room = RoomModel()
        val invalid = WallOpening("bad", WallSide.EAST, 3000, 900, 0, 2100, OpeningKind.DOOR)
        assertTrue(GeometryEngine.validateRoom(room.copy(openings = listOf(invalid))).any { it.code == GeometryIssueCode.INVALID_OPENING })
    }

    @Test fun supportsEngineeringElementsAndValidatesCoordinates() {
        val outlet = EngineeringElement("socket-1", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1200, 0, 300), WallSide.NORTH)
        val updated = GeometryEngine.addEngineeringElement(RoomModel(), outlet)
        assertEquals(1, updated.engineering.size)
        val invalid = outlet.copy(id = "bad", position = Vec3Mm(6000, 0, 300))
        assertTrue(GeometryEngine.validateRoom(RoomModel(engineering = listOf(invalid))).any { it.code == GeometryIssueCode.INVALID_ENGINEERING })
    }

    @Test fun resizesRoomWithoutChangingUnrelatedRoomData() {
        val room = RoomModel(openings = listOf(WallOpening("d", WallSide.SOUTH, 100, 900, 0, 2100, OpeningKind.DOOR)))
        val resized = GeometryEngine.resizeRoom(room, 6000, 4000, 2800)
        assertEquals(6000, resized.widthMm)
        assertEquals(room.openings, resized.openings)
    }


    @Test fun wallElevationIsSplitAroundWindow() {
        val room = RoomModel(openings = listOf(WallOpening("w", WallSide.NORTH, 1000, 1200, 900, 1200, OpeningKind.WINDOW)))
        val segments = GeometryEngine.wallSegments(room, WallSide.NORTH)
        assertTrue(segments.any { it.offsetMm == 0 && it.lengthMm == 1000 && it.heightMm == 2700 })
        assertTrue(segments.any { it.offsetMm == 1000 && it.bottomMm == 0 && it.heightMm == 900 })
        assertTrue(segments.any { it.offsetMm == 1000 && it.bottomMm == 2100 && it.heightMm == 600 })
    }

    @Test fun snappingUsesMillimetreGrid() {
        assertEquals(1250, GeometryEngine.snap(1237, 50))
        assertEquals(1200, GeometryEngine.snap(1224, 50))
    }

    @Test fun cabinetCutListContainsAllBoards() {
        val assembly = FurnitureAssembly("cab", "Шкаф", Vec3Mm(0, 0), GeometryEngine.createCabinetParts(800, 500, 2100, 18))
        val cuts = GeometryEngine.cutList(assembly)
        assertEquals(6, cuts.size)
        assertTrue(cuts.any { it.partId == "shelf" && it.thicknessMm == 18 })
        assertTrue(GeometryEngine.validateAssembly(assembly).isEmpty())
    }

    @Test fun rotatedFurnitureCollisionUsesActualOrientation() {
        val a = FurnitureItem("a", "Стол", Size3Mm(1200, 300, 750), Vec3Mm(2000, 1500), 45)
        val b = FurnitureItem("b", "Тумба", Size3Mm(300, 300, 600), Vec3Mm(2000, 1900), 0)
        assertTrue(GeometryEngine.validateFurniture(RoomModel(), listOf(a, b)).any { it.code == GeometryIssueCode.COLLISION })
    }

    @Test fun rejectsOverlappingOpeningsOnSameWall() {
        val a = WallOpening("a", WallSide.NORTH, 500, 1200, 0, 2100, OpeningKind.DOOR)
        val b = WallOpening("b", WallSide.NORTH, 1500, 1200, 900, 1200, OpeningKind.WINDOW)
        assertTrue(GeometryEngine.validateRoom(RoomModel(openings = listOf(a, b))).any { it.objectId == "b" })
    }

    @Test fun rejectsDoorWithRaisedSill() {
        val door = WallOpening("door", WallSide.WEST, 200, 800, 100, 2000, OpeningKind.DOOR)
        assertTrue(GeometryEngine.validateRoom(RoomModel(openings = listOf(door))).any { it.code == GeometryIssueCode.INVALID_OPENING })
    }

    @Test fun snappedMoveAndUpsertAreDeterministic() {
        val original = FurnitureItem("x", "Шкаф", Size3Mm(600, 600, 2000), Vec3Mm(1000, 1000))
        val moved = GeometryEngine.snappedMove(original, 1237, 1224, 50)
        assertEquals(Vec3Mm(1250, 1200), moved.position)
        assertEquals(1, GeometryEngine.upsertFurniture(listOf(original), moved).size)
    }

}


    @Test fun planAndElevationSelectTheSameOpeningByItsStableId() {
        val room = RoomModel(openings = listOf(
            WallOpening("door-east", WallSide.EAST, 400, 900, 0, 2100, OpeningKind.DOOR)
        ))
        val drawings = RoomDrawingProjection.build(room, emptyList())
        val plan = drawings.plan
        val elevation = drawings.elevations.getValue(WallSide.EAST)
        assertEquals("door-east", RoomDrawingSelection.selectPlanOpening(
            plan, room.widthMm.toDouble(), 850.0, 25.0
        ))
        assertEquals("door-east", RoomDrawingSelection.selectElevationOpening(
            elevation, 850.0, 1000.0
        ))
        assertEquals(null, RoomDrawingSelection.selectElevationOpening(
            drawings.elevations.getValue(WallSide.WEST), 850.0, 1000.0
        ))
    }

    @Test fun engineeringPointSelectionUsesWallElevationCoordinatesAndTolerance() {
        val marker = EngineeringElement("water-east", "Вывод воды", EngineeringKind.WATER,
            Vec3Mm(5000, 1250, 600), WallSide.EAST)
        val drawings = RoomDrawingProjection.build(RoomModel(engineering = listOf(marker)), emptyList())
        val east = drawings.elevations.getValue(WallSide.EAST)
        assertEquals("water-east", RoomDrawingSelection.selectElevationEngineering(
            east, 1260.0, 610.0, 20.0
        ))
        assertEquals(null, RoomDrawingSelection.selectElevationEngineering(
            east, 1300.0, 650.0, 20.0
        ))
    }
