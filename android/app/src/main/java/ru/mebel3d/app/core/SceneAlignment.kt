package ru.mebel3d.app.core

import kotlin.math.abs

/** A measured gap from the selected furniture footprint to a wall or another object. */
data class SceneGapGuide(
    val axis: String,
    val fromMm: Int,
    val toMm: Int,
    val distanceMm: Int,
    val label: String,
    val targetId: String? = null
)

/** A non-mutating suggestion for snapping the selected object's centre to an alignment line. */
data class FurnitureSnapSuggestion(
    val xMm: Int,
    val yMm: Int,
    val axis: String,
    val targetId: String?,
    val label: String,
    val distanceMm: Int
)

/** Scene-level measurement and alignment helpers. All values are room-local millimetres. */
object SceneAlignmentEngine {
    private data class Bounds(val left: Long, val right: Long, val near: Long, val far: Long) {
        val width: Long get() = right - left
        val depth: Long get() = far - near
    }

    private fun bounds(item: FurnitureItem): Bounds {
        val (w, d) = GeometryEngine.rotatedFootprint(item.size, item.rotationDeg)
        val halfW = w.toLong() / 2L
        val halfD = d.toLong() / 2L
        return Bounds(item.position.x.toLong() - halfW, item.position.x.toLong() + halfW,
            item.position.y.toLong() - halfD, item.position.y.toLong() + halfD)
    }

    private fun exactInt(value: Long, description: String): Int {
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "$description выходит за допустимый диапазон миллиметров"
        }
        return value.toInt()
    }

    /** Exact clearances to all four walls and nearest furniture on each cardinal side. */
    fun measure(project: ProjectModel, itemId: String): List<SceneGapGuide> {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val b = bounds(item)
        val roomW = room.widthMm.toLong()
        val roomD = room.depthMm.toLong()
        val guides = mutableListOf(
            SceneGapGuide("X", 0, exactInt(b.left, "Координата грани"), exactInt(b.left, "Зазор"), "До левой стены"),
            SceneGapGuide("X", exactInt(b.right, "Координата грани"), room.widthMm,
                exactInt(roomW - b.right, "Зазор"), "До правой стены"),
            SceneGapGuide("Y", 0, exactInt(b.near, "Координата грани"), exactInt(b.near, "Зазор"), "До дальней стены"),
            SceneGapGuide("Y", exactInt(b.far, "Координата грани"), room.depthMm,
                exactInt(roomD - b.far, "Зазор"), "До ближней стены")
        )
        project.furniture.filter { it.roomId == item.roomId && it.id != item.id }.forEach { other ->
            val o = bounds(other)
            val overlapY = b.near < o.far && b.far > o.near
            val overlapX = b.left < o.right && b.right > o.left
            if (overlapY && o.left >= b.right) guides += gap("X", b.right, o.left, "До ${other.name}", other.id)
            if (overlapY && o.right <= b.left) guides += gap("X", o.right, b.left, "До ${other.name}", other.id)
            if (overlapX && o.near >= b.far) guides += gap("Y", b.far, o.near, "До ${other.name}", other.id)
            if (overlapX && o.far <= b.near) guides += gap("Y", o.far, b.near, "До ${other.name}", other.id)
        }
        return guides.filter { it.distanceMm >= 0 }.sortedWith(compareBy<SceneGapGuide>({ it.distanceMm }, { it.targetId ?: "" }))
    }

    private fun gap(axis: String, from: Long, to: Long, label: String, targetId: String) = SceneGapGuide(
        axis, exactInt(from, "Координата грани"), exactInt(to, "Координата грани"),
        exactInt(to - from, "Зазор"), label, targetId
    )

    /**
     * Finds the closest edge/centre alignment within tolerance for a proposed centre.
     * Suggestions never mutate the project; the caller previews and validates the result.
     */
    fun suggestSnap(project: ProjectModel, itemId: String, desiredX: Int, desiredY: Int, toleranceMm: Int = 80): FurnitureSnapSuggestion? {
        require(toleranceMm >= 0) { "Допуск привязки не может быть отрицательным" }
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val current = bounds(item)
        val halfW = current.width / 2L
        val halfD = current.depth / 2L
        val candidates = mutableListOf<FurnitureSnapSuggestion>()
        fun add(axis: String, currentEdge: Long, targetEdge: Long, desiredCenter: Long, targetId: String?, label: String) {
            val delta = targetEdge - currentEdge
            val distance = abs(delta)
            if (distance <= toleranceMm.toLong()) {
                val x = if (axis == "X") desiredCenter + delta else desiredX.toLong()
                val y = if (axis == "Y") desiredCenter + delta else desiredY.toLong()
                if (x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() && y in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
                    candidates += FurnitureSnapSuggestion(x.toInt(), y.toInt(), axis, targetId, label, distance.toInt())
                }
            }
        }
        val dx = desiredX.toLong()
        val dy = desiredY.toLong()
        // Wall-edge alignment preserves the object's current footprint size.
        add("X", dx - halfW, 0L, dx, null, "Левая грань по стене")
        add("X", dx + halfW, room.widthMm.toLong(), dx, null, "Правая грань по стене")
        add("Y", dy - halfD, 0L, dy, null, "Дальняя грань по стене")
        add("Y", dy + halfD, room.depthMm.toLong(), dy, null, "Ближняя грань по стене")
        project.furniture.filter { it.roomId == item.roomId && it.id != item.id }.forEach { other ->
            val o = bounds(other)
            add("X", dx - halfW, o.left, dx, other.id, "Левые грани: ${other.name}")
            add("X", dx + halfW, o.right, dx, other.id, "Правые грани: ${other.name}")
            add("X", dx + halfW, o.left, dx, other.id, "Встык справа к ${other.name}")
            add("X", dx - halfW, o.right, dx, other.id, "Встык слева к ${other.name}")
            add("X", dx, other.position.x.toLong(), dx, other.id, "Центры по X: ${other.name}")
            add("Y", dy - halfD, o.near, dy, other.id, "Ближние грани: ${other.name}")
            add("Y", dy + halfD, o.far, dy, other.id, "Дальние грани: ${other.name}")
            add("Y", dy + halfD, o.near, dy, other.id, "Встык по Y к ${other.name}")
            add("Y", dy - halfD, o.far, dy, other.id, "Встык по Y к ${other.name}")
            add("Y", dy, other.position.y.toLong(), dy, other.id, "Центры по Y: ${other.name}")
        }
        return candidates.minWithOrNull(compareBy<FurnitureSnapSuggestion>({ it.distanceMm }, { if (it.targetId == null) 0 else 1 }))
    }

    /** Applies a suggestion only if standard room geometry validation accepts the move. */
    fun applySnap(project: ProjectModel, itemId: String, suggestion: FurnitureSnapSuggestion): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val next = ProjectEditor.moveFurniture(project, itemId, suggestion.xMm, suggestion.yMm, item.position.z, gridMm = 1)
        return next
    }
}
