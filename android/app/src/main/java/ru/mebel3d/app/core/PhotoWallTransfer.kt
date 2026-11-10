package ru.mebel3d.app.core


/** Result of transferring manually marked photo rectangles into editable room geometry. */
data class PhotoTransferResult(
    val project: ProjectModel,
    val createdOpenings: List<String>,
    val createdEngineering: List<String>,
    val ignoredAnnotations: List<String>
)

enum class PhotoTransferSkipReason { NO_WALL, NEEDS_CALIBRATION, INVALID_TRANSFER }

data class RoomPhotoTransferResult(
    val project: ProjectModel,
    val createdOpenings: List<String>,
    val createdEngineering: List<String>,
    val ignoredAnnotations: List<String>,
    val skippedPhotoIds: List<String>,
    val skippedPhotoReasons: Map<String, PhotoTransferSkipReason> = emptyMap()
)

/** Non-destructive estimate of what a room-wide photo import would do. */
data class PhotoTransferSyncResult(
    val project: ProjectModel,
    val updatedIds: List<String>,
    val detachedIds: List<String>,
    val conflicts: List<String>
)

data class PhotoTransferPreview(
    val annotatedPhotoCount: Int,
    val readyPhotoCount: Int,
    val skippedPhotoCount: Int,
    val plannedOpeningCount: Int,
    val plannedEngineeringCount: Int,
    val ignoredAnnotationCount: Int,
    val skippedReasons: Map<PhotoTransferSkipReason, Int>
) {
    val plannedElementCount: Int get() = plannedOpeningCount + plannedEngineeringCount
    val hasUnresolvedMarks: Boolean get() = skippedPhotoCount > 0 || ignoredAnnotationCount > 0
}

/**
 * Converts annotations from a calibrated, wall-aligned photograph into room coordinates.
 * Image Y is top-down. The wall-origin anchor is the pixel where canonical wall coordinate
 * zero lies: image-left for NORTH/EAST, image-right for SOUTH/WEST when photographed from
 * inside the room. Perspective correction is intentionally not guessed: the user must
 * calibrate a wall-aligned view.
 */
object PhotoWallTransfer {
    /**
     * Runs the exact room-wide conversion rules on an isolated immutable project snapshot.
     * The caller receives counts only; the supplied project is never modified or persisted.
     */
    fun preview(project: ProjectModel, roomId: String): PhotoTransferPreview {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val annotated = room.photos.filter { it.annotations.isNotEmpty() }
        val result = transferAll(project, roomId)
        return PhotoTransferPreview(
            annotatedPhotoCount = annotated.size,
            readyPhotoCount = annotated.size - result.skippedPhotoIds.size,
            skippedPhotoCount = result.skippedPhotoIds.size,
            plannedOpeningCount = result.createdOpenings.size,
            plannedEngineeringCount = result.createdEngineering.size,
            ignoredAnnotationCount = result.ignoredAnnotations.size,
            skippedReasons = result.skippedPhotoReasons.values.groupingBy { it }.eachCount()
        )
    }

    /** Transfers every eligible annotated photo in one room, preserving per-photo idempotency. */
    fun transferAll(project: ProjectModel, roomId: String): RoomPhotoTransferResult {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        var working = project
        val openings = mutableListOf<String>()
        val engineering = mutableListOf<String>()
        val ignored = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val skipReasons = linkedMapOf<String, PhotoTransferSkipReason>()
        room.photos.filter { it.annotations.isNotEmpty() }.forEach { photo ->
            val reason = when {
                photo.wallSide == null -> PhotoTransferSkipReason.NO_WALL
                !PhotoWallCoordinateMapper.isTransferReady(photo) -> PhotoTransferSkipReason.NEEDS_CALIBRATION
                else -> null
            }
            if (reason != null) {
                skipped += photo.id
                skipReasons[photo.id] = reason
            } else {
                val result = runCatching { transfer(working, roomId, photo.id) }.getOrNull()
                if (result == null) {
                    skipped += photo.id
                    skipReasons[photo.id] = PhotoTransferSkipReason.INVALID_TRANSFER
                } else {
                    working = result.project
                    openings += result.createdOpenings
                    engineering += result.createdEngineering
                    ignored += result.ignoredAnnotations
                }
            }
        }
        return RoomPhotoTransferResult(working, openings, engineering, ignored, skipped, skipReasons)
    }

    /**
     * Reconciles already-imported geometry with the current annotation rectangles.
     * Only objects carrying matching provenance are touched. Removed marks are detached,
     * not deleted, so a user-edited opening or utility point is never silently lost.
     * Invalid or type-changed marks remain untouched and are returned as conflicts.
     */
    fun synchronize(project: ProjectModel, roomId: String, photoId: String): PhotoTransferSyncResult {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val annotations = photo.annotations.associateBy { it.id }
        val updated = mutableListOf<String>()
        val detached = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val openings = room.openings.map { opening ->
            if (opening.sourcePhotoId != photoId) return@map opening
            val markId = opening.sourceAnnotationId
            val mark = markId?.let(annotations::get)
            if (mark == null) {
                detached += opening.id
                opening.copy(sourcePhotoId = null, sourceAnnotationId = null)
            } else if (mark.kind != PhotoAnnotationKind.DOOR && mark.kind != PhotoAnnotationKind.WINDOW) {
                conflicts += opening.id
                opening
            } else {
                val bounds = PhotoWallCoordinateMapper.annotationBounds(room, photo, mark)
                val side = photo.wallSide
                if (bounds == null || side == null || bounds.offsetMm < 0 || bounds.widthMm <= 0 ||
                    bounds.offsetMm + bounds.widthMm > room.wallLength(side) ||
                    (mark.kind == PhotoAnnotationKind.DOOR && bounds.sillHeightMm > 50)) {
                    conflicts += opening.id; opening
                } else {
                    val kind = if (mark.kind == PhotoAnnotationKind.DOOR) OpeningKind.DOOR else OpeningKind.WINDOW
                    val candidate = opening.copy(side = side, offsetMm = bounds.offsetMm.toInt(),
                        widthMm = bounds.widthMm.toInt(),
                        sillHeightMm = if (kind == OpeningKind.DOOR) 0 else bounds.sillHeightMm.toInt(),
                        heightMm = if (kind == OpeningKind.DOOR) bounds.topHeightMm.toInt() else
                            (bounds.topHeightMm - bounds.sillHeightMm).toInt(), kind = kind)
                    val trial = room.copy(openings = room.openings.map { if (it.id == opening.id) candidate else it })
                    if (GeometryEngine.validateRoom(trial).none { it.objectId == opening.id }) {
                        if (candidate != opening) updated += opening.id
                        candidate
                    } else { conflicts += opening.id; opening }
                }
            }
        }
        val engineering = room.engineering.map { element ->
            if (element.sourcePhotoId != photoId) return@map element
            val markId = element.sourceAnnotationId
            val mark = markId?.let(annotations::get)
            if (mark == null) {
                detached += element.id
                element.copy(sourcePhotoId = null, sourceAnnotationId = null)
            } else {
                val mappedKind = when (mark.kind) {
                    PhotoAnnotationKind.OUTLET -> EngineeringKind.OUTLET
                    PhotoAnnotationKind.SWITCH -> EngineeringKind.SWITCH
                    PhotoAnnotationKind.WATER -> EngineeringKind.WATER
                    PhotoAnnotationKind.GAS -> EngineeringKind.GAS
                    PhotoAnnotationKind.PIPE -> EngineeringKind.PIPE
                    else -> null
                }
                val bounds = PhotoWallCoordinateMapper.annotationBounds(room, photo, mark)
                val side = photo.wallSide
                if (mappedKind == null || bounds == null || side == null || bounds.offsetMm < 0 ||
                    bounds.offsetMm + bounds.widthMm > room.wallLength(side)) {
                    conflicts += element.id; element
                } else {
                    val along = (bounds.offsetMm + bounds.widthMm / 2).coerceIn(0, room.wallLength(side).toLong()).toInt()
                    val elevation = ((bounds.topHeightMm + bounds.sillHeightMm) / 2).coerceIn(0, room.heightMm.toLong()).toInt()
                    val position = when (side) {
                        WallSide.NORTH -> Vec3Mm(along, 0, elevation)
                        WallSide.SOUTH -> Vec3Mm(along, room.depthMm, elevation)
                        WallSide.EAST -> Vec3Mm(room.widthMm, along, elevation)
                        WallSide.WEST -> Vec3Mm(0, along, elevation)
                    }
                    val candidate = element.copy(name = mark.label.ifBlank { mappedKind.name }, kind = mappedKind,
                        position = position, wallSide = side)
                    val trial = room.copy(engineering = room.engineering.map { if (it.id == element.id) candidate else it })
                    if (GeometryEngine.validateRoom(trial).none { it.objectId == element.id }) {
                        if (candidate != element) updated += element.id
                        candidate
                    } else { conflicts += element.id; element }
                }
            }
        }
        val reconciledRoom = room.copy(openings = openings, engineering = engineering)
        val reconciledProject = project.copy(rooms = project.rooms.map { if (it.id == roomId) reconciledRoom else it })
        ProjectValidator.requireValid(reconciledProject)
        return PhotoTransferSyncResult(reconciledProject, updated.distinct(), detached.distinct(), conflicts.distinct())
    }

    fun transfer(project: ProjectModel, roomId: String, photoId: String): PhotoTransferResult {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val side = requireNotNull(photo.wallSide) { "Сначала привяжите фотографию к стене" }
        require(PhotoWallCoordinateMapper.isTransferReady(photo)) { "Фотография не откалибрована" }
        val wallLength = room.wallLength(side)
        val existingIds = (room.openings.map { it.id } + room.engineering.map { it.id }).toMutableSet()
        val openings = room.openings.toMutableList()
        val engineering = room.engineering.toMutableList()
        val createdOpenings = mutableListOf<String>()
        val createdEngineering = mutableListOf<String>()
        val ignored = mutableListOf<String>()

        photo.annotations.forEach { mark ->
            // Pixel coordinates start at the top-left; room elevations start at the floor.
            // Keep the conversion explicit so windows receive a floor-relative sill and
            // doors use the distance from their marked top edge to the ceiling.
            // Keep pixel sums and scaled coordinates in Long until every room bound
            // has been checked. Int addition here used to wrap for malformed annotations.
            val bounds = PhotoWallCoordinateMapper.annotationBounds(room, photo, mark)
            if (bounds == null) { ignored += mark.id; return@forEach }
            val xLong = bounds.offsetMm
            val widthLong = bounds.widthMm
            val topFromFloorLong = bounds.topHeightMm
            val bottomFromFloorLong = bounds.sillHeightMm
            val verticalBoundsValid = topFromFloorLong in 1L..room.heightMm.toLong() &&
                bottomFromFloorLong in 0L until topFromFloorLong
            val id = "photo-$photoId-${mark.id}"
            if (id in existingIds) { ignored += mark.id; return@forEach }
            if (xLong < 0L || widthLong <= 0L ||
                xLong > wallLength.toLong() || widthLong > wallLength.toLong() - xLong ||
                xLong > Int.MAX_VALUE.toLong() || widthLong > Int.MAX_VALUE.toLong()) {
                ignored += mark.id
                return@forEach
            }
            if (!verticalBoundsValid || topFromFloorLong > Int.MAX_VALUE || bottomFromFloorLong > Int.MAX_VALUE) {
                ignored += mark.id
                return@forEach
            }
            val x = xLong.toInt()
            val width = widthLong.toInt()
            val topFromFloor = topFromFloorLong.toInt()
            val bottomFromFloor = bottomFromFloorLong.toInt()
            // A door annotation must reach the floor (within a small drawing tolerance).
            // Otherwise converting it to a floor-level opening would invent geometry.
            if (mark.kind == PhotoAnnotationKind.DOOR && bottomFromFloor > 50) {
                ignored += mark.id
                return@forEach
            }

            when (mark.kind) {
                PhotoAnnotationKind.DOOR, PhotoAnnotationKind.WINDOW -> {
                    val kind = if (mark.kind == PhotoAnnotationKind.DOOR) OpeningKind.DOOR else OpeningKind.WINDOW
                    val sill = if (kind == OpeningKind.DOOR) 0 else bottomFromFloor
                    val openingHeight = if (kind == OpeningKind.DOOR) topFromFloor else topFromFloor - bottomFromFloor
                    // Door marks are accepted only when their lower edge meets the floor.
                    val opening = WallOpening(id, side, x, width, sill, openingHeight, kind, photoId, mark.id)
                    val candidate = room.copy(openings = openings + opening, engineering = engineering)
                    if (GeometryEngine.validateRoom(candidate).none { it.objectId == id }) {
                        openings += opening
                        createdOpenings += id
                        existingIds += id
                    } else ignored += mark.id
                }
                PhotoAnnotationKind.OUTLET, PhotoAnnotationKind.SWITCH,
                PhotoAnnotationKind.WATER, PhotoAnnotationKind.GAS, PhotoAnnotationKind.PIPE -> {
                    val kind = when (mark.kind) {
                        PhotoAnnotationKind.OUTLET -> EngineeringKind.OUTLET
                        PhotoAnnotationKind.SWITCH -> EngineeringKind.SWITCH
                        PhotoAnnotationKind.WATER -> EngineeringKind.WATER
                        PhotoAnnotationKind.GAS -> EngineeringKind.GAS
                        else -> EngineeringKind.PIPE
                    }
                    val along = (x + width / 2).coerceIn(0, wallLength)
                    val elevation = ((topFromFloor + bottomFromFloor) / 2).coerceIn(0, room.heightMm)
                    val position = when (side) {
                        WallSide.NORTH -> Vec3Mm(along, 0, elevation)
                        WallSide.SOUTH -> Vec3Mm(along, room.depthMm, elevation)
                        WallSide.EAST -> Vec3Mm(room.widthMm, along, elevation)
                        WallSide.WEST -> Vec3Mm(0, along, elevation)
                    }
                    val element = EngineeringElement(id, mark.label.ifBlank { kind.name }, kind, position, side, sourcePhotoId = photoId, sourceAnnotationId = mark.id)
                    val candidate = room.copy(openings = openings, engineering = engineering + element)
                    if (GeometryEngine.validateRoom(candidate).none { it.objectId == id }) {
                        engineering += element
                        createdEngineering += id
                        existingIds += id
                    } else ignored += mark.id
                }
                PhotoAnnotationKind.OTHER -> ignored += mark.id
            }
        }
        val updatedRoom = room.copy(openings = openings, engineering = engineering)
        val updatedProject = project.copy(rooms = project.rooms.map { if (it.id == roomId) updatedRoom else it })
        ProjectValidator.requireValid(updatedProject)
        return PhotoTransferResult(updatedProject, createdOpenings, createdEngineering, ignored)
    }
}
