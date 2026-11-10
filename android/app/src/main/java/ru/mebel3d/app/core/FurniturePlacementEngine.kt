package ru.mebel3d.app.core

import kotlin.math.abs
import kotlin.math.roundToInt

/** Result of a non-mutating placement preview. Coordinates are room-local millimetres. */
data class FurniturePlacementPreview(
    val candidate: FurnitureItem?,
    val snappedToWall: WallSide?,
    val issues: List<RoomLayoutIssue>,
    val snapDistanceMm: Int
) { val isValid: Boolean get() = candidate != null && issues.isEmpty() }

data class FurniturePlacementRequest(
    val furniture: FurnitureItem,
    val roomId: String,
    val desiredPosition: Vec3Mm,
    val desiredRotationDeg: Int = furniture.rotationDeg,
    val wallSnapToleranceMm: Int = 180,
    val gridMm: Int = 50,
    val keepClearanceFromOpeningsMm: Int = 0,
    val checkEngineering: Boolean = true
)

/** Shared, non-mutating placement pipeline for catalogue insertion, drag and numeric positioning. */
object FurniturePlacementEngine {
    private data class WallCandidate(val side: WallSide, val x: Int, val y: Int, val distance: Int, val rotation: Int)

    private fun normalize(degrees: Int) = ((degrees % 360) + 360) % 360
    private fun angleDistance(a: Int, b: Int): Int = minOf(abs(normalize(a) - normalize(b)), 360 - abs(normalize(a) - normalize(b)))
    private fun wallRotation(desired: Int, base: Int): Int = listOf(base, normalize(base + 180)).minBy { angleDistance(desired, it) }

    fun preview(project: ProjectModel, request: FurniturePlacementRequest): FurniturePlacementPreview {
        val room = project.rooms.firstOrNull { it.id == request.roomId }
            ?: return fail("ROOM_NOT_FOUND", "Помещение не найдено", request.roomId)
        if (request.wallSnapToleranceMm < 0 || request.gridMm < 0 || request.keepClearanceFromOpeningsMm < 0)
            return fail("INVALID_REQUEST", "Отступы и шаг сетки не могут быть отрицательными")

        val grid = request.gridMm
        fun snap(v: Int): Int = if (grid == 0) v else (v.toDouble() / grid).roundToInt() * grid
        val rotation = normalize(request.desiredRotationDeg)
        val x = snap(request.desiredPosition.x)
        val y = snap(request.desiredPosition.y)
        val northRot = wallRotation(rotation, 0)
        val eastRot = wallRotation(rotation, 90)
        val (nw, nd) = GeometryEngine.rotatedFootprint(request.furniture.size, northRot)
        val (ew, ed) = GeometryEngine.rotatedFootprint(request.furniture.size, eastRot)
        val walls = listOf(
            WallCandidate(WallSide.NORTH, x, nd / 2, abs(y - nd / 2), northRot),
            WallCandidate(WallSide.SOUTH, x, room.depthMm - nd / 2, abs(y - (room.depthMm - nd / 2)), northRot),
            WallCandidate(WallSide.WEST, ew / 2, y, abs(x - ew / 2), eastRot),
            WallCandidate(WallSide.EAST, room.widthMm - ew / 2, y, abs(x - (room.widthMm - ew / 2)), eastRot)
        ).filter { c -> c.distance <= request.wallSnapToleranceMm &&
            (if (c.side == WallSide.NORTH || c.side == WallSide.SOUTH) nw <= room.widthMm else ed <= room.depthMm) }
        val wall = walls.minByOrNull { it.distance }
        val px = wall?.x ?: x
        val py = wall?.y ?: y
        val finalRotation = wall?.rotation ?: rotation
        val z = request.desiredPosition.z
        val candidate = request.furniture.copy(roomId = room.id, position = Vec3Mm(px, py, z), rotationDeg = finalRotation)
        val issues = mutableListOf<RoomLayoutIssue>()
        val (cw, cd) = GeometryEngine.rotatedFootprint(candidate.size, candidate.rotationDeg)
        if (z < 0) issues += RoomLayoutIssue("BELOW_FLOOR", "Предмет находится ниже пола", candidate.id)
        if (px - cw / 2 < 0 || py - cd / 2 < 0 || px + cw / 2 > room.widthMm || py + cd / 2 > room.depthMm)
            issues += RoomLayoutIssue("OUT_OF_ROOM", "Предмет выходит за границы помещения", candidate.id)
        if (z + candidate.size.height > room.heightMm)
            issues += RoomLayoutIssue("ABOVE_CEILING", "Предмет выше потолка", candidate.id)

        val others = project.furniture.filter { it.roomId == room.id && it.id != candidate.id }
        val baselineCollisions = GeometryEngine.validateFurniture(room, others)
            .count { it.code == GeometryIssueCode.COLLISION }
        val combinedCollisions = GeometryEngine.validateFurniture(room, others + candidate)
            .count { it.code == GeometryIssueCode.COLLISION }
        if (combinedCollisions > baselineCollisions)
            issues += RoomLayoutIssue("FURNITURE_COLLISION", "Предмет пересекается с другой мебелью", candidate.id)

        room.openings.forEach { opening ->
            val clearance = request.keepClearanceFromOpeningsMm
            val alongCenter: Int
            val alongHalf: Int
            val reachesWall: Boolean
            when (opening.side) {
                WallSide.NORTH -> { alongCenter = px; alongHalf = cw / 2; reachesWall = py - cd / 2 <= clearance }
                WallSide.SOUTH -> { alongCenter = px; alongHalf = cw / 2; reachesWall = py + cd / 2 >= room.depthMm - clearance }
                WallSide.WEST -> { alongCenter = py; alongHalf = cd / 2; reachesWall = px - cw / 2 <= clearance }
                WallSide.EAST -> { alongCenter = py; alongHalf = cd / 2; reachesWall = px + cw / 2 >= room.widthMm - clearance }
            }
            val overlaps = alongCenter - alongHalf < opening.offsetMm + opening.widthMm + clearance &&
                alongCenter + alongHalf > opening.offsetMm - clearance
            val verticalOverlap = z < opening.sillHeightMm + opening.heightMm && z + candidate.size.height > opening.sillHeightMm
            if (reachesWall && overlaps && verticalOverlap)
                issues += RoomLayoutIssue("OPENING_CONFLICT", "Мебель перекрывает дверной или оконный проём", opening.id)
        }
        if (request.checkEngineering) room.engineering.filter { e ->
            e.position.x in (px - cw / 2)..(px + cw / 2) && e.position.y in (py - cd / 2)..(py + cd / 2) &&
                e.position.z in z..(z + candidate.size.height)
        }.forEach { issues += RoomLayoutIssue("ENGINEERING_CONFLICT", "Мебель перекрывает элемент коммуникаций «${it.name}»", it.id) }

        return FurniturePlacementPreview(candidate, wall?.side, issues.distinctBy { it.code to it.relatedId }, wall?.distance ?: 0)
    }

    private fun fail(code: String, message: String, related: String? = null) =
        FurniturePlacementPreview(null, null, listOf(RoomLayoutIssue(code, message, related)), 0)

    /** Commits only a valid preview and refuses stale previews or duplicate identifiers. */
    fun apply(project: ProjectModel, preview: FurniturePlacementPreview): ProjectModel {
        require(preview.isValid) { preview.issues.firstOrNull()?.message ?: "Сначала проверьте размещение" }
        val item = requireNotNull(preview.candidate)
        require(project.rooms.any { it.id == item.roomId }) { "Помещение для предмета больше не существует" }
        require(project.furniture.none { it.id == item.id }) { "Предмет с таким ID уже существует" }
        return project.copy(furniture = project.furniture + item)
    }
}
