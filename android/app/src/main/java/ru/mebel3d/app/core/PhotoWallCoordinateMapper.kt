package ru.mebel3d.app.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Half-open wall interval [startMm, endMm) occupied by a calibrated photograph. */
data class PhotoWallCoverageInterval(val startMm: Long, val endMm: Long) {
    val lengthMm: Long get() = (endMm - startMm).coerceAtLeast(0L)
}

/** Canonical projection of one photo annotation into a wall elevation. */
data class PhotoWallAnnotationBounds(
    val offsetMm: Long,
    val widthMm: Long,
    val sillHeightMm: Long,
    val topHeightMm: Long
)

/**
 * The single coordinate contract shared by wall coverage and photo-to-room transfer.
 * Photos are assumed to be manually aligned to a wall plane; no perspective reconstruction
 * is inferred. NORTH/EAST run left-to-right, SOUTH/WEST right-to-left when viewed from inside.
 */
object PhotoWallCoordinateMapper {
    fun isHorizontallyCalibrated(photo: RoomPhotoReference): Boolean =
        photo.wallSide != null && (photo.knownLengthMm ?: 0) > 0 &&
            (photo.calibrationPixels ?: 0) > 0 && (photo.imageWidthPx ?: 0) > 0

    fun isTransferReady(photo: RoomPhotoReference): Boolean =
        isHorizontallyCalibrated(photo) && (photo.imageHeightPx ?: 0) > 0

    fun millimetresPerPixel(photo: RoomPhotoReference): Double {
        require(isHorizontallyCalibrated(photo)) { "Фотография не откалибрована" }
        return requireNotNull(photo.knownLengthMm).toDouble() / requireNotNull(photo.calibrationPixels).toDouble()
    }

    /** Returns the clipped wall interval actually covered by the source image. */
    fun coverageInterval(photo: RoomPhotoReference, wallLengthMm: Int): PhotoWallCoverageInterval? {
        if (!isHorizontallyCalibrated(photo) || wallLengthMm <= 0) return null
        val width = requireNotNull(photo.imageWidthPx).toDouble()
        val scale = millimetresPerPixel(photo)
        val side = requireNotNull(photo.wallSide)
        val reverse = side == WallSide.SOUTH || side == WallSide.WEST
        val calibration = requireNotNull(photo.calibrationPixels).toDouble()
        val origin = photo.wallOriginXPx?.toDouble() ?: if (reverse) calibration else 0.0
        val a = if (reverse) (origin - width) * scale else (0.0 - origin) * scale
        val b = if (reverse) origin * scale else (width - origin) * scale
        if (!a.isFinite() || !b.isFinite()) return null
        val wallEnd = wallLengthMm.toDouble()
        val start = floor(minOf(a, b).coerceIn(0.0, wallEnd)).toLong()
        val end = ceil(maxOf(a, b).coerceIn(0.0, wallEnd)).toLong()
        return if (end > start) PhotoWallCoverageInterval(start, end) else null
    }

    /** Converts an annotation rectangle to canonical wall/floor-relative millimetres. */
    fun annotationBounds(
        room: RoomModel,
        photo: RoomPhotoReference,
        annotation: PhotoAnnotation
    ): PhotoWallAnnotationBounds? {
        if (!isTransferReady(photo) || annotation.xPx < 0 || annotation.yPx < 0 ||
            annotation.widthPx <= 0 || annotation.heightPx <= 0) return null
        val side = requireNotNull(photo.wallSide)
        val scale = millimetresPerPixel(photo)
        val reverse = side == WallSide.SOUTH || side == WallSide.WEST
        val calibration = requireNotNull(photo.calibrationPixels).toLong()
        val origin = (photo.wallOriginXPx ?: if (reverse) calibration.toInt() else 0).toLong()
        val xFromOrigin = if (reverse)
            origin - (annotation.xPx.toLong() + annotation.widthPx.toLong())
        else annotation.xPx.toLong() - origin
        val offset = (xFromOrigin.toDouble() * scale).roundToLong()
        val width = (annotation.widthPx.toDouble() * scale).roundToLong().coerceAtLeast(1L)
        val floorLine = photo.floorLineYPx?.toDouble() ?: room.heightMm.toDouble() / scale
        val bottomPx = annotation.yPx.toLong() + annotation.heightPx.toLong()
        val topHeight = ((floorLine - annotation.yPx.toDouble()) * scale).roundToLong()
        val sill = ((floorLine - bottomPx.toDouble()) * scale).roundToLong()
        if (!floorLine.isFinite() || width <= 0L ||
            topHeight !in 1L..room.heightMm.toLong() || sill !in 0L until topHeight) return null
        return PhotoWallAnnotationBounds(offset, width, sill, topHeight)
    }
}
