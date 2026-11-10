package ru.mebel3d.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/** All dimensions are millimetres. X/Y describe the floor plane; Z is height. */
data class Vec3Mm(val x: Int, val y: Int, val z: Int = 0)

data class Size3Mm(val width: Int, val depth: Int, val height: Int) {
    init { require(width > 0 && depth > 0 && height > 0) }
}

enum class WallSide { NORTH, EAST, SOUTH, WEST }

data class WallOpening(
    val id: String,
    val side: WallSide,
    /** Distance from the left/bottom corner of the wall, in mm. */
    val offsetMm: Int,
    val widthMm: Int,
    val sillHeightMm: Int,
    val heightMm: Int,
    val kind: OpeningKind,
    /** Origin metadata is set only for geometry imported from a room-photo annotation. */
    val sourcePhotoId: String? = null,
    val sourceAnnotationId: String? = null
)

enum class OpeningKind { DOOR, WINDOW }

enum class EngineeringKind { OUTLET, SWITCH, WATER, GAS, DRAIN, RADIATOR, VENTILATION, PIPE }

data class EngineeringElement(
    val id: String,
    val name: String,
    val kind: EngineeringKind,
    val position: Vec3Mm,
    val wallSide: WallSide? = null,
    val diameterMm: Int = 0,
    val notes: String = "",
    /** Origin metadata is set only for geometry imported from a room-photo annotation. */
    val sourcePhotoId: String? = null,
    val sourceAnnotationId: String? = null
)

enum class PhotoAnnotationKind { DOOR, WINDOW, OUTLET, SWITCH, WATER, GAS, PIPE, OTHER }

data class PhotoAnnotation(
    val id: String,
    val kind: PhotoAnnotationKind,
    /** Rectangle in source-image pixels, origin at top-left. */
    val xPx: Int,
    val yPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val label: String = ""
)

data class RoomPhotoReference(
    val id: String,
    val uri: String,
    val wallSide: WallSide? = null,
    val knownLengthMm: Int? = null,
    val calibrationPixels: Int? = null,
    val imageWidthPx: Int? = null,
    val imageHeightPx: Int? = null,
    val annotations: List<PhotoAnnotation> = emptyList(),
    /** Pixel X coordinate of the wall origin; null preserves legacy left-edge origin. */
    val wallOriginXPx: Int? = null,
    /** Pixel Y coordinate of the floor line; null preserves legacy full-height calibration. */
    val floorLineYPx: Int? = null
)

data class RoomModel(
    val id: String = "room-main",
    val name: String = "Помещение 1",
    val widthMm: Int = 5000,
    val depthMm: Int = 3500,
    val heightMm: Int = 2700,
    val wallThicknessMm: Int = 120,
    val openings: List<WallOpening> = emptyList(),
    val engineering: List<EngineeringElement> = emptyList(),
    val photos: List<RoomPhotoReference> = emptyList(),
    val wallColorArgb: Long = 0xFFF0ECE5L,
    val floorColorArgb: Long = 0xFFEADCC8L,
    val lightingPreset: String = "Дневной",
    /** Optional catalog finish IDs; null preserves the exact legacy custom-color appearance. */
    val wallMaterialId: String? = null,
    val floorMaterialId: String? = null,
    val ceilingColorArgb: Long = 0xFFF8F7F3L,
    val ceilingMaterialId: String? = null,
    /** Optional independent finish for each wall; null inherits the room-wide wall finish. */
    val northWallMaterialId: String? = null,
    val eastWallMaterialId: String? = null,
    val southWallMaterialId: String? = null,
    val westWallMaterialId: String? = null,
    /** Scene illumination controls; independent of material colors and safe for legacy projects. */
    val lightIntensityPercent: Int = 72,
    val lightTemperatureKelvin: Int = 4200,
    val shadowStrengthPercent: Int = 35
) {
    init {
        require(widthMm > 0 && depthMm > 0 && heightMm > 0 && wallThicknessMm > 0)
    }

    fun wallLength(side: WallSide): Int = if (side == WallSide.NORTH || side == WallSide.SOUTH) widthMm else depthMm
}

enum class FurnitureFootprintShape { RECTANGLE, CIRCLE }

data class FurnitureItem(
    val id: String,
    val name: String,
    val size: Size3Mm,
    val position: Vec3Mm,
    val rotationDeg: Int = 0,
    val materialId: String = "wood-oak",
    val colorArgb: Long = 0xFFD8C2A5,
    /** Stable room ownership; defaults to the first room for legacy projects. */
    val roomId: String = "room-main",
    /** Legacy projects omit this field and remain rectangular. */
    val footprintShape: FurnitureFootprintShape = FurnitureFootprintShape.RECTANGLE
)

data class FurniturePart(
    val id: String,
    val name: String,
    val size: Size3Mm,
    val localPosition: Vec3Mm,
    val rotationDeg: Int = 0,
    val materialId: String = "wood-oak"
)

enum class JointKind { BUTT, SCREW, DOWEL, CONFIRMAT, MINIFIX, GROOVE, HINGE, DRAWER_SLIDE }

/** Manufacturing connection between two parts; all dimensions are millimetres. */
data class AssemblyJoint(
    val id: String,
    val kind: JointKind,
    val firstPartId: String,
    val secondPartId: String,
    val fastenerDiameterMm: Int = 0,
    val fastenerLengthMm: Int = 0,
    val edgeDistanceMm: Int = 0,
    val materialThicknessMm: Int = 0,
    val clearanceMm: Int = 0,
    val notes: String = "",
    /** Origin metadata is set only for geometry imported from a room-photo annotation. */
    val sourcePhotoId: String? = null,
    val sourceAnnotationId: String? = null
)

data class FurnitureAssembly(
    val id: String,
    val name: String,
    val position: Vec3Mm,
    val parts: List<FurniturePart>,
    val joints: List<AssemblyJoint> = emptyList()
)

/** Pure project-level operations for multi-room projects. Room IDs remain stable across edits. */
object RoomManager {
    fun addRoom(rooms: List<RoomModel>, room: RoomModel): List<RoomModel> {
        require(rooms.none { it.id == room.id }) { "Помещение с таким идентификатором уже существует" }
        return rooms + room
    }

    fun updateRoom(rooms: List<RoomModel>, room: RoomModel): List<RoomModel> {
        require(rooms.any { it.id == room.id }) { "Помещение не найдено" }
        require(GeometryEngine.validateRoom(room).isEmpty()) { "Параметры помещения содержат ошибки" }
        return rooms.map { if (it.id == room.id) room else it }
    }

    fun removeRoom(rooms: List<RoomModel>, roomId: String): List<RoomModel> {
        require(rooms.size > 1) { "В проекте должно остаться хотя бы одно помещение" }
        require(rooms.any { it.id == roomId }) { "Помещение не найдено" }
        return rooms.filterNot { it.id == roomId }
    }

    fun duplicateRoom(rooms: List<RoomModel>, sourceId: String, newId: String, newName: String): List<RoomModel> {
        val source = rooms.firstOrNull { it.id == sourceId } ?: error("Помещение не найдено")
        require(rooms.none { it.id == newId }) { "Помещение с таким идентификатором уже существует" }
        return rooms + source.copy(id = newId, name = newName,
            openings = source.openings.map { it.copy(id = "$newId-${it.id}") },
            engineering = source.engineering.map { it.copy(id = "$newId-${it.id}") },
            photos = source.photos.map { it.copy(id = "$newId-${it.id}") })
    }
}

data class ProjectModel(
    val id: String,
    val name: String,
    val rooms: List<RoomModel> = listOf(RoomModel()),
    val furniture: List<FurnitureItem> = emptyList(),
    val assemblies: List<FurnitureAssembly> = emptyList(),
    val activeRoomId: String = rooms.firstOrNull()?.id ?: "room-main"
)

data class WallSegment(val side: WallSide, val offsetMm: Int, val lengthMm: Int, val bottomMm: Int, val heightMm: Int)

data class FurnitureDimensions(
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
    val westClearanceMm: Int,
    val eastClearanceMm: Int,
    val northClearanceMm: Int,
    val southClearanceMm: Int
)

data class BoardCut(
    val assemblyId: String,
    val partId: String,
    val partName: String,
    val widthMm: Int,
    val lengthMm: Int,
    val thicknessMm: Int,
    val materialId: String
)

enum class GeometryIssueCode { OUT_OF_ROOM, BELOW_FLOOR, ABOVE_CEILING, COLLISION, INVALID_OPENING, INVALID_ENGINEERING, DUPLICATE_ROOM_ELEMENT_ID, INVALID_ASSEMBLY }
data class GeometryIssue(val code: GeometryIssueCode, val message: String, val objectId: String? = null)

object GeometryEngine {
    /** Axis-aligned footprint of a rotated rectangular object. */
    fun rotatedFootprint(size: Size3Mm, rotationDeg: Int): Pair<Int, Int> {
        val radians = Math.toRadians(rotationDeg.toDouble())
        val c = abs(cos(radians)); val s = abs(sin(radians))
        return (size.width * c + size.depth * s).roundToInt() to
            (size.width * s + size.depth * c).roundToInt()
    }

    /**
     * Builds a room-local marker position on the requested wall.
     * The room origin is NORTH-WEST: NORTH is Y=0, SOUTH is Y=depth.
     * NORTH/SOUTH offsets run along X; EAST/WEST offsets run along Y.
     */
    fun wallPosition(room: RoomModel, side: WallSide, alongMm: Int, elevationMm: Int): Vec3Mm {
        require(alongMm in 0..room.wallLength(side)) { "Отступ выходит за длину стены" }
        require(elevationMm in 0..room.heightMm) { "Высота выходит за пределы помещения" }
        return when (side) {
            WallSide.NORTH -> Vec3Mm(alongMm, 0, elevationMm)
            WallSide.SOUTH -> Vec3Mm(alongMm, room.depthMm, elevationMm)
            WallSide.EAST -> Vec3Mm(room.widthMm, alongMm, elevationMm)
            WallSide.WEST -> Vec3Mm(0, alongMm, elevationMm)
        }
    }

    fun validateRoom(room: RoomModel): List<GeometryIssue> {
        val issues = mutableListOf<GeometryIssue>()
        room.openings.forEach { opening ->
            val valid = opening.offsetMm >= 0 && opening.widthMm > 0 &&
                opening.offsetMm.toLong() + opening.widthMm.toLong() <= room.wallLength(opening.side).toLong() &&
                opening.sillHeightMm >= 0 && opening.heightMm > 0 &&
                opening.sillHeightMm.toLong() + opening.heightMm.toLong() <= room.heightMm.toLong() &&
                (opening.kind != OpeningKind.DOOR || opening.sillHeightMm == 0)
            if (!valid) issues += GeometryIssue(GeometryIssueCode.INVALID_OPENING, "Проём выходит за пределы стены или имеет неверную высоту", opening.id)
        }
        room.openings.groupBy { it.side }.values.forEach { sameWall ->
            sameWall.sortedBy { it.offsetMm }.zipWithNext().forEach { (a, b) ->
                if (a.offsetMm.toLong() + a.widthMm.toLong() > b.offsetMm.toLong()) {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_OPENING, "Проёмы на одной стене пересекаются", b.id)
                }
            }
        }
        room.engineering.forEach { element ->
            val onDeclaredWall = when (element.wallSide) {
                null -> true
                WallSide.NORTH -> element.position.y == 0
                WallSide.SOUTH -> element.position.y == room.depthMm
                WallSide.EAST -> element.position.x == room.widthMm
                WallSide.WEST -> element.position.x == 0
            }
            val valid = element.name.isNotBlank() && element.position.x in 0..room.widthMm && element.position.y in 0..room.depthMm &&
                element.position.z in 0..room.heightMm && element.diameterMm in 0..ParametricDimensionLimits.MAX_MM && onDeclaredWall
            if (!valid) issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING,
                "Инженерный элемент «${element.name}» расположен вне помещения, не совпадает с указанной стеной или имеет неверный размер", element.id)
        }
        // IDs are the stable keys used by selection, editing and photo transfer. They
        // must be unique across both wall openings and engineering markers.
        (room.openings.map { it.id } + room.engineering.map { it.id }).groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys.forEach { duplicateId ->
                issues += GeometryIssue(GeometryIssueCode.DUPLICATE_ROOM_ELEMENT_ID,
                    "Идентификатор элемента помещения повторяется", duplicateId)
            }
        return issues
    }

    fun validateFurniture(room: RoomModel, furniture: List<FurnitureItem>): List<GeometryIssue> {
        val issues = mutableListOf<GeometryIssue>()
        furniture.forEach { item ->
            // Validate the true rotated half-extents in millimetres. The rounded integer
            // footprint is useful for labels, but must not decide whether a placement fits:
            // rounding each full extent first can hide a fractional-mm boundary overflow.
            val angle = Math.toRadians(item.rotationDeg.toDouble())
            val halfWidth = (abs(cos(angle)) * item.size.width + abs(sin(angle)) * item.size.depth) / 2.0
            val halfDepth = (abs(sin(angle)) * item.size.width + abs(cos(angle)) * item.size.depth) / 2.0
            val left = item.position.x.toDouble() - halfWidth
            val right = item.position.x.toDouble() + halfWidth
            val near = item.position.y.toDouble() - halfDepth
            val far = item.position.y.toDouble() + halfDepth
            if (left < 0.0 || near < 0.0 || right > room.widthMm.toDouble() || far > room.depthMm.toDouble())
                issues += GeometryIssue(GeometryIssueCode.OUT_OF_ROOM, "Объект «${item.name}» выходит за границы помещения", item.id)
            if (item.position.z < 0)
                issues += GeometryIssue(GeometryIssueCode.BELOW_FLOOR, "Объект «${item.name}» ниже уровня пола", item.id)
            if (item.position.z.toLong() + item.size.height.toLong() > room.heightMm.toLong())
                issues += GeometryIssue(GeometryIssueCode.ABOVE_CEILING, "Объект «${item.name}» выше потолка", item.id)
        }
        for (i in furniture.indices) for (j in i + 1 until furniture.size) {
            val a = furniture[i]; val b = furniture[j]
            val overlapXY = footprintsIntersect(a, b)
            val overlapZ = a.position.z.toLong() < b.position.z.toLong() + b.size.height.toLong() &&
                b.position.z.toLong() < a.position.z.toLong() + a.size.height.toLong()
            if (overlapXY && overlapZ)
                issues += GeometryIssue(GeometryIssueCode.COLLISION, "Объекты «${a.name}» и «${b.name}» пересекаются", a.id)
        }
        return issues
    }

    /** Exact 2D separating-axis test for rotated rectangular furniture footprints. */
    private fun footprintsIntersect(a: FurnitureItem, b: FurnitureItem): Boolean {
        fun corners(item: FurnitureItem): List<Pair<Double, Double>> {
            val angle = Math.toRadians(item.rotationDeg.toDouble())
            val c = cos(angle); val s = sin(angle)
            val hw = item.size.width / 2.0; val hd = item.size.depth / 2.0
            return listOf(-hw to -hd, hw to -hd, hw to hd, -hw to hd).map { (x, y) ->
                (item.position.x + x * c - y * s) to (item.position.y + x * s + y * c)
            }
        }
        val ac = corners(a); val bc = corners(b)
        val axes = listOf(ac, bc).flatMap { polygon ->
            polygon.indices.map { i ->
                val p = polygon[i]; val q = polygon[(i + 1) % polygon.size]
                val dx = q.first - p.first; val dy = q.second - p.second
                (-dy) to dx
            }
        }
        return axes.all { axis ->
            fun projection(points: List<Pair<Double, Double>>) = points.map { it.first * axis.first + it.second * axis.second }
            val pa = projection(ac); val pb = projection(bc)
            pa.maxOrNull()!! > pb.minOrNull()!! && pb.maxOrNull()!! > pa.minOrNull()!!
        }
    }

    /** Returns a candidate move snapped to the requested grid; callers validate before committing. */
    fun snappedMove(item: FurnitureItem, xMm: Int, yMm: Int, gridMm: Int = 50): FurnitureItem =
        move(item, snap(xMm, gridMm), snap(yMm, gridMm))

    /** Rejects duplicate identifiers so selection and persistence remain deterministic. */
    fun upsertFurniture(items: List<FurnitureItem>, item: FurnitureItem): List<FurnitureItem> =
        items.filterNot { it.id == item.id } + item

    fun resizeRoom(room: RoomModel, widthMm: Int, depthMm: Int, heightMm: Int, wallThicknessMm: Int = room.wallThicknessMm): RoomModel {
        require(widthMm > 0 && depthMm > 0 && heightMm > 0 && wallThicknessMm > 0)
        return room.copy(widthMm = widthMm, depthMm = depthMm, heightMm = heightMm, wallThicknessMm = wallThicknessMm)
    }

    fun addOpening(room: RoomModel, opening: WallOpening): RoomModel {
        val updated = room.copy(openings = room.openings.filterNot { it.id == opening.id } + opening)
        require(validateRoom(updated).none { it.objectId == opening.id })
        return updated
    }

    fun addEngineeringElement(room: RoomModel, element: EngineeringElement): RoomModel {
        val updated = room.copy(engineering = room.engineering.filterNot { it.id == element.id } + element)
        require(validateRoom(updated).none { it.objectId == element.id })
        return updated
    }

    /** Replaces an engineering marker only after validating its coordinates and dimensions. */
    fun updateEngineeringElement(room: RoomModel, element: EngineeringElement): RoomModel {
        require(room.engineering.any { it.id == element.id }) { "Инженерный элемент не найден" }
        val updated = room.copy(engineering = room.engineering.map { if (it.id == element.id) element else it })
        require(validateRoom(updated).none { it.objectId == element.id }) { "Инженерный элемент имеет неверные координаты или размер" }
        return updated
    }

    fun removeEngineeringElement(room: RoomModel, elementId: String): RoomModel {
        require(room.engineering.any { it.id == elementId }) { "Инженерный элемент не найден" }
        return room.copy(engineering = room.engineering.filterNot { it.id == elementId })
    }

    fun duplicateEngineeringElement(room: RoomModel, elementId: String, newId: String, newName: String? = null): RoomModel {
        val source = room.engineering.firstOrNull { it.id == elementId } ?: error("Инженерный элемент не найден")
        require(newId.isNotBlank() && room.engineering.none { it.id == newId }) { "Новый идентификатор пуст или уже используется" }
        return addEngineeringElement(room, source.copy(id = newId, name = newName ?: "${source.name} — копия"))
    }

    /** Calibrates a wall photo from a known real-world length and its pixel measurement. */
    fun calibratePhoto(photo: RoomPhotoReference, knownLengthMm: Int, measuredPixels: Int): RoomPhotoReference {
        require(photo.uri.isNotBlank()) { "У фотографии отсутствует URI" }
        require(knownLengthMm > 0 && measuredPixels > 0) { "Размер и длина в пикселях должны быть положительными" }
        return photo.copy(knownLengthMm = knownLengthMm, calibrationPixels = measuredPixels)
    }

    fun addPhotoAnnotation(photo: RoomPhotoReference, annotation: PhotoAnnotation): RoomPhotoReference {
        require(photo.uri.isNotBlank()) { "У фотографии отсутствует URI" }
        require(annotation.id.isNotBlank()) { "У отметки должен быть ID" }
        require(photo.annotations.none { it.id == annotation.id }) { "Отметка с таким ID уже существует" }
        require(annotation.xPx >= 0 && annotation.yPx >= 0 && annotation.widthPx > 0 && annotation.heightPx > 0) {
            "Отметка должна иметь положительную область внутри изображения"
        }
        photo.imageWidthPx?.let { require(annotation.xPx.toLong() + annotation.widthPx.toLong() <= it.toLong()) { "Отметка выходит за правую границу фотографии" } }
        photo.imageHeightPx?.let { require(annotation.yPx.toLong() + annotation.heightPx.toLong() <= it.toLong()) { "Отметка выходит за нижнюю границу фотографии" } }
        return photo.copy(annotations = photo.annotations + annotation)
    }

    fun removePhotoAnnotation(photo: RoomPhotoReference, annotationId: String): RoomPhotoReference {
        require(photo.annotations.any { it.id == annotationId }) { "Отметка не найдена" }
        return photo.copy(annotations = photo.annotations.filterNot { it.id == annotationId })
    }

    fun updatePhotoAnnotation(photo: RoomPhotoReference, annotation: PhotoAnnotation): RoomPhotoReference {
        require(photo.annotations.any { it.id == annotation.id }) { "Отметка не найдена" }
        require(annotation.xPx >= 0 && annotation.yPx >= 0 && annotation.widthPx > 0 && annotation.heightPx > 0) {
            "Отметка должна иметь положительную область внутри изображения"
        }
        photo.imageWidthPx?.let { require(annotation.xPx.toLong() + annotation.widthPx.toLong() <= it.toLong()) { "Отметка выходит за правую границу фотографии" } }
        photo.imageHeightPx?.let { require(annotation.yPx.toLong() + annotation.heightPx.toLong() <= it.toLong()) { "Отметка выходит за нижнюю границу фотографии" } }
        return photo.copy(annotations = photo.annotations.map { if (it.id == annotation.id) annotation else it })
    }

    /** Converts a marked pixel rectangle to millimetres using the photo calibration. */
    fun annotationBoundsMm(photo: RoomPhotoReference, annotation: PhotoAnnotation): List<Int> {
        val scale = millimetresPerPixel(photo)
        return listOf(annotation.xPx, annotation.yPx, annotation.widthPx, annotation.heightPx).map { (it * scale).roundToInt() }
    }

    fun millimetresPerPixel(photo: RoomPhotoReference): Double {
        val length = requireNotNull(photo.knownLengthMm) { "Фотография не откалибрована" }
        val pixels = requireNotNull(photo.calibrationPixels) { "Фотография не откалибрована" }
        require(length > 0 && pixels > 0) { "Некорректные параметры калибровки" }
        return length.toDouble() / pixels
    }

    /** Splits a wall elevation into solid rectangular segments around its openings. */
    fun wallSegments(room: RoomModel, side: WallSide): List<WallSegment> {
        val length = room.wallLength(side).toLong()
        val height = room.heightMm.toLong()
        val openings = room.openings.filter { it.side == side }
            .filter { it.offsetMm >= 0 && it.widthMm > 0 && it.offsetMm.toLong() + it.widthMm.toLong() <= length }
            .sortedBy { it.offsetMm }
        val result = mutableListOf<WallSegment>()
        var cursor = 0L
        openings.forEach { opening ->
            val start = opening.offsetMm.toLong()
            val end = start + opening.widthMm.toLong()
            if (start > cursor)
                result += WallSegment(side, cursor.toInt(), (start - cursor).toInt(), 0, room.heightMm)
            if (opening.sillHeightMm > 0)
                result += WallSegment(side, opening.offsetMm, opening.widthMm, 0, opening.sillHeightMm)
            val topStart = opening.sillHeightMm.toLong() + opening.heightMm.toLong()
            if (topStart < height)
                result += WallSegment(side, opening.offsetMm, opening.widthMm, topStart.toInt(), (height - topStart).toInt())
            cursor = maxOf(cursor, end)
        }
        if (cursor < length) result += WallSegment(side, cursor.toInt(), (length - cursor).toInt(), 0, room.heightMm)
        return result
    }

    /** Converts assembly boards into a manufacturing cut list; dimensions are sorted for sheet planning. */
    fun cutList(assembly: FurnitureAssembly): List<BoardCut> = assembly.parts.map { part ->
        val dimensions = listOf(part.size.width, part.size.depth, part.size.height).sortedDescending()
        BoardCut(assembly.id, part.id, part.name, dimensions[0], dimensions[1], dimensions[2], part.materialId)
    }

    /** Snaps a coordinate to a grid in millimetres; grid must be positive. */
    fun snap(valueMm: Int, gridMm: Int = 50): Int {
        require(gridMm > 0)
        return (valueMm.toDouble() / gridMm).roundToInt() * gridMm
    }

    fun validateAssembly(assembly: FurnitureAssembly): List<GeometryIssue> {
        val ids = assembly.parts.map { it.id }
        val issues = mutableListOf<GeometryIssue>()
        if (ids.size != ids.toSet().size || assembly.parts.isEmpty())
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Сборка должна содержать детали с уникальными идентификаторами", assembly.id)
        assembly.parts.forEach { part ->
            if (part.size.width <= 0 || part.size.depth <= 0 || part.size.height <= 0 ||
                part.size.width > ParametricDimensionLimits.MAX_MM ||
                part.size.depth > ParametricDimensionLimits.MAX_MM ||
                part.size.height > ParametricDimensionLimits.MAX_MM)
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY,
                    "Деталь «${part.name}» имеет неверные или чрезмерные размеры", part.id)
            // X/Y may be negative for legitimate overhangs (countertops, trim and handles).
            // Z remains floor-relative and cannot be negative. Keep every coordinate bounded.
            val coordinateLimit = ParametricDimensionLimits.MAX_MM.toLong()
            if (part.localPosition.x.toLong() !in -coordinateLimit..coordinateLimit ||
                part.localPosition.y.toLong() !in -coordinateLimit..coordinateLimit ||
                part.localPosition.z !in 0..ParametricDimensionLimits.MAX_MM)
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY,
                    "Деталь «${part.name}» расположена за допустимыми границами сборки", part.id)
        }
        return issues
    }

    fun resize(item: FurnitureItem, widthMm: Int, depthMm: Int, heightMm: Int): FurnitureItem =
        item.copy(size = Size3Mm(widthMm, depthMm, heightMm))

    fun move(item: FurnitureItem, xMm: Int, yMm: Int, zMm: Int = item.position.z): FurnitureItem =
        item.copy(position = Vec3Mm(xMm, yMm, zMm))

    fun rotate(item: FurnitureItem, degrees: Int): FurnitureItem = item.copy(rotationDeg = ((degrees % 360) + 360) % 360)

    /** Rebuilds the standard cabinet carcass parametrically; part IDs and material choices are preserved. */
    fun resizeCabinetAssembly(
        assembly: FurnitureAssembly,
        widthMm: Int,
        depthMm: Int,
        heightMm: Int,
        thicknessMm: Int
    ): FurnitureAssembly {
        require(widthMm > thicknessMm * 2 && heightMm > thicknessMm * 2 && depthMm > 0 && thicknessMm > 0) {
            "Габариты шкафа должны быть больше удвоенной толщины материала"
        }
        val oldById = assembly.parts.associateBy { it.id }
        val requiredIds = setOf("left", "right", "bottom", "top", "back", "shelf")
        require(oldById.keys.containsAll(requiredIds)) { "Параметрическое изменение доступно для стандартного корпуса шкафа" }
        val rebuilt = createCabinetParts(widthMm, depthMm, heightMm, thicknessMm).map { fresh ->
            val old = oldById[fresh.id]!!
            fresh.copy(name = old.name, materialId = old.materialId, rotationDeg = old.rotationDeg)
        }
        return assembly.copy(parts = rebuilt)
    }

    /** Creates a cabinet-like assembly with independent, selectable board components. */
    fun createCabinetParts(width: Int, depth: Int, height: Int, thickness: Int): List<FurniturePart> {
        require(width > thickness * 2 && depth > 0 && height > thickness * 2 && thickness > 0)
        return listOf(
            FurniturePart("left", "Левая боковина", Size3Mm(thickness, depth, height), Vec3Mm(0, 0, 0)),
            FurniturePart("right", "Правая боковина", Size3Mm(thickness, depth, height), Vec3Mm(width - thickness, 0, 0)),
            FurniturePart("bottom", "Дно", Size3Mm(width - 2 * thickness, depth, thickness), Vec3Mm(thickness, 0, 0)),
            FurniturePart("top", "Крышка", Size3Mm(width - 2 * thickness, depth, thickness), Vec3Mm(thickness, 0, height - thickness)),
            FurniturePart("back", "Задняя стенка", Size3Mm(width - 2 * thickness, thickness, height - 2 * thickness), Vec3Mm(thickness, depth - thickness, thickness)),
            FurniturePart("shelf", "Полка", Size3Mm(width - 2 * thickness, depth - thickness, thickness), Vec3Mm(thickness, 0, height / 2))
        )
    }
}
