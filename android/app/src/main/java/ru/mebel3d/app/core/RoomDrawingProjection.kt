package ru.mebel3d.app.core

import kotlin.math.cos
import kotlin.math.sin

/** Canonical geometry shared by dimensioned plan and four wall elevations. */
data class DrawingPoint2(val xMm: Double, val yMm: Double)
data class DrawingRect(val leftMm: Double, val bottomMm: Double, val rightMm: Double, val topMm: Double) {
    val widthMm: Double get() = rightMm - leftMm
    val heightMm: Double get() = topMm - bottomMm
}
data class PlanFurnitureProjection(val id: String, val name: String, val polygon: List<DrawingPoint2>, val bottomMm: Int, val topMm: Int)
data class PlanOpeningProjection(val id: String, val kind: OpeningKind, val side: WallSide, val startMm: Int, val endMm: Int)
data class PlanEngineeringProjection(val id: String, val kind: EngineeringKind, val point: DrawingPoint2, val elevationMm: Int, val wallSide: WallSide?)
data class RoomPlanProjection(
    val widthMm: Int,
    val depthMm: Int,
    val furniture: List<PlanFurnitureProjection>,
    val openings: List<PlanOpeningProjection>,
    val engineering: List<PlanEngineeringProjection>
)
data class ElevationOpeningProjection(val id: String, val kind: OpeningKind, val bounds: DrawingRect)
data class ElevationEngineeringProjection(val id: String, val kind: EngineeringKind, val alongMm: Int, val elevationMm: Int, val diameterMm: Int)
data class ElevationFurnitureProjection(val id: String, val name: String, val bounds: DrawingRect)
data class DrawingDimension(val label: String, val fromMm: Int, val toMm: Int) { val lengthMm: Int get() = toMm - fromMm }
data class WallElevationProjection(
    val side: WallSide,
    val lengthMm: Int,
    val heightMm: Int,
    val solidPanels: List<WallSegment>,
    val openings: List<ElevationOpeningProjection>,
    val engineering: List<ElevationEngineeringProjection>,
    val furniture: List<ElevationFurnitureProjection>,
    val horizontalDimensions: List<DrawingDimension>,
    val verticalDimensions: List<DrawingDimension>
)
data class RoomDrawingSet(val plan: RoomPlanProjection, val elevations: Map<WallSide, WallElevationProjection>)

/**
 * All drawings derive from room-space millimetre geometry. This is a projection layer only:
 * it never rounds, edits, or persists source geometry. Display code may scale to pixels later.
 */
object RoomDrawingProjection {
    fun build(room: RoomModel, furniture: List<FurnitureItem>): RoomDrawingSet = RoomDrawingSet(
        plan = plan(room, furniture),
        elevations = WallSide.values().associateWith { elevation(room, furniture, it) }
    )

    fun plan(room: RoomModel, furniture: List<FurnitureItem>): RoomPlanProjection {
        val projectedFurniture = furniture.filter { it.roomId == room.id }.map { item ->
            val angle = Math.toRadians(item.rotationDeg.toDouble())
            val c = cos(angle); val s = sin(angle)
            val hw = item.size.width / 2.0; val hd = item.size.depth / 2.0
            val polygon = listOf(-hw to -hd, hw to -hd, hw to hd, -hw to hd).map { (x, y) ->
                DrawingPoint2(item.position.x + x * c - y * s, item.position.y + x * s + y * c)
            }
            PlanFurnitureProjection(item.id, item.name, polygon, item.position.z, item.position.z + item.size.height)
        }
        return RoomPlanProjection(
            room.widthMm, room.depthMm, projectedFurniture,
            room.openings.filter { validOpening(room, it) }.map { opening ->
                PlanOpeningProjection(opening.id, opening.kind, opening.side, opening.offsetMm, opening.offsetMm + opening.widthMm)
            },
            room.engineering.map { marker -> PlanEngineeringProjection(
                marker.id, marker.kind, DrawingPoint2(marker.position.x.toDouble(), marker.position.y.toDouble()), marker.position.z, marker.wallSide
            ) }
        )
    }

    fun elevation(room: RoomModel, furniture: List<FurnitureItem>, side: WallSide): WallElevationProjection {
        val length = room.wallLength(side)
        val openings = room.openings.filter { it.side == side && validOpening(room, it) }.map { opening ->
            ElevationOpeningProjection(opening.id, opening.kind, DrawingRect(
                opening.offsetMm.toDouble(), opening.sillHeightMm.toDouble(),
                (opening.offsetMm + opening.widthMm).toDouble(), (opening.sillHeightMm + opening.heightMm).toDouble()
            ))
        }
        val engineering = room.engineering.filter { it.wallSide == side && onWall(room, it, side) }.map { marker ->
            ElevationEngineeringProjection(marker.id, marker.kind, along(marker, side), marker.position.z, marker.diameterMm)
        }
        val projectedFurniture = furniture.filter { it.roomId == room.id }.mapNotNull { item ->
            val polygon = rotatedFootprint(item)
            val wallDistance = when (side) {
                WallSide.NORTH -> polygon.minOf { it.yMm }
                WallSide.SOUTH -> room.depthMm - polygon.maxOf { it.yMm }
                WallSide.WEST -> polygon.minOf { it.xMm }
                WallSide.EAST -> room.widthMm - polygon.maxOf { it.xMm }
            }
            // Only furniture touching or within 150 mm of this wall appears in its elevation.
            if (wallDistance > 150.0 || wallDistance < -150.0) return@mapNotNull null
            val values = when (side) {
                WallSide.NORTH, WallSide.SOUTH -> polygon.map { it.xMm }
                WallSide.EAST, WallSide.WEST -> polygon.map { it.yMm }
            }
            val left = values.minOrNull()!!.coerceAtLeast(0.0)
            val right = values.maxOrNull()!!.coerceAtMost(length.toDouble())
            if (right <= left) return@mapNotNull null
            ElevationFurnitureProjection(item.id, item.name, DrawingRect(left, item.position.z.toDouble(), right, (item.position.z + item.size.height).toDouble()))
        }
        val horizontal = buildList {
            val sorted = openings.sortedBy { it.bounds.leftMm }
            var cursor = 0
            sorted.forEach { opening ->
                val start = opening.bounds.leftMm.toInt()
                val end = opening.bounds.rightMm.toInt()
                if (start > cursor) add(DrawingDimension("Простенок", cursor, start))
                add(DrawingDimension(if (opening.kind == OpeningKind.DOOR) "Ширина двери" else "Ширина окна", start, end))
                cursor = maxOf(cursor, end)
            }
            if (cursor < length) add(DrawingDimension("Остаток стены", cursor, length))
            if (isEmpty()) add(DrawingDimension("Стена", 0, length))
        }
        val vertical = buildList {
            add(DrawingDimension("Высота стены", 0, room.heightMm))
            openings.forEach { opening ->
                if (opening.kind == OpeningKind.WINDOW) add(DrawingDimension("Высота подоконника", 0, opening.bounds.bottomMm.toInt()))
                add(DrawingDimension("Высота проёма", opening.bounds.bottomMm.toInt(), opening.bounds.topMm.toInt()))
            }
        }
        return WallElevationProjection(
            side, length, room.heightMm, GeometryEngine.wallSegments(room, side), openings,
            engineering, projectedFurniture, horizontal, vertical
        )
    }

    private fun validOpening(room: RoomModel, opening: WallOpening): Boolean =
        opening.offsetMm >= 0 && opening.widthMm > 0 && opening.heightMm > 0 &&
            opening.offsetMm.toLong() + opening.widthMm.toLong() <= room.wallLength(opening.side).toLong() &&
            opening.sillHeightMm >= 0 && opening.sillHeightMm.toLong() + opening.heightMm.toLong() <= room.heightMm.toLong()

    private fun onWall(room: RoomModel, marker: EngineeringElement, side: WallSide): Boolean = when (side) {
        WallSide.NORTH -> marker.position.y == 0
        WallSide.SOUTH -> marker.position.y == room.depthMm
        WallSide.WEST -> marker.position.x == 0
        WallSide.EAST -> marker.position.x == room.widthMm
    }

    private fun along(marker: EngineeringElement, side: WallSide): Int =
        if (side == WallSide.NORTH || side == WallSide.SOUTH) marker.position.x else marker.position.y

    private fun rotatedFootprint(item: FurnitureItem): List<DrawingPoint2> {
        val angle = Math.toRadians(item.rotationDeg.toDouble())
        val c = cos(angle); val s = sin(angle)
        val hw = item.size.width / 2.0; val hd = item.size.depth / 2.0
        return listOf(-hw to -hd, hw to -hd, hw to hd, -hw to hd).map { (x, y) ->
            DrawingPoint2(item.position.x + x * c - y * s, item.position.y + x * s + y * c)
        }
    }
}
