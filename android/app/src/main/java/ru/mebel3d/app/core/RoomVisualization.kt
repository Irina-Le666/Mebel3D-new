package ru.mebel3d.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Camera and presentation state for the interior-design workspace. Angles are degrees. */
enum class RoomViewPreset { ISOMETRIC, TOP, FRONT, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST }
enum class WallDisplayMode { ALL_TRANSPARENT, CUTAWAY, SOLID_NEAR, HIDE_ROOF }
enum class LightingPreset { DAYLIGHT, WARM_EVENING, NEUTRAL, STUDIO }
enum class SurfaceFinish { MATTE, SATIN, GLOSS, WOOD_GRAIN, STONE, TILE, FABRIC, METAL }

data class RoomCameraState(
    val yawDeg: Float = 45f,
    val pitchDeg: Float = 32f,
    val zoom: Float = 1f,
    val targetXmm: Float? = null,
    val targetYmm: Float? = null,
    val targetZmm: Float? = null,
    val preset: RoomViewPreset = RoomViewPreset.ISOMETRIC
) {
    init { require(zoom in 0.25f..4f); require(pitchDeg in 5f..85f) }
}

data class RoomPresentationState(
    val camera: RoomCameraState = RoomCameraState(),
    val wallMode: WallDisplayMode = WallDisplayMode.ALL_TRANSPARENT,
    val lighting: LightingPreset = LightingPreset.DAYLIGHT,
    val showDimensions: Boolean = true,
    val showGrid: Boolean = true,
    val showLabels: Boolean = true,
    val showCeiling: Boolean = false,
    val selectedFinish: SurfaceFinish = SurfaceFinish.MATTE
)

data class ProjectMaterial(
    val id: String,
    val name: String,
    val finish: SurfaceFinish,
    val colorArgb: Long,
    val roughness: Float,
    val textureScaleMm: Int = 300
) {
    init { require(roughness in 0f..1f); require(textureScaleMm > 0) }
}

data class ScenePoint(val x: Float, val y: Float, val depth: Float)
data class WallVisual(val side: WallSide, val opacity: Float, val visible: Boolean, val cutaway: Boolean)

/** A solid wall fragment in wall-local coordinates; openings are intentionally absent. */
data class WallPanelRect(val offsetMm: Int, val bottomMm: Int, val widthMm: Int, val heightMm: Int)

object RoomVisualizationEngine {
    fun preset(preset: RoomViewPreset, previous: RoomCameraState = RoomCameraState()): RoomCameraState {
        val (yaw, pitch) = when (preset) {
            RoomViewPreset.ISOMETRIC -> 45f to 32f
            RoomViewPreset.TOP -> 0f to 85f
            RoomViewPreset.FRONT -> 0f to 12f
            RoomViewPreset.NORTH_EAST -> 45f to 28f
            RoomViewPreset.NORTH_WEST -> 315f to 28f
            RoomViewPreset.SOUTH_EAST -> 135f to 28f
            RoomViewPreset.SOUTH_WEST -> 225f to 28f
        }
        return previous.copy(yawDeg = yaw, pitchDeg = pitch, preset = preset)
    }

    fun orbit(camera: RoomCameraState, deltaYaw: Float, deltaPitch: Float): RoomCameraState = camera.copy(
        yawDeg = ((camera.yawDeg + deltaYaw) % 360f + 360f) % 360f,
        pitchDeg = (camera.pitchDeg + deltaPitch).coerceIn(5f, 85f),
        preset = RoomViewPreset.ISOMETRIC
    )

    fun zoom(camera: RoomCameraState, factor: Float): RoomCameraState = camera.copy(zoom = (camera.zoom * factor).coerceIn(0.25f, 4f))

    /** Camera-space orthographic projection; depth is returned for painter ordering. */
    fun project(room: RoomModel, camera: RoomCameraState, point: Vec3Mm): ScenePoint {
        val cx = camera.targetXmm ?: room.widthMm / 2f
        val cy = camera.targetYmm ?: room.depthMm / 2f
        val cz = camera.targetZmm ?: room.heightMm / 2f
        val yaw = camera.yawDeg * PI.toFloat() / 180f
        val pitch = camera.pitchDeg * PI.toFloat() / 180f
        val dx = point.x - cx
        val dy = point.y - cy
        val dz = point.z - cz
        val horizontal = dx * cos(yaw) - dy * sin(yaw)
        val depth = dx * sin(yaw) + dy * cos(yaw)
        val screenY = -depth * sin(pitch) + dz * cos(pitch)
        val screenDepth = depth * cos(pitch) + dz * sin(pitch)
        return ScenePoint(horizontal * camera.zoom, screenY * camera.zoom, screenDepth)
    }

    /** Camera-space depth at the centre of a furniture footprint; larger values are closer to the camera. */
    fun furnitureDepth(room: RoomModel, camera: RoomCameraState, item: FurnitureItem): Float =
        project(room, camera, Vec3Mm(item.position.x, item.position.y, item.position.z + item.size.height / 2)).depth

    /** Painter order for a flat Canvas renderer: far objects first, near objects last. */
    fun furnitureBackToFront(room: RoomModel, camera: RoomCameraState, items: List<FurnitureItem>): List<FurnitureItem> =
        items.sortedWith(compareBy<FurnitureItem> { furnitureDepth(room, camera, it) }.thenBy { it.id })

    /** Camera-space depth for an individual parametric board. */
    fun partDepth(room: RoomModel, camera: RoomCameraState, assembly: FurnitureAssembly, part: FurniturePart): Float =
        project(room, camera, Vec3Mm(
            assembly.position.x + part.localPosition.x + part.size.width / 2,
            assembly.position.y + part.localPosition.y + part.size.depth / 2,
            assembly.position.z + part.localPosition.z + part.size.height / 2
        )).depth

    /** Canonical room-space corners for a wall opening, ordered around its rectangle. */
    fun openingCorners(room: RoomModel, opening: WallOpening): List<Vec3Mm>? {
        if (opening.widthMm <= 0 || opening.heightMm <= 0) return null
        val start = opening.offsetMm.toLong()
        val end = start + opening.widthMm.toLong()
        val bottom = opening.sillHeightMm.toLong()
        val top = bottom + opening.heightMm.toLong()
        if (listOf(start, end, bottom, top).any { it !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }) return null
        val a = start.toInt(); val b = end.toInt()
        val low = bottom.toInt(); val high = top.toInt()
        return when (opening.side) {
            WallSide.NORTH -> listOf(Vec3Mm(a, 0, low), Vec3Mm(b, 0, low), Vec3Mm(b, 0, high), Vec3Mm(a, 0, high))
            WallSide.SOUTH -> listOf(Vec3Mm(a, room.depthMm, low), Vec3Mm(b, room.depthMm, low), Vec3Mm(b, room.depthMm, high), Vec3Mm(a, room.depthMm, high))
            WallSide.WEST -> listOf(Vec3Mm(0, a, low), Vec3Mm(0, b, low), Vec3Mm(0, b, high), Vec3Mm(0, a, high))
            WallSide.EAST -> listOf(Vec3Mm(room.widthMm, a, low), Vec3Mm(room.widthMm, b, low), Vec3Mm(room.widthMm, b, high), Vec3Mm(room.widthMm, a, high))
        }
    }

    /**
     * Wall-mounted engineering marker corners in room coordinates. The marker is a small
     * upright plaque attached to the declared wall plane; its width follows the wall and
     * its height follows room Z. A null wallSide keeps legacy/free-standing markers valid.
     */
    fun engineeringMarkerCorners(
        room: RoomModel,
        element: EngineeringElement,
        halfWidthMm: Int = 90,
        halfHeightMm: Int = 90
    ): List<Vec3Mm>? {
        if (halfWidthMm <= 0 || halfHeightMm <= 0) return null
        val tangent = when (element.wallSide) {
            WallSide.NORTH, null -> 1L to 0L
            WallSide.SOUTH -> -1L to 0L
            WallSide.EAST -> 0L to 1L
            WallSide.WEST -> 0L to -1L
        }
        val x = element.position.x.toLong()
        val y = element.position.y.toLong()
        val z = element.position.z.toLong()
        val halfW = halfWidthMm.toLong()
        val halfH = halfHeightMm.toLong()
        val raw = listOf(
            Triple(x - tangent.first * halfW, y - tangent.second * halfW, z - halfH),
            Triple(x + tangent.first * halfW, y + tangent.second * halfW, z - halfH),
            Triple(x + tangent.first * halfW, y + tangent.second * halfW, z + halfH),
            Triple(x - tangent.first * halfW, y - tangent.second * halfW, z + halfH)
        )
        if (raw.any { (px, py, pz) -> px !in 0L..room.widthMm.toLong() || py !in 0L..room.depthMm.toLong() || pz !in 0L..room.heightMm.toLong() }) return null
        return raw.map { (px, py, pz) -> Vec3Mm(px.toInt(), py.toInt(), pz.toInt()) }
    }

    /** Glazing/door leaves remain subtle so the actual wall cutout and room beyond stay visible. */
    fun openingFillOpacity(kind: OpeningKind, selected: Boolean): Float = when {
        selected -> 0.22f
        kind == OpeningKind.WINDOW -> 0.10f
        else -> 0.045f
    }

    /**
     * Subtracts all valid openings from a wall and returns only the remaining solid panels.
     * A coordinate-grid decomposition handles overlapping openings without double-filling them.
     * Invalid/out-of-wall openings are clipped defensively; project validation reports them separately.
     */
    fun wallPanels(wallLengthMm: Int, wallHeightMm: Int, openings: List<WallOpening>): List<WallPanelRect> {
        if (wallLengthMm <= 0 || wallHeightMm <= 0) return emptyList()
        val clipped = openings.mapNotNull { opening ->
            if (opening.widthMm <= 0 || opening.heightMm <= 0) return@mapNotNull null
            val left = opening.offsetMm.toLong().coerceAtLeast(0L).coerceAtMost(wallLengthMm.toLong())
            val right = (opening.offsetMm.toLong() + opening.widthMm.toLong()).coerceAtLeast(0L).coerceAtMost(wallLengthMm.toLong())
            val bottom = opening.sillHeightMm.toLong().coerceAtLeast(0L).coerceAtMost(wallHeightMm.toLong())
            val top = (opening.sillHeightMm.toLong() + opening.heightMm.toLong()).coerceAtLeast(0L).coerceAtMost(wallHeightMm.toLong())
            if (right <= left || top <= bottom) null else longArrayOf(left, right, bottom, top)
        }
        val zs = (listOf(0L, wallHeightMm.toLong()) + clipped.flatMap { listOf(it[2], it[3]) }).distinct().sorted()
        val result = mutableListOf<WallPanelRect>()
        for (zi in 0 until zs.lastIndex) {
            val bottom = zs[zi]
            val top = zs[zi + 1]
            if (top <= bottom) continue
            // Merge active horizontal opening intervals into a union for this height band.
            // This avoids an O(openings^3) cell-by-cell subtraction on imported room plans.
            val active = clipped.asSequence()
                .filter { it[2] < top && it[3] > bottom }
                .map { it[0] to it[1] }
                .sortedBy { it.first }
                .toList()
            val merged = mutableListOf<Pair<Long, Long>>()
            active.forEach { interval ->
                val last = merged.lastOrNull()
                if (last == null || interval.first > last.second) merged += interval
                else if (interval.second > last.second) merged[merged.lastIndex] = last.first to interval.second
            }
            var cursor = 0L
            merged.forEach { opening ->
                if (opening.first > cursor) result += WallPanelRect(
                    cursor.toInt(), bottom.toInt(), (opening.first - cursor).toInt(), (top - bottom).toInt()
                )
                cursor = maxOf(cursor, opening.second)
            }
            if (cursor < wallLengthMm.toLong()) result += WallPanelRect(
                cursor.toInt(), bottom.toInt(), (wallLengthMm.toLong() - cursor).toInt(), (top - bottom).toInt()
            )
        }
        return result
    }

    /** Four walls remain available in cutaway/transparent modes instead of disappearing behind the camera. */
    fun wallVisuals(camera: RoomCameraState, mode: WallDisplayMode): List<WallVisual> = WallSide.values().map { side ->
        // Projection uses depth = dx*sin(yaw) + dy*cos(yaw): positive depth
        // points toward the camera. Therefore yaw 0° sees SOUTH, 90° sees EAST,
        // 180° sees NORTH and 270° sees WEST. Keep this mapping in sync with project().
        val angle = when (side) {
            WallSide.NORTH -> 180f
            WallSide.EAST -> 90f
            WallSide.SOUTH -> 0f
            WallSide.WEST -> 270f
        }
        val delta = angularDistance(camera.yawDeg, angle)
        val near = delta < 90f
        when (mode) {
            WallDisplayMode.ALL_TRANSPARENT -> WallVisual(side, if (near) 0.72f else 0.20f, true, !near)
            WallDisplayMode.CUTAWAY -> WallVisual(side, if (near) 0.88f else 0.12f, true, !near)
            WallDisplayMode.SOLID_NEAR -> WallVisual(side, if (near) 1f else 0f, near, false)
            WallDisplayMode.HIDE_ROOF -> WallVisual(side, if (near) 0.9f else 0.28f, true, !near)
        }
    }

    fun daylightFactor(preset: LightingPreset): Float = when (preset) {
        LightingPreset.DAYLIGHT -> 1.0f
        LightingPreset.WARM_EVENING -> 0.78f
        LightingPreset.NEUTRAL -> 0.9f
        LightingPreset.STUDIO -> 1.12f
    }

    fun fitZoom(room: RoomModel, viewportWidth: Float, viewportHeight: Float, margin: Float = 0.88f): Float {
        require(viewportWidth > 0f && viewportHeight > 0f && margin in 0.1f..1f)
        val diagonal = sqrt((room.widthMm * room.widthMm + room.depthMm * room.depthMm + room.heightMm * room.heightMm).toFloat())
        return (minOf(viewportWidth, viewportHeight) * margin / diagonal).coerceIn(0.0001f, 4f)
    }

    private fun angularDistance(a: Float, b: Float): Float {
        val d = abs(((a - b + 180f) % 360f + 360f) % 360f - 180f)
        return d
    }
}

object InteriorMaterialCatalog {
    val materials = listOf(
        ProjectMaterial("wall-warm-white", "Тёплый белый", SurfaceFinish.MATTE, 0xFFF1EEE8, 0.92f),
        ProjectMaterial("wall-greige", "Светлый greige", SurfaceFinish.MATTE, 0xFFDCD5CA, 0.9f),
        ProjectMaterial("oak-natural", "Дуб натуральный", SurfaceFinish.WOOD_GRAIN, 0xFFC9A77F, 0.62f, 450),
        ProjectMaterial("walnut", "Орех", SurfaceFinish.WOOD_GRAIN, 0xFF79543A, 0.58f, 450),
        ProjectMaterial("stone-light", "Светлый камень", SurfaceFinish.STONE, 0xFFE1DED7, 0.38f),
        ProjectMaterial("tile-white", "Белая плитка", SurfaceFinish.TILE, 0xFFF7F6F2, 0.28f, 200),
        ProjectMaterial("fabric-sand", "Ткань песочная", SurfaceFinish.FABRIC, 0xFFCBBBA5, 0.96f),
        ProjectMaterial("metal-black", "Чёрный металл", SurfaceFinish.METAL, 0xFF34383D, 0.3f)
    )

    fun find(id: String): ProjectMaterial? = materials.firstOrNull { it.id == id }
}
