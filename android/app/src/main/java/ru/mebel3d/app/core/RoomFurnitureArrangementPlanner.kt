package ru.mebel3d.app.core

/** A suggested furniture position. The planner never mutates a project. */
data class FurniturePlacementSuggestion(
    val furnitureId: String,
    val from: Vec3Mm,
    val to: Vec3Mm,
    val moved: Boolean
)

data class RoomFurnitureArrangementPlan(
    val roomId: String,
    val suggestions: List<FurniturePlacementSuggestion>,
    val unresolvedFurnitureIds: List<String>,
    val accessWarnings: List<PlanningAdvice>
) {
    val isComplete: Boolean get() = unresolvedFurnitureIds.isEmpty()
    val movedCount: Int get() = suggestions.count { it.moved }
}

/**
 * Interior-only arrangement assistant. It preserves object dimensions and rotation, searches
 * deterministic room-grid positions, avoids collisions and practical access zones, and returns
 * a previewable proposal. It does not generate manufacturing data or alter the source project.
 */
object RoomFurnitureArrangementPlanner {
    fun plan(
        project: ProjectModel,
        roomId: String = project.activeRoomId,
        gridMm: Int = 100,
        doorClearanceMm: Int = 900,
        windowClearanceMm: Int = 300,
        engineeringClearanceMm: Int = 180
    ): RoomFurnitureArrangementPlan {
        require(gridMm in 25..500) { "Шаг сетки должен быть от 25 до 500 мм" }
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val source = project.furniture.filter { it.roomId == roomId }
        val placed = mutableListOf<FurnitureItem>()
        val suggestions = mutableListOf<FurniturePlacementSuggestion>()
        val unresolved = mutableListOf<String>()

        // Large pieces first reduce the chance that a small object consumes a useful large area.
        val ordered = source.sortedWith(compareByDescending<FurnitureItem> { it.size.width.toLong() * it.size.depth }
            .thenBy { it.id })
        ordered.forEach { item ->
            val candidates = candidatePositions(room, item, gridMm)
                .sortedWith(compareBy<Pair<Int, Int>> { distanceSquared(it.first, it.second, item.position.x, item.position.y) }
                    .thenBy { it.second }.thenBy { it.first })
            val accepted = candidates.firstOrNull { (x, y) ->
                val candidate = item.copy(position = Vec3Mm(x, y, item.position.z))
                val scene = placed + candidate
                if (GeometryEngine.validateFurniture(room, scene).isNotEmpty()) return@firstOrNull false
                RoomPlanningDiagnostics.inspect(room, listOf(candidate), doorClearanceMm,
                    windowClearanceMm, engineeringClearanceMm).none { it.objectId == item.id }
            }
            if (accepted == null) {
                unresolved += item.id
                // Keep the original object in the preview scene so later suggestions remain honest.
                placed += item
                suggestions += FurniturePlacementSuggestion(item.id, item.position, item.position, false)
            } else {
                val target = Vec3Mm(accepted.first, accepted.second, item.position.z)
                placed += item.copy(position = target)
                suggestions += FurniturePlacementSuggestion(item.id, item.position, target, target != item.position)
            }
        }
        val warnings = RoomPlanningDiagnostics.inspect(room, placed, doorClearanceMm,
            windowClearanceMm, engineeringClearanceMm)
        return RoomFurnitureArrangementPlan(roomId, suggestions.sortedBy { it.furnitureId }, unresolved, warnings)
    }

    private fun candidatePositions(room: RoomModel, item: FurnitureItem, grid: Int): List<Pair<Int, Int>> {
        val (footW, footD) = GeometryEngine.rotatedFootprint(item)
        val minX = (footW / 2).coerceAtLeast(0)
        val maxX = room.widthMm - (footW + 1) / 2
        val minY = (footD / 2).coerceAtLeast(0)
        val maxY = room.depthMm - (footD + 1) / 2
        if (minX > maxX || minY > maxY) return emptyList()
        val points = LinkedHashSet<Pair<Int, Int>>()
        points += item.position.x.coerceIn(minX, maxX) to item.position.y.coerceIn(minY, maxY)
        var y = ((minY + grid - 1) / grid) * grid
        while (y <= maxY) {
            var x = ((minX + grid - 1) / grid) * grid
            while (x <= maxX) {
                points += x to y
                x += grid
            }
            y += grid
        }
        // Include exact wall-clearance positions even when the room dimensions are not grid-aligned.
        points += minX to minY
        points += maxX to minY
        points += minX to maxY
        points += maxX to maxY
        return points.toList()
    }

    private fun distanceSquared(x: Int, y: Int, ox: Int, oy: Int): Long {
        val dx = x.toLong() - ox
        val dy = y.toLong() - oy
        return dx * dx + dy * dy
    }
}
