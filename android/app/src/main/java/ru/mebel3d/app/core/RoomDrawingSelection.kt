package ru.mebel3d.app.core

/** Selection helpers for the schematic plan and wall views. Coordinates are room millimetres. */
object RoomDrawingSelection {
    fun selectPlanFurniture(plan: RoomPlanProjection, xMm: Double, yMm: Double): String? =
        plan.furniture.asSequence()
            .filter { contains(it.polygon, xMm, yMm) }
            .minByOrNull { polygonArea(it.polygon) }
            ?.id

    fun selectElevationFurniture(elevation: WallElevationProjection, alongMm: Double, heightMm: Double): String? =
        elevation.furniture.asSequence()
            .filter { item ->
                val b = item.bounds
                alongMm >= b.leftMm && alongMm <= b.rightMm &&
                    heightMm >= b.bottomMm && heightMm <= b.topMm
            }
            .minByOrNull { it.bounds.widthMm * it.bounds.heightMm }
            ?.id

    fun selectPlanOpening(plan: RoomPlanProjection, xMm: Double, yMm: Double, toleranceMm: Double): String? =
        plan.openings.asSequence().filter { opening ->
            val along = when (opening.side) {
                WallSide.NORTH, WallSide.SOUTH -> xMm
                WallSide.EAST, WallSide.WEST -> yMm
            }
            val wallDistance = when (opening.side) {
                WallSide.NORTH -> kotlin.math.abs(yMm)
                WallSide.SOUTH -> kotlin.math.abs(yMm - plan.depthMm)
                WallSide.WEST -> kotlin.math.abs(xMm)
                WallSide.EAST -> kotlin.math.abs(xMm - plan.widthMm)
            }
            along >= opening.startMm - toleranceMm && along <= opening.endMm + toleranceMm && wallDistance <= toleranceMm
        }.minByOrNull { it.endMm - it.startMm }?.id

    fun selectPlanEngineering(plan: RoomPlanProjection, xMm: Double, yMm: Double, toleranceMm: Double): String? =
        plan.engineering.asSequence().map { it to kotlin.math.hypot(it.point.xMm - xMm, it.point.yMm - yMm) }
            .filter { (_, distance) -> distance <= toleranceMm }
            .minByOrNull { it.second }?.first?.id

    fun selectElevationOpening(elevation: WallElevationProjection, alongMm: Double, heightMm: Double): String? =
        elevation.openings.asSequence().filter { item ->
            val b = item.bounds
            alongMm >= b.leftMm && alongMm <= b.rightMm && heightMm >= b.bottomMm && heightMm <= b.topMm
        }.minByOrNull { it.bounds.widthMm * it.bounds.heightMm }?.id

    fun selectElevationEngineering(elevation: WallElevationProjection, alongMm: Double, heightMm: Double, toleranceMm: Double): String? =
        elevation.engineering.asSequence().map { it to kotlin.math.hypot(it.alongMm - alongMm, it.elevationMm - heightMm) }
            .filter { (_, distance) -> distance <= toleranceMm }
            .minByOrNull { it.second }?.first?.id

    private fun contains(points: List<DrawingPoint2>, x: Double, y: Double): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            val cross = (x - a.xMm) * (b.yMm - a.yMm) - (y - a.yMm) * (b.xMm - a.xMm)
            val within = x >= minOf(a.xMm, b.xMm) && x <= maxOf(a.xMm, b.xMm) &&
                y >= minOf(a.yMm, b.yMm) && y <= maxOf(a.yMm, b.yMm)
            if (within && kotlin.math.abs(cross) < 0.0001) return true
            if ((a.yMm > y) != (b.yMm > y) &&
                x < (b.xMm - a.xMm) * (y - a.yMm) / (b.yMm - a.yMm) + a.xMm) inside = !inside
            j = i
        }
        return inside
    }

    private fun polygonArea(points: List<DrawingPoint2>): Double {
        if (points.size < 3) return Double.POSITIVE_INFINITY
        var sum = 0.0
        points.indices.forEach { i ->
            val next = points[(i + 1) % points.size]
            sum += points[i].xMm * next.yMm - next.xMm * points[i].yMm
        }
        return kotlin.math.abs(sum) / 2.0
    }
}
