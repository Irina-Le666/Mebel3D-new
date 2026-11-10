import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun verify(ok: Boolean, message: String) { check(ok) { message }; checks++ }

    val legacy = RoomModel()
    verify(RoomSurfaceAppearance.wallMaterialId(legacy, WallSide.NORTH) == null, "legacy wall has no override")
    val north = RoomSurfaceAppearance.applyWall(legacy, WallSide.NORTH, "tile-white")
    verify(north.northWallMaterialId == "tile-white", "north override is stored")
    verify(north.eastWallMaterialId == null && north.southWallMaterialId == null && north.westWallMaterialId == null,
        "other walls remain untouched")
    verify(RoomSurfaceAppearance.wallMaterial(north, WallSide.NORTH)?.pattern == SurfacePattern.TILE,
        "north wall resolves material and pattern")
    verify(RoomSurfaceAppearance.wallMaterial(north, WallSide.SOUTH) == null,
        "unmodified wall retains legacy custom-color mode")
    val all = RoomSurfaceAppearance.applyWall(north, "solid-sand")
    verify(RoomSurfaceAppearance.wallMaterial(all, WallSide.NORTH)?.id == "tile-white",
        "side override takes precedence over room-wide finish")
    verify(RoomSurfaceAppearance.wallMaterial(all, WallSide.EAST)?.id == "solid-sand",
        "room-wide finish remains fallback for other walls")
    verify(runCatching { RoomSurfaceAppearance.applyWall(legacy, WallSide.WEST, "fabric-linen") }.isFailure,
        "incompatible wall material is rejected")
    verify(legacy.northWallMaterialId == null, "immutable source room remains unchanged")
    val inherited = RoomSurfaceAppearance.inheritWallFinish(all, WallSide.NORTH)
    verify(inherited.northWallMaterialId == null && RoomSurfaceAppearance.wallMaterial(inherited, WallSide.NORTH)?.id == "solid-sand",
        "wall override can be cleared to inherit common finish")
    println("RoomPerWallFinishCoreSmoke: $checks checks passed")
}
