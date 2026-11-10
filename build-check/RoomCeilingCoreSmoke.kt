package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }

    val legacy = RoomModel()
    verify(legacy.ceilingMaterialId == null, "legacy room has no explicit ceiling material")
    verify(legacy.ceilingColorArgb == 0xFFF8F7F3L, "legacy ceiling uses neutral fallback")
    val wood = RoomSurfaceAppearance.applyCeiling(legacy, "ceiling-wood")
    verify(wood.ceilingMaterialId == "ceiling-wood", "ceiling material ID is assigned")
    verify(wood.ceilingColorArgb == 0xFFE5D8C2L, "ceiling color follows catalog")
    verify(wood.wallMaterialId == null && wood.floorMaterialId == null, "ceiling update leaves other surfaces unchanged")
    verify(RoomSurfaceAppearance.ceilingMaterial(wood)?.pattern == SurfacePattern.WOOD_GRAIN, "ceiling pattern is exposed to renderer")
    verify(runCatching { RoomSurfaceAppearance.applyCeiling(legacy, "fabric-linen") }.isFailure, "incompatible upholstery is rejected")
    val white = RoomSurfaceAppearance.applyCeiling(wood, "ceiling-white")
    verify(white.ceilingMaterialId == "ceiling-white", "ceiling can be changed again")
    verify(white.ceilingColorArgb == 0xFFF8F7F3L, "replacement color is updated")

    println("RoomCeilingCoreSmoke: $checks checks passed")
}
