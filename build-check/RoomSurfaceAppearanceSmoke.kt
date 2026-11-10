import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }

    val original = RoomModel()
    verify(original.wallMaterialId == null && original.floorMaterialId == null, "legacy room appearance remains supported")
    val tileWall = RoomSurfaceAppearance.applyWall(original, "tile-white")
    verify(tileWall.wallMaterialId == "tile-white", "wall finish ID saved")
    verify(tileWall.wallColorArgb == 0xFFF1F0ECL, "wall finish color follows catalog")
    verify(tileWall.floorMaterialId == null && tileWall.floorColorArgb == original.floorColorArgb, "wall update leaves floor unchanged")

    val oakFloor = RoomSurfaceAppearance.applyFloor(tileWall, "wood-oak")
    verify(oakFloor.floorMaterialId == "wood-oak", "floor finish ID saved")
    verify(oakFloor.floorColorArgb == 0xFFD8C2A5L, "floor finish color follows catalog")
    verify(oakFloor.wallMaterialId == "tile-white", "floor update leaves wall unchanged")
    verify(InteriorMaterialLibrary.find("tile-white")!!.pattern == SurfacePattern.TILE, "tile pattern available to renderer")
    verify(InteriorMaterialLibrary.find("wood-oak")!!.pattern == SurfacePattern.WOOD_GRAIN, "wood pattern available to renderer")

    val project = ProjectModel(id = "surface-test", name = "Поверхности", rooms = listOf(oakFloor))
    verify(ProjectValidator.validate(project).isEmpty(), "valid room surface materials pass project validation")
    verify(runCatching { RoomSurfaceAppearance.applyWall(original, "metal-black") }.isFailure, "fixture-only material rejected for wall")
    verify(runCatching { RoomSurfaceAppearance.applyFloor(original, "fabric-linen") }.isFailure, "upholstery material rejected for floor")
    val ceiling = RoomSurfaceAppearance.applyCeiling(oakFloor, "ceiling-wood")
    verify(ceiling.ceilingMaterialId == "ceiling-wood", "ceiling finish ID saved")
    verify(ceiling.ceilingColorArgb == 0xFFE5D8C2L, "ceiling color follows material catalog")
    verify(ceiling.wallMaterialId == oakFloor.wallMaterialId && ceiling.floorMaterialId == oakFloor.floorMaterialId, "ceiling update preserves wall and floor")
    verify(RoomSurfaceAppearance.ceilingMaterial(ceiling)?.pattern == SurfacePattern.WOOD_GRAIN, "ceiling pattern is exposed to renderer")
    val ceilingProject = ProjectEditor.applyRoomSurface(project, "room-main", SurfaceUse.CEILING, "ceiling-warm")
    verify(ceilingProject.rooms.single().ceilingMaterialId == "ceiling-warm", "project editor applies ceiling finish")
    verify(ProjectValidator.validate(ceilingProject).isEmpty(), "ceiling finish passes project validation")
    verify(runCatching { RoomSurfaceAppearance.applyCeiling(original, "fabric-linen") }.isFailure, "upholstery material rejected for ceiling")
    val variantA = RoomDesignVariantManager.capture(project, "a", "A")
    val variantB = RoomDesignVariantManager.capture(ceilingProject, "b", "B")
    verify(RoomDesignVariantManager.compare(variantA, variantB).changes.any { it.kind == VariantChangeKind.ROOM_FINISH_CHANGED }, "ceiling finish change appears in variant comparison")
    val northFinished = RoomSurfaceAppearance.applyWall(original, WallSide.NORTH, "tile-white")
    verify(northFinished.northWallMaterialId == "tile-white", "north wall can receive independent finish")
    verify(RoomSurfaceAppearance.wallMaterial(northFinished, WallSide.NORTH)?.id == "tile-white", "north wall resolves its own material")
    verify(RoomSurfaceAppearance.wallMaterial(northFinished, WallSide.SOUTH) == null, "untouched wall does not inherit an override")
    val mixed = ProjectEditor.applyWallFinish(project, "room-main", WallSide.EAST, "solid-sand")
    verify(mixed.rooms.single().eastWallMaterialId == "solid-sand", "project editor updates only requested wall")
    verify(mixed.rooms.single().northWallMaterialId == null && mixed.rooms.single().southWallMaterialId == null, "other walls remain unchanged")
    verify(ProjectValidator.validate(mixed).isEmpty(), "per-wall finish passes project validation")
    val mixedVariant = RoomDesignVariantManager.capture(mixed, "c", "C")
    verify(RoomDesignVariantManager.compare(variantA, mixedVariant).changes.any { it.kind == VariantChangeKind.ROOM_FINISH_CHANGED }, "per-wall finish is included in variant comparison")
    verify(runCatching { RoomSurfaceAppearance.applyWall(original, WallSide.WEST, "fabric-linen") }.isFailure, "invalid per-wall material is rejected")
    verify(original.wallColorArgb == 0xFFF0ECE5L && original.floorColorArgb == 0xFFEADCC8L && original.ceilingColorArgb == 0xFFF8F7F3L, "original immutable appearance preserved")

    println("RoomSurfaceAppearanceSmoke: $checks checks passed")
}
