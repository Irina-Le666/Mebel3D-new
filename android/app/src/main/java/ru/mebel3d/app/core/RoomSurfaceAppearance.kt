package ru.mebel3d.app.core

/** Room-level surface appearance; material IDs are optional for backwards compatibility. */
object RoomSurfaceAppearance {
    fun applyWall(room: RoomModel, materialId: String): RoomModel {
        val error = InteriorMaterialLibrary.validate(materialId, SurfaceUse.WALL)
        require(error == null) { error ?: "Недопустимая отделка стен" }
        val material = requireNotNull(InteriorMaterialLibrary.find(materialId))
        return room.copy(wallMaterialId = material.id, wallColorArgb = material.argb)
    }

    fun applyFloor(room: RoomModel, materialId: String): RoomModel {
        val error = InteriorMaterialLibrary.validate(materialId, SurfaceUse.FLOOR)
        require(error == null) { error ?: "Недопустимая отделка пола" }
        val material = requireNotNull(InteriorMaterialLibrary.find(materialId))
        return room.copy(floorMaterialId = material.id, floorColorArgb = material.argb)
    }

    fun applyCeiling(room: RoomModel, materialId: String): RoomModel {
        val error = InteriorMaterialLibrary.validate(materialId, SurfaceUse.CEILING)
        require(error == null) { error ?: "Недопустимая отделка потолка" }
        val material = requireNotNull(InteriorMaterialLibrary.find(materialId))
        return room.copy(ceilingMaterialId = material.id, ceilingColorArgb = material.argb)
    }

    fun applyWall(room: RoomModel, side: WallSide, materialId: String): RoomModel {
        val error = InteriorMaterialLibrary.validate(materialId, SurfaceUse.WALL)
        require(error == null) { error ?: "Недопустимая отделка стены" }
        return when (side) {
            WallSide.NORTH -> room.copy(northWallMaterialId = materialId)
            WallSide.EAST -> room.copy(eastWallMaterialId = materialId)
            WallSide.SOUTH -> room.copy(southWallMaterialId = materialId)
            WallSide.WEST -> room.copy(westWallMaterialId = materialId)
        }
    }

    fun inheritWallFinish(room: RoomModel, side: WallSide): RoomModel = when (side) {
        WallSide.NORTH -> room.copy(northWallMaterialId = null)
        WallSide.EAST -> room.copy(eastWallMaterialId = null)
        WallSide.SOUTH -> room.copy(southWallMaterialId = null)
        WallSide.WEST -> room.copy(westWallMaterialId = null)
    }

    fun wallMaterial(room: RoomModel): InteriorMaterial? =
        room.wallMaterialId?.let(InteriorMaterialLibrary::find)?.takeIf { SurfaceUse.WALL in it.uses }

    fun wallMaterial(room: RoomModel, side: WallSide): InteriorMaterial? {
        val override = when (side) {
            WallSide.NORTH -> room.northWallMaterialId
            WallSide.EAST -> room.eastWallMaterialId
            WallSide.SOUTH -> room.southWallMaterialId
            WallSide.WEST -> room.westWallMaterialId
        }
        return (override ?: room.wallMaterialId)?.let(InteriorMaterialLibrary::find)
            ?.takeIf { SurfaceUse.WALL in it.uses }
    }

    fun wallMaterialId(room: RoomModel, side: WallSide): String? = when (side) {
        WallSide.NORTH -> room.northWallMaterialId
        WallSide.EAST -> room.eastWallMaterialId
        WallSide.SOUTH -> room.southWallMaterialId
        WallSide.WEST -> room.westWallMaterialId
    } ?: room.wallMaterialId

    fun floorMaterial(room: RoomModel): InteriorMaterial? =
        room.floorMaterialId?.let(InteriorMaterialLibrary::find)?.takeIf { SurfaceUse.FLOOR in it.uses }

    fun ceilingMaterial(room: RoomModel): InteriorMaterial? =
        room.ceilingMaterialId?.let(InteriorMaterialLibrary::find)?.takeIf { SurfaceUse.CEILING in it.uses }
}
