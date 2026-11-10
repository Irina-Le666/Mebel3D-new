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

    fun choose(hits: List<SceneHit>, maxDistancePx: Float = 30f): String? {
        if (maxDistancePx < 0f) return null
        val limit = maxDistancePx * maxDistancePx
        return hits.asSequence().filter { it.inside || it.distanceSquared <= limit }
            .sortedWith(compareBy<SceneHit>({ if (it.inside) 0 else 1 },
                { if (it.inside) it.regionArea else it.distanceSquared }, { it.regionArea }))
            .firstOrNull()?.id
    }
}
