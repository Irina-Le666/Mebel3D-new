import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, message: String) { check(value) { message }; checks++ }
    val room = RoomModel(widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val iso = RoomVisualizationEngine.preset(RoomViewPreset.ISOMETRIC)
    check(iso.yawDeg == 45f && iso.pitchDeg == 32f, "isometric preset")
    val top = RoomVisualizationEngine.preset(RoomViewPreset.TOP)
    check(top.pitchDeg == 85f, "top preset")
    val front = RoomVisualizationEngine.preset(RoomViewPreset.FRONT)
    check(front.pitchDeg == 12f, "front preset")
    val topFloorNorth = RoomVisualizationEngine.project(room, top, Vec3Mm(2500, 0, 0))
    val topFloorSouth = RoomVisualizationEngine.project(room, top, Vec3Mm(2500, 3500, 0))
    check(kotlin.math.abs(topFloorNorth.y - topFloorSouth.y) > 3000f, "top preset exposes floor depth")
    val frontFloorNorth = RoomVisualizationEngine.project(room, front, Vec3Mm(2500, 0, 0))
    val frontFloorSouth = RoomVisualizationEngine.project(room, front, Vec3Mm(2500, 3500, 0))
    check(kotlin.math.abs(frontFloorNorth.y - frontFloorSouth.y) < kotlin.math.abs(topFloorNorth.y - topFloorSouth.y), "front preset compresses floor depth")
    val nw = RoomVisualizationEngine.preset(RoomViewPreset.NORTH_WEST)
    check(nw.yawDeg == 315f, "north-west preset")
    val orbit = RoomVisualizationEngine.orbit(iso, 30f, 100f)
    check(orbit.yawDeg == 75f && orbit.pitchDeg == 85f, "orbit clamps pitch")
    val wrap = RoomVisualizationEngine.orbit(iso, -90f, -10f)
    check(wrap.yawDeg == 315f && wrap.pitchDeg == 22f, "orbit wraps yaw")
    val zoomIn = RoomVisualizationEngine.zoom(iso, 2f)
    check(zoomIn.zoom == 2f, "zoom in")
    check(RoomVisualizationEngine.zoom(iso, 100f).zoom == 4f, "zoom upper bound")
    check(RoomVisualizationEngine.zoom(iso, 0.001f).zoom == 0.25f, "zoom lower bound")
    val origin = RoomVisualizationEngine.project(room, iso, Vec3Mm(2500, 1750, 1350))
    check(kotlin.math.abs(origin.x) < 0.01f && kotlin.math.abs(origin.y) < 0.01f, "camera target projects to origin")
    val north = RoomVisualizationEngine.project(room, iso, Vec3Mm(2500, 0, 1350))
    val south = RoomVisualizationEngine.project(room, iso, Vec3Mm(2500, 3500, 1350))
    check(north.depth != south.depth, "depth separates opposite walls")
    val transparent = RoomVisualizationEngine.wallVisuals(iso, WallDisplayMode.ALL_TRANSPARENT)
    check(transparent.size == 4 && transparent.all { it.visible }, "all four walls retained")
    fun emphasized(yaw: Float) = RoomVisualizationEngine.wallVisuals(iso.copy(yawDeg = yaw), WallDisplayMode.ALL_TRANSPARENT)
        .filter { it.opacity > 0.7f }.map { it.side }.toSet()
    check(emphasized(0f) == setOf(WallSide.SOUTH), "yaw 0 faces south wall")
    check(emphasized(45f) == setOf(WallSide.EAST, WallSide.SOUTH), "isometric view faces east and south")
    check(emphasized(90f) == setOf(WallSide.EAST), "yaw 90 faces east wall")
    check(emphasized(180f) == setOf(WallSide.NORTH), "yaw 180 faces north wall")
    check(emphasized(225f) == setOf(WallSide.NORTH, WallSide.WEST), "north-west diagonal faces north and west")
    check(emphasized(270f) == setOf(WallSide.WEST), "yaw 270 faces west wall")
    check(transparent.any { it.opacity < 0.3f } && transparent.any { it.opacity > 0.7f }, "near/far wall opacity")
    val cutaway = RoomVisualizationEngine.wallVisuals(iso, WallDisplayMode.CUTAWAY)
    check(cutaway.size == 4 && cutaway.all { it.visible && it.opacity in 0f..1f }, "cutaway keeps four walls with valid opacity")
    val solid = RoomVisualizationEngine.wallVisuals(iso, WallDisplayMode.SOLID_NEAR)
    check(solid.count { it.visible } in 1..3, "solid near walls only")
    val rotatedWalls = RoomVisualizationEngine.wallVisuals(iso.copy(yawDeg = 135f), WallDisplayMode.ALL_TRANSPARENT)
    check(rotatedWalls.filter { it.opacity > 0.7f }.map { it.side }.toSet() != transparent.filter { it.opacity > 0.7f }.map { it.side }.toSet(), "near wall emphasis follows camera yaw")
    check(RoomVisualizationEngine.daylightFactor(LightingPreset.STUDIO) > RoomVisualizationEngine.daylightFactor(LightingPreset.WARM_EVENING), "lighting presets")
    val northFurniture = FurnitureItem("north", "Северный", Size3Mm(600, 500, 900), Vec3Mm(2500, 500, 0))
    val southFurniture = FurnitureItem("south", "Южный", Size3Mm(600, 500, 900), Vec3Mm(2500, 3000, 0))
    val southFacingCamera = RoomCameraState(yawDeg = 0f, pitchDeg = 32f)
    check(RoomVisualizationEngine.furnitureDepth(room, southFacingCamera, southFurniture) >
        RoomVisualizationEngine.furnitureDepth(room, southFacingCamera, northFurniture), "south-side furniture is closer at yaw zero")
    check(RoomVisualizationEngine.furnitureBackToFront(room, southFacingCamera, listOf(southFurniture, northFurniture)).map { it.id } ==
        listOf("north", "south"), "furniture painter order is far-to-near")
    val northFacingCamera = southFacingCamera.copy(yawDeg = 180f)
    check(RoomVisualizationEngine.furnitureBackToFront(room, northFacingCamera, listOf(northFurniture, southFurniture)).map { it.id } ==
        listOf("south", "north"), "painter order reverses with camera orbit")
    val tieA = northFurniture.copy(id = "a")
    val tieB = northFurniture.copy(id = "b")
    check(RoomVisualizationEngine.furnitureBackToFront(room, southFacingCamera, listOf(tieB, tieA)).map { it.id } ==
        listOf("a", "b"), "painter order is deterministic at equal depth")
    val assembly = FurnitureAssembly("cabinet", "Корпус", Vec3Mm(0, 0, 0), listOf(
        FurniturePart("board", "Полка", Size3Mm(500, 400, 18), Vec3Mm(100, 100, 700))
    ))
    check(RoomVisualizationEngine.partDepth(room, southFacingCamera, assembly, assembly.parts.single()).isFinite(),
        "parametric part has finite camera depth")
    val fit = RoomVisualizationEngine.fitZoom(room, 1200f, 800f)
    check(fit > 0f && fit <= 4f, "fit zoom positive and bounded")
    check(InteriorMaterialCatalog.materials.size >= 8, "interior material catalog")
    check(InteriorMaterialCatalog.find("oak-natural")?.finish == SurfaceFinish.WOOD_GRAIN, "material lookup")

    val solidWall = RoomVisualizationEngine.wallPanels(4000, 2700, emptyList())
    check(solidWall.size == 1 && solidWall.single().widthMm == 4000 && solidWall.single().heightMm == 2700,
        "wall without openings remains one full panel")
    val door = WallOpening("door", WallSide.NORTH, 500, 900, 0, 2100, OpeningKind.DOOR)
    val window = WallOpening("window", WallSide.NORTH, 2200, 1200, 900, 1200, OpeningKind.WINDOW)
    val panels = RoomVisualizationEngine.wallPanels(4000, 2700, listOf(door, window))
    val panelArea = panels.sumOf { it.widthMm.toLong() * it.heightMm }
    val expectedArea = 4000L * 2700 - 900L * 2100 - 1200L * 1200
    check(panelArea == expectedArea, "door and window are removed from wall surface")
    check(panels.none { it.offsetMm < 1400 && it.offsetMm + it.widthMm > 500 && it.bottomMm < 2100 && it.bottomMm + it.heightMm > 0 },
        "door opening contains no wall fragment")
    check(panels.none { it.offsetMm < 3400 && it.offsetMm + it.widthMm > 2200 && it.bottomMm < 2100 && it.bottomMm + it.heightMm > 900 },
        "window opening contains no wall fragment")
    val overlapA = WallOpening("a", WallSide.SOUTH, 500, 1200, 500, 1200, OpeningKind.WINDOW)
    val overlapB = WallOpening("b", WallSide.SOUTH, 1000, 1200, 1000, 1200, OpeningKind.WINDOW)
    val overlapPanels = RoomVisualizationEngine.wallPanels(3000, 2500, listOf(overlapA, overlapB))
    val overlapArea = overlapPanels.sumOf { it.widthMm.toLong() * it.heightMm }
    check(overlapArea == 3000L * 2500 - (2L * 1200L * 1200L - 700L * 700L),
        "overlapping openings are subtracted as a union")
    val openingRoom = RoomModel(widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val wallOpenings = listOf(
        WallOpening("north", WallSide.NORTH, 300, 900, 700, 1200, OpeningKind.WINDOW),
        WallOpening("east", WallSide.EAST, 400, 800, 0, 2100, OpeningKind.DOOR),
        WallOpening("south", WallSide.SOUTH, 1200, 1000, 800, 1100, OpeningKind.WINDOW),
        WallOpening("west", WallSide.WEST, 1500, 700, 0, 2050, OpeningKind.DOOR)
    )
    val expectedOpeningCorners = listOf(
        listOf(Vec3Mm(300, 0, 700), Vec3Mm(1200, 0, 700), Vec3Mm(1200, 0, 1900), Vec3Mm(300, 0, 1900)),
        listOf(Vec3Mm(5000, 400, 0), Vec3Mm(5000, 1200, 0), Vec3Mm(5000, 1200, 2100), Vec3Mm(5000, 400, 2100)),
        listOf(Vec3Mm(1200, 3500, 800), Vec3Mm(2200, 3500, 800), Vec3Mm(2200, 3500, 1900), Vec3Mm(1200, 3500, 1900)),
        listOf(Vec3Mm(0, 1500, 0), Vec3Mm(0, 2200, 0), Vec3Mm(0, 2200, 2050), Vec3Mm(0, 1500, 2050))
    )
    wallOpenings.zip(expectedOpeningCorners).forEach { (opening, expected) ->
        check(RoomVisualizationEngine.openingCorners(openingRoom, opening) == expected,
            "${opening.side} opening projects onto its canonical wall plane")
    }
    check(RoomVisualizationEngine.openingCorners(openingRoom, wallOpenings.first().copy(widthMm = Int.MAX_VALUE)) == null,
        "overflowing opening edge is rejected for rendering")
    check(RoomVisualizationEngine.openingFillOpacity(OpeningKind.WINDOW, false) == 0.10f,
        "unselected glazing stays transparent")
    check(RoomVisualizationEngine.openingFillOpacity(OpeningKind.DOOR, false) < 0.05f,
        "door leaf does not seal the doorway visually")
    check(RoomVisualizationEngine.openingFillOpacity(OpeningKind.WINDOW, true) >
        RoomVisualizationEngine.openingFillOpacity(OpeningKind.WINDOW, false),
        "selected opening receives stronger visual feedback")
    val clipped = RoomVisualizationEngine.wallPanels(2000, 2000, listOf(
        WallOpening("outside", WallSide.EAST, -100, 500, 1800, 500, OpeningKind.WINDOW)
    ))
    check(clipped.sumOf { it.widthMm.toLong() * it.heightMm } == 2000L * 2000 - 400L * 200,
        "out-of-bounds openings are clipped safely")
    check(RoomVisualizationEngine.wallPanels(0, 1000, listOf(door)).isEmpty(), "invalid wall dimensions yield no panels")

    val wallMarkers = listOf(
        EngineeringElement("outlet-n", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1200, 0, 350), WallSide.NORTH),
        EngineeringElement("water-e", "Вода", EngineeringKind.WATER, Vec3Mm(5000, 1200, 600), WallSide.EAST),
        EngineeringElement("gas-s", "Газ", EngineeringKind.GAS, Vec3Mm(2400, 3500, 500), WallSide.SOUTH),
        EngineeringElement("switch-w", "Выключатель", EngineeringKind.SWITCH, Vec3Mm(0, 1800, 1100), WallSide.WEST)
    )
    val expectedMarkerCorners = listOf(
        listOf(Vec3Mm(1110, 0, 260), Vec3Mm(1290, 0, 260), Vec3Mm(1290, 0, 440), Vec3Mm(1110, 0, 440)),
        listOf(Vec3Mm(5000, 1110, 510), Vec3Mm(5000, 1290, 510), Vec3Mm(5000, 1290, 690), Vec3Mm(5000, 1110, 690)),
        listOf(Vec3Mm(2490, 3500, 410), Vec3Mm(2310, 3500, 410), Vec3Mm(2310, 3500, 590), Vec3Mm(2490, 3500, 590)),
        listOf(Vec3Mm(0, 1890, 1010), Vec3Mm(0, 1710, 1010), Vec3Mm(0, 1710, 1190), Vec3Mm(0, 1890, 1190))
    )
    wallMarkers.zip(expectedMarkerCorners).forEach { (element, expected) ->
        check(RoomVisualizationEngine.engineeringMarkerCorners(room, element) == expected,
            "${element.wallSide} engineering marker lies on its wall plane and faces the room")
    }
    check(RoomVisualizationEngine.engineeringMarkerCorners(room, wallMarkers.first().copy(position = Vec3Mm(20, 0, 350))) == null,
        "marker plaque is clipped instead of crossing a room corner")
    check(RoomVisualizationEngine.engineeringMarkerCorners(room, wallMarkers.first(), 0, 90) == null,
        "zero-width marker geometry is rejected")
    check(RoomVisualizationEngine.engineeringMarkerCorners(room, wallMarkers.first().copy(wallSide = null))?.size == 4,
        "legacy free-standing engineering marker remains renderable")
    println("RoomVisualizationSmoke: $checks checks passed")
}
