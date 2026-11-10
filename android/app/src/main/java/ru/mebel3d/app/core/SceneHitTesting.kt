package ru.mebel3d.app.core

import kotlin.math.max
import kotlin.math.min

/** Screen-space hit region used by the scene selector. Coordinates are pixels. */
data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init { require(left <= right && top <= bottom) }
    val area: Float get() = (right - left).coerceAtLeast(1f) * (bottom - top).coerceAtLeast(1f)
}

data class SceneHit(val id: String, val distanceSquared: Float, val regionArea: Float, val inside: Boolean)

/** Builds a stable selection score: hits inside a visible object's bounds beat nearby misses,
 * and the smaller visible object wins when selectable regions overlap. */
object SceneHitTesting {
    fun bounds(points: List<Pair<Float, Float>>, paddingPx: Float = 0f): ScreenRect? {
        if (points.isEmpty() || paddingPx < 0f) return null
        val xs = points.map { it.first }; val ys = points.map { it.second }
        return ScreenRect((xs.minOrNull() ?: return null) - paddingPx,
            (ys.minOrNull() ?: return null) - paddingPx,
            (xs.maxOrNull() ?: return null) + paddingPx,
            (ys.maxOrNull() ?: return null) + paddingPx)
    }

    fun score(id: String, x: Float, y: Float, rect: ScreenRect): SceneHit {
        val inside = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
        val dx = max(max(rect.left - x, 0f), x - rect.right)
        val dy = max(max(rect.top - y, 0f), y - rect.bottom)
        return SceneHit(id, dx * dx + dy * dy, rect.area, inside)
    }


    /** Scores the projected convex silhouette, not its axis-aligned bounding rectangle. */
    fun scorePolygon(id: String, x: Float, y: Float, points: List<Pair<Float, Float>>): SceneHit? {
        if (!x.isFinite() || !y.isFinite() || points.size < 3 ||
            points.any { !it.first.isFinite() || !it.second.isFinite() }) return null
        val hull = convexHull(points)
        if (hull.size < 3) return null
        val rect = bounds(hull) ?: return null
        val inside = contains(hull, x, y)
        var distanceSquared = 0f
        if (!inside) {
            var best = Float.POSITIVE_INFINITY
            for (i in hull.indices) {
                val a = hull[i]
                val b = hull[(i + 1) % hull.size]
                val dx = b.first - a.first
                val dy = b.second - a.second
                val lengthSquared = dx * dx + dy * dy
                val t = if (lengthSquared <= 0f) 0f else
                    (((x - a.first) * dx + (y - a.second) * dy) / lengthSquared).coerceIn(0f, 1f)
                val ex = x - (a.first + t * dx)
                val ey = y - (a.second + t * dy)
                best = min(best, ex * ex + ey * ey)
            }
            distanceSquared = best
        }
        return SceneHit(id, distanceSquared, rect.area, inside)
    }

    private fun convexHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = points.distinct().sortedWith(compareBy<Pair<Float, Float>> { it.first }.thenBy { it.second })
        if (sorted.size <= 2) return sorted
        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = mutableListOf<Pair<Float, Float>>()
        sorted.forEach { point ->
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0f) lower.removeAt(lower.lastIndex)
            lower += point
        }
        val upper = mutableListOf<Pair<Float, Float>>()
        sorted.asReversed().forEach { point ->
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0f) upper.removeAt(upper.lastIndex)
            upper += point
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun contains(polygon: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            val crosses = (a.second > y) != (b.second > y) &&
                x < (b.first - a.first) * (y - a.second) / (b.second - a.second) + a.first
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    fun choose(hits: List<SceneHit>, maxDistancePx: Float = 30f): String? {
        if (maxDistancePx < 0f) return null
        val limit = maxDistancePx * maxDistancePx
        return hits.asSequence().filter { it.inside || it.distanceSquared <= limit }
            .sortedWith(compareBy<SceneHit>({ if (it.inside) 0 else 1 },
                { if (it.inside) it.regionArea else it.distanceSquared }, { it.regionArea }))
            .firstOrNull()?.id
    }
}
