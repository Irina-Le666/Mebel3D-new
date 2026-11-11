package ru.mebel3d.app.core

import kotlin.math.cos
import kotlin.math.sin

/** Non-blocking interior-planning advice. These findings never prevent saving a project. */
enum class PlanningAdviceKind { BLOCKED_DOOR, OBSTRUCTED_WINDOW, BLOCKED_ENGINEERING_ACCESS }

data class PlanningAdvice(
    val kind: PlanningAdviceKind,
    val objectId: String,
    val relatedId: String,
    val message: String
)

object RoomPlanningDiagnostics {
    /**
     * Finds furniture placed in practical access zones in front of doors, windows and
     * wall-mounted engineering markers. Dimensions are millimetres; rotated furniture
     * is tested as an oriented rectangle rather than its coarse axis-aligned bounds.
     */
    fun inspect(
        room: RoomModel,
        furniture: List<FurnitureItem>,
        doorClearanceMm: Int = 900,
        windowClearanceMm: Int = 300,
        engineeringClearanceMm: Int = 180
    ): List<PlanningAdvice> {
        require(doorClearanceMm >= 0 && windowClearanceMm >= 0 && engineeringClearanceMm >= 0)
        val advice = mutableListOf<PlanningAdvice>()
        room.openings.forEach { opening ->
            val isDoor = opening.kind == OpeningKind.DOOR
            val top = opening.sillHeightMm.toLong() + opening.heightMm.toLong()
            furniture.forEach furnitureLoop@ { item ->
                val itemBottom = item.position.z.toLong()
                val itemTop = itemBottom + item.size.height.toLong()
                val verticalOverlap = itemBottom < top && itemTop > opening.sillHeightMm.toLong()
                if (!verticalOverlap) return@furnitureLoop
                val clearance = if (isDoor) doorClearanceMm else windowClearanceMm
                val zone = openingZone(room, opening, clearance) ?: return@furnitureLoop
                if (intersects(item, zone)) {
                    advice += PlanningAdvice(
                        if (isDoor) PlanningAdviceKind.BLOCKED_DOOR else PlanningAdviceKind.OBSTRUCTED_WINDOW,
                        item.id,
                        opening.id,
                        if (isDoor) "«${item.name}» занимает зону прохода перед дверью" else "«${item.name}» может перекрывать окно"
                    )
                }
            }
        }
        room.engineering.filter { it.wallSide != null }.forEach { marker ->
            furniture.forEach furnitureLoop@ { item ->
                val markerHeight = marker.position.z.toLong()
                val markerTop = markerHeight + maxOf(80, marker.diameterMm).toLong()
                val itemBottom = item.position.z.toLong()
                val itemTop = itemBottom + item.size.height.toLong()
                if (itemBottom >= markerTop || itemTop <= markerHeight) return@furnitureLoop
                val zone = engineeringZone(room, marker, engineeringClearanceMm) ?: return@furnitureLoop
                if (intersects(item, zone)) {
                    advice += PlanningAdvice(
                        PlanningAdviceKind.BLOCKED_ENGINEERING_ACCESS,
                        item.id,
                        marker.id,
                        "«${item.name}» затрудняет доступ к элементу «${marker.name}»"
                    )
                }
            }
        }
        return advice.distinctBy { Triple(it.kind, it.objectId, it.relatedId) }
    }

    private data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double)

    private fun openingZone(room: RoomModel, opening: WallOpening, clearance: Int): Rect? {
        val start = opening.offsetMm.toDouble()
        val end = start + opening.widthMm
        val reach = clearance.toDouble()
        return when (opening.side) {
            WallSide.NORTH -> Rect(start, 0.0, end, minOf(room.depthMm.toDouble(), reach))
            WallSide.SOUTH -> Rect(start, room.depthMm - reach, end, room.depthMm.toDouble())
            WallSide.WEST -> Rect(0.0, start, minOf(room.widthMm.toDouble(), reach), end)
            WallSide.EAST -> Rect(room.widthMm - reach, start, room.widthMm.toDouble(), end)
        }
    }

    private fun engineeringZone(room: RoomModel, marker: EngineeringElement, clearance: Int): Rect? {
        val reach = clearance.toDouble()
        return when (marker.wallSide) {
            WallSide.NORTH -> Rect(marker.position.x - reach, 0.0, marker.position.x + reach, reach)
            WallSide.SOUTH -> Rect(marker.position.x - reach, room.depthMm - reach, marker.position.x + reach, room.depthMm.toDouble())
            WallSide.WEST -> Rect(0.0, marker.position.y - reach, reach, marker.position.y + reach)
            WallSide.EAST -> Rect(room.widthMm - reach, marker.position.y - reach, room.widthMm.toDouble(), marker.position.y + reach)
            null -> null
        }?.let { r ->
            Rect(r.left.coerceAtLeast(0.0), r.top.coerceAtLeast(0.0), r.right.coerceAtMost(room.widthMm.toDouble()), r.bottom.coerceAtMost(room.depthMm.toDouble()))
        }
    }

    private fun intersects(item: FurnitureItem, rect: Rect): Boolean {
        if (rect.left >= rect.right || rect.top >= rect.bottom) return false
        if (item.footprintShape == FurnitureFootprintShape.CIRCLE) {
            // Circular furniture is a disk, not its square bounding box. Testing the
            // nearest point avoids false warnings when only the box corners overlap.
            val radius = item.size.width / 2.0
            val nearestX = item.position.x.toDouble().coerceIn(rect.left, rect.right)
            val nearestY = item.position.y.toDouble().coerceIn(rect.top, rect.bottom)
            val dx = item.position.x - nearestX
            val dy = item.position.y - nearestY
            return dx * dx + dy * dy < radius * radius
        }
        val angle = Math.toRadians(item.rotationDeg.toDouble())
        val c = cos(angle); val s = sin(angle)
        val hw = item.size.width / 2.0; val hd = item.size.depth / 2.0
        val polygon = listOf(-hw to -hd, hw to -hd, hw to hd, -hw to hd).map { (x, y) ->
            (item.position.x + x * c - y * s) to (item.position.y + x * s + y * c)
        }
        val box = listOf(rect.left to rect.top, rect.right to rect.top, rect.right to rect.bottom, rect.left to rect.bottom)
        val axes = listOf(polygon, box).flatMap { points -> points.indices.map { i ->
            val p = points[i]; val q = points[(i + 1) % points.size]
            (-(q.second - p.second)) to (q.first - p.first)
        } }
        return axes.all { axis ->
            val p = polygon.map { it.first * axis.first + it.second * axis.second }
            val b = box.map { it.first * axis.first + it.second * axis.second }
            p.maxOrNull()!! > b.minOrNull()!! && b.maxOrNull()!! > p.minOrNull()!!
        }
    }
}
