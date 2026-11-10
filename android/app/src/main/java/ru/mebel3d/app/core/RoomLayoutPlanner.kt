package ru.mebel3d.app.core

/** Wall-aware furniture placement. All coordinates are room-local millimetres. */
data class WallRunPlacement(
    val moduleId: String,
    val assembly: FurnitureAssembly,
    val center: Vec3Mm,
    val origin: Vec3Mm,
    val rotationDeg: Int,
    val wallSide: WallSide,
    val moduleSize: Size3Mm
)

data class RoomLayoutRequest(
    val roomId: String,
    val wallSide: WallSide,
    val modules: List<FurnitureModuleSpec>,
    val startOffsetMm: Int = 0,
    val endOffsetMm: Int = 0,
    val wallGapMm: Int = 0,
    val moduleGapMm: Int = 2,
    val minimumAisleMm: Int = 900,
    val avoidWindows: Boolean = true,
    val avoidDoors: Boolean = true,
    val avoidEngineering: Boolean = true
)

data class RoomLayoutIssue(val code: String, val message: String, val relatedId: String? = null)
data class RoomLayoutResult(
    val placements: List<WallRunPlacement>,
    val issues: List<RoomLayoutIssue>,
    val usedWallLengthMm: Int,
    val remainingWallLengthMm: Int,
    val minimumAisleMm: Int
) { val isValid: Boolean get() = issues.none { it.code in setOf("OUT_OF_ROOM", "OPENING_CONFLICT", "FURNITURE_COLLISION", "ENGINEERING_CONFLICT", "INSUFFICIENT_WALL", "INVALID_REQUEST", "NARROW_AISLE") } }

object RoomLayoutPlanner {
    private const val MAX_COORDINATE_MM = Int.MAX_VALUE.toLong()

    fun plan(project: ProjectModel, request: RoomLayoutRequest): RoomLayoutResult {
        val issues = mutableListOf<RoomLayoutIssue>()
        val room = project.rooms.firstOrNull { it.id == request.roomId }
            ?: return RoomLayoutResult(emptyList(), listOf(RoomLayoutIssue("INVALID_REQUEST", "Помещение не найдено", request.roomId)), 0, 0, 0)
        if (request.modules.isEmpty() || request.startOffsetMm < 0 || request.endOffsetMm < 0 ||
            request.wallGapMm < 0 || request.moduleGapMm < 0 || request.minimumAisleMm < 0) {
            return RoomLayoutResult(emptyList(), listOf(RoomLayoutIssue("INVALID_REQUEST", "Проверьте модули, отступы и ширину прохода")), 0, 0, 0)
        }
        if (request.modules.map { it.id }.distinct().size != request.modules.size)
            issues += RoomLayoutIssue("INVALID_REQUEST", "Идентификаторы модулей должны быть уникальными")
        if (request.modules.any { it.id.isBlank() || it.widthMm <= 0 || it.depthMm <= 0 || it.heightMm <= 0 ||
                it.widthMm > ParametricDimensionLimits.MAX_MM || it.depthMm > ParametricDimensionLimits.MAX_MM || it.heightMm > ParametricDimensionLimits.MAX_MM })
            issues += RoomLayoutIssue("INVALID_REQUEST", "Размеры и идентификаторы всех модулей должны быть допустимыми")
        if (issues.isNotEmpty()) return RoomLayoutResult(emptyList(), issues, 0, 0, 0)

        val wallLength = room.wallLength(request.wallSide).toLong()
        val moduleWidths = request.modules.sumOf { it.widthMm.toLong() }
        val gaps = request.moduleGapMm.toLong() * (request.modules.size - 1).toLong()
        val totalWidthLong = moduleWidths + gaps
        val availableLong = wallLength - request.startOffsetMm.toLong() - request.endOffsetMm.toLong()
        if (totalWidthLong > MAX_COORDINATE_MM || availableLong !in Int.MIN_VALUE.toLong()..MAX_COORDINATE_MM) {
            issues += RoomLayoutIssue("INVALID_REQUEST", "Суммарные размеры композиции выходят за допустимый диапазон")
            return RoomLayoutResult(emptyList(), issues, 0, 0, request.minimumAisleMm)
        }
        if (totalWidthLong > availableLong) issues += RoomLayoutIssue("INSUFFICIENT_WALL", "Для композиции нужно ${totalWidthLong} мм, доступно ${availableLong} мм")
        if (issues.isNotEmpty()) return RoomLayoutResult(emptyList(), issues, totalWidthLong.coerceAtMost(MAX_COORDINATE_MM).toInt(), (availableLong-totalWidthLong).coerceIn(Int.MIN_VALUE.toLong(), MAX_COORDINATE_MM).toInt(), request.minimumAisleMm)

        val modules = request.modules.map { spec ->
            val built = FurnitureModuleEngine.build(spec)
            if (built.assembly == null) issues += built.issues.map { RoomLayoutIssue(it.code, it.message, it.moduleId) }
            spec to built.assembly
        }
        if (issues.isNotEmpty()) return RoomLayoutResult(emptyList(), issues, totalWidthLong.toInt(), (availableLong-totalWidthLong).toInt(), request.minimumAisleMm)

        var cursor = request.startOffsetMm.toLong()
        val placements = mutableListOf<WallRunPlacement>()
        modules.forEach { (spec, nullableAssembly) ->
            val assembly = requireNotNull(nullableAssembly)
            val depth = spec.depthMm.toLong()
            val centerOffset = cursor + spec.widthMm.toLong() / 2L
            val gap = request.wallGapMm.toLong()
            val centerX: Long
            val centerY: Long
            val originX: Long
            val originY: Long
            when (request.wallSide) {
                WallSide.NORTH -> {
                    centerX = centerOffset; centerY = gap + depth / 2L
                    originX = cursor + spec.widthMm.toLong(); originY = gap + depth
                }
                WallSide.SOUTH -> {
                    centerX = centerOffset; centerY = room.depthMm.toLong() - gap - depth / 2L
                    originX = cursor; originY = room.depthMm.toLong() - gap - depth
                }
                WallSide.WEST -> {
                    centerX = gap + depth / 2L; centerY = centerOffset
                    originX = gap + depth; originY = cursor
                }
                WallSide.EAST -> {
                    centerX = room.widthMm.toLong() - gap - depth / 2L; centerY = centerOffset
                    originX = room.widthMm.toLong() - gap - depth; originY = cursor + spec.widthMm.toLong()
                }
            }
            if (centerX !in Int.MIN_VALUE.toLong()..MAX_COORDINATE_MM || centerY !in Int.MIN_VALUE.toLong()..MAX_COORDINATE_MM ||
                originX !in Int.MIN_VALUE.toLong()..MAX_COORDINATE_MM || originY !in Int.MIN_VALUE.toLong()..MAX_COORDINATE_MM) {
                issues += RoomLayoutIssue("OUT_OF_ROOM", "Модуль «${spec.name}» имеет координату за допустимым диапазоном", spec.id)
            } else {
                val center = Vec3Mm(centerX.toInt(), centerY.toInt(), 0)
                // Carcass coordinates use Y=0 at the front; the rear panel is at +Y=depth.
                // Rotate the rear panel onto the selected wall for each side of the room.
                val rotation = when (request.wallSide) {
                    WallSide.NORTH -> 180
                    WallSide.EAST -> 270
                    WallSide.SOUTH -> 0
                    WallSide.WEST -> 90
                }
                placements += WallRunPlacement(spec.id, assembly, center, Vec3Mm(originX.toInt(), originY.toInt(), 0),
                    rotation, request.wallSide, Size3Mm(spec.widthMm, spec.depthMm, spec.heightMm))
            }
            cursor += spec.widthMm.toLong() + request.moduleGapMm.toLong()
        }
        if (issues.isNotEmpty()) return RoomLayoutResult(emptyList(), issues, totalWidthLong.toInt(), (availableLong-totalWidthLong).toInt(), request.minimumAisleMm)

        // Verify the complete footprint fits within the room and preserve the requested aisle.
        placements.forEach { p ->
            val spec = request.modules.first { it.id == p.moduleId }
            val (fw, fd) = GeometryEngine.rotatedFootprint(Size3Mm(spec.widthMm, spec.depthMm, spec.heightMm), p.rotationDeg)
            val left = p.center.x.toLong() - fw.toLong()/2L
            val top = p.center.y.toLong() - fd.toLong()/2L
            val right = p.center.x.toLong() + fw.toLong()/2L
            val bottom = p.center.y.toLong() + fd.toLong()/2L
            if (left < 0 || top < 0 || right > room.widthMm.toLong() || bottom > room.depthMm.toLong())
                issues += RoomLayoutIssue("OUT_OF_ROOM", "Модуль «${spec.name}» не помещается в помещении", spec.id)
            val aisle = when (request.wallSide) {
                WallSide.NORTH -> room.depthMm.toLong() - bottom
                WallSide.SOUTH -> top
                WallSide.WEST -> room.widthMm.toLong() - right
                WallSide.EAST -> left
            }
            if (aisle < request.minimumAisleMm.toLong()) issues += RoomLayoutIssue("NARROW_AISLE", "Перед модулем «${spec.name}» проход ${aisle} мм, требуется ${request.minimumAisleMm} мм", spec.id)
        }

        val intervalStart = request.startOffsetMm.toLong()
        val intervalEnd = intervalStart + totalWidthLong
        room.openings.filter { it.side == request.wallSide }.forEach { opening ->
            val considered = (opening.kind == OpeningKind.DOOR && request.avoidDoors) || (opening.kind == OpeningKind.WINDOW && request.avoidWindows)
            val openingStart = opening.offsetMm.toLong()
            val openingEnd = openingStart + opening.widthMm.toLong()
            if (considered && openingStart < intervalEnd && openingEnd > intervalStart)
                issues += RoomLayoutIssue("OPENING_CONFLICT", "Композиция пересекает ${if (opening.kind == OpeningKind.DOOR) "дверной" else "оконный"} проём", opening.id)
        }
        val scopedExisting = project.furniture.filter { it.roomId == room.id }
        placements.forEach { p ->
            val spec = request.modules.first { it.id == p.moduleId }
            val candidate = FurnitureItem("layout-${p.moduleId}", spec.name, Size3Mm(spec.widthMm, spec.depthMm, spec.heightMm), p.center, p.rotationDeg, roomId = room.id)
            val all = listOf(candidate) + scopedExisting + placements.filter { it.moduleId != p.moduleId }.map { other ->
                val otherSpec = request.modules.first { it.id == other.moduleId }
                FurnitureItem("layout-${other.moduleId}", otherSpec.name, Size3Mm(otherSpec.widthMm, otherSpec.depthMm, otherSpec.heightMm), other.center, other.rotationDeg, roomId=room.id)
            }
            val collisions = GeometryEngine.validateFurniture(room, all).filter { it.code == GeometryIssueCode.COLLISION && it.objectId == candidate.id }
            if (collisions.isNotEmpty()) issues += RoomLayoutIssue("FURNITURE_COLLISION", "Модуль «${spec.name}» пересекается с существующей мебелью или соседним модулем", spec.id)
            if (request.avoidEngineering) room.engineering.filter { e ->
                val halfW = fwFor(spec, p.rotationDeg).first.toLong()/2L
                val halfD = fwFor(spec, p.rotationDeg).second.toLong()/2L
                val ex = e.position.x.toLong(); val ey = e.position.y.toLong()
                ex in (p.center.x.toLong()-halfW)..(p.center.x.toLong()+halfW) &&
                    ey in (p.center.y.toLong()-halfD)..(p.center.y.toLong()+halfD) && e.position.z.toLong() <= spec.heightMm.toLong()
            }.forEach { issues += RoomLayoutIssue("ENGINEERING_CONFLICT", "Модуль «${spec.name}» перекрывает инженерную точку «${it.name}»", it.id) }
        }
        return RoomLayoutResult(placements, issues.distinctBy { Triple(it.code,it.relatedId,it.message) }, totalWidthLong.toInt(), (availableLong-totalWidthLong).toInt(), request.minimumAisleMm)
    }

    private fun fwFor(spec: FurnitureModuleSpec, rotationDeg: Int): Pair<Int, Int> =
        GeometryEngine.rotatedFootprint(Size3Mm(spec.widthMm, spec.depthMm, spec.heightMm), rotationDeg)

    /** Commits a valid layout as room-owned furniture assemblies; invalid plans are never partially applied. */
    fun apply(project: ProjectModel, result: RoomLayoutResult, prefix: String = "layout"): ProjectModel {
        require(result.isValid) { result.issues.firstOrNull()?.message ?: "План размещения содержит ошибки" }
        val ids = result.placements.map { "$prefix-${it.moduleId}" }
        require(ids.distinct().size == ids.size && project.assemblies.none { it.id in ids }) { "ID создаваемых сборок уже используются" }
        val newAssemblies = result.placements.map { p ->
            val id = "$prefix-${p.moduleId}"
            val width = p.moduleSize.width.toLong()
            val depth = p.moduleSize.depth.toLong()
            val rotated = ((p.rotationDeg % 360) + 360) % 360
            require(rotated in setOf(0, 90, 180, 270)) { "Композицию можно ориентировать только по четвертям оборота" }
            require(width in 1L..ParametricDimensionLimits.MAX_MM.toLong() &&
                depth in 1L..ParametricDimensionLimits.MAX_MM.toLong()) { "Размеры модуля выходят за допустимый диапазон" }
            require(p.origin.x in -ParametricDimensionLimits.MAX_MM..ParametricDimensionLimits.MAX_MM &&
                p.origin.y in -ParametricDimensionLimits.MAX_MM..ParametricDimensionLimits.MAX_MM) {
                "Начало координат модуля выходит за допустимый диапазон"
            }
            val placedParts = p.assembly.parts.map { part ->
                val x = part.localPosition.x.toLong()
                val y = part.localPosition.y.toLong()
                val partWidth = part.size.width.toLong()
                val partDepth = part.size.depth.toLong()
                val (nextX, nextY, nextSize) = when (rotated) {
                    90 -> Triple(-y - partDepth / 2L - partWidth / 2L,
                        x + partWidth / 2L - partDepth / 2L, part.size)
                    180 -> Triple(-x - partWidth, -y - partDepth, part.size)
                    270 -> Triple(y + partDepth / 2L - partWidth / 2L,
                        -x - partWidth / 2L - partDepth / 2L, part.size)
                    else -> Triple(x, y, part.size)
                }
                require(nextX in -ParametricDimensionLimits.MAX_MM.toLong()..ParametricDimensionLimits.MAX_MM.toLong() &&
                    nextY in -ParametricDimensionLimits.MAX_MM.toLong()..ParametricDimensionLimits.MAX_MM.toLong()) {
                    "Поворот детали «${part.name}» выводит её за допустимые координаты"
                }
                val angle = ((part.rotationDeg.toLong() + rotated.toLong()) % 360L + 360L).rem(360L).toInt()
                part.copy(id = "$id-${part.id}", size = nextSize,
                    localPosition = Vec3Mm(nextX.toInt(), nextY.toInt(), part.localPosition.z), rotationDeg = angle)
            }
            val originZ = p.center.z.toLong() + p.assembly.position.z.toLong()
            require(originZ in 0L..ParametricDimensionLimits.MAX_MM.toLong()) { "Высота сборки выходит за допустимый диапазон" }
            p.assembly.copy(id = id, position = Vec3Mm(p.origin.x, p.origin.y, originZ.toInt()),
                parts = placedParts,
                joints = p.assembly.joints.map { j -> j.copy(id = "$id-${j.id}", firstPartId = "$id-${j.firstPartId}", secondPartId = "$id-${j.secondPartId}") })
        }
        return project.copy(assemblies = project.assemblies + newAssemblies)
    }
}
