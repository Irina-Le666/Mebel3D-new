package ru.mebel3d.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Coverage and calibration diagnostics for a room assembled from several wall photographs. */
data class WallCoverageGap(
    val startMm: Int,
    val endMm: Int
) {
    val lengthMm: Int get() = (endMm - startMm).coerceAtLeast(0)
}

enum class WallCoverageState { NO_PHOTOS, NEEDS_CALIBRATION, PARTIAL, COMPLETE, COMPLETE_WITH_UNCALIBRATED_PHOTOS }

data class WallPhotoCoverage(
    val side: WallSide,
    val wallLengthMm: Int,
    val coveredLengthMm: Int,
    val coveragePercent: Int,
    val calibratedPhotoCount: Int,
    val photoCount: Int,
    val uncoveredGaps: List<WallCoverageGap> = emptyList(),
    val uncalibratedPhotoCount: Int = 0
) {
    /** Completion is based on exact measured millimetres, never rounded display percent. */
    val isFullyCovered: Boolean get() = wallLengthMm > 0 && coveredLengthMm >= wallLengthMm && uncoveredGaps.isEmpty()

    /** Explicit capture state avoids treating an uncalibrated photo as measured coverage. */
    val state: WallCoverageState get() = when {
        photoCount == 0 -> WallCoverageState.NO_PHOTOS
        isFullyCovered && uncalibratedPhotoCount > 0 -> WallCoverageState.COMPLETE_WITH_UNCALIBRATED_PHOTOS
        isFullyCovered -> WallCoverageState.COMPLETE
        calibratedPhotoCount == 0 -> WallCoverageState.NEEDS_CALIBRATION
        else -> WallCoverageState.PARTIAL
    }
    /** Total wall length still requiring a photograph, in millimetres. */
    val uncoveredLengthMm: Int get() = uncoveredGaps.sumOf { it.lengthMm }

    /** The longest missing span is the most useful next capture target. */
    val largestUncoveredGap: WallCoverageGap? get() = uncoveredGaps.maxByOrNull { it.lengthMm }
}

data class RoomPhotoCoverageReport(
    val walls: List<WallPhotoCoverage>,
    val unassignedPhotoIds: List<String>,
    val uncalibratedPhotoIds: List<String>,
    val scaleMismatchWallSides: List<WallSide>
) {
    val fullyCoveredWallCount: Int get() = walls.count { it.isFullyCovered }
    val hasAllWallsPhotographed: Boolean get() = walls.all { it.photoCount > 0 }
    val hasCompleteCalibratedCoverage: Boolean get() = walls.all { it.isFullyCovered }
    val totalUncoveredLengthMm: Int get() = walls.sumOf { it.uncoveredLengthMm }
    val largestUncoveredWall: WallPhotoCoverage? get() = walls
        .filter { it.largestUncoveredGap != null }
        .maxByOrNull { it.largestUncoveredGap!!.lengthMm }
    val wallsNeedingCapture: List<WallPhotoCoverage> get() = walls
        .filter { it.uncoveredLengthMm > 0 }
        .sortedByDescending { it.uncoveredLengthMm }
}

object RoomPhotoCoverageAnalyzer {
    /**
     * Estimates horizontal wall coverage only for calibrated, wall-assigned images with known
     * source width. It does not infer perspective or claim photogrammetric reconstruction.
     */
    fun analyze(room: RoomModel): RoomPhotoCoverageReport {
        val unassigned = room.photos.filter { it.wallSide == null }.map { it.id }
        val uncalibrated = room.photos.filter {
            it.wallSide != null && (it.knownLengthMm == null || it.calibrationPixels == null ||
                it.imageWidthPx == null || it.knownLengthMm <= 0 || it.calibrationPixels <= 0 || it.imageWidthPx <= 0)
        }.map { it.id }

        val walls = WallSide.values().map { side ->
            val photos = room.photos.filter { it.wallSide == side }
            // Work with half-open integer millimetre intervals [start, end). Floor the
            // beginning and ceil the end so fractional pixel scales never create false gaps
            // or lose a millimetre at a photograph boundary.
            val intervals = photos.mapNotNull { photo ->
                PhotoWallCoordinateMapper.coverageInterval(photo, room.wallLength(side))
                    ?.let { it.startMm until it.endMm }
            }
            val merged = mutableListOf<LongRange>()
            intervals.sortedBy { it.first }.forEach { interval ->
                val last = merged.lastOrNull()
                if (last != null && interval.first <= last.last + 1L) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, interval.last)
                } else merged += interval
            }
            val wallLength = room.wallLength(side)
            // Preserve the uncovered spans as actionable capture guidance, not just a
            // percentage. Intervals are inclusive integer millimetres after clipping.
            val gaps = mutableListOf<WallCoverageGap>()
            var cursor = 0L
            merged.forEach { interval ->
                val start = interval.first.coerceIn(0L, wallLength.toLong())
                val endExclusive = (interval.last + 1L).coerceIn(0L, wallLength.toLong())
                if (start > cursor) gaps += WallCoverageGap(cursor.toInt(), start.toInt())
                cursor = maxOf(cursor, endExclusive)
            }
            if (cursor < wallLength.toLong()) gaps += WallCoverageGap(cursor.toInt(), wallLength)
            val covered = merged.sumOf { (it.last - it.first + 1L).coerceAtLeast(0L) }
                .coerceAtMost(wallLength.toLong()).toInt()
            WallPhotoCoverage(
                side = side,
                wallLengthMm = wallLength,
                coveredLengthMm = covered,
                coveragePercent = if (covered >= wallLength) 100 else floor((covered.toDouble() / wallLength.toDouble()) * 100.0).toInt().coerceIn(0, 99),
                calibratedPhotoCount = intervals.size,
                photoCount = photos.size,
                uncoveredGaps = gaps,
                uncalibratedPhotoCount = photos.count { photo ->
                    photo.knownLengthMm == null || photo.calibrationPixels == null ||
                        photo.imageWidthPx == null || photo.knownLengthMm <= 0 ||
                        photo.calibrationPixels <= 0 || photo.imageWidthPx <= 0
                }
            )
        }

        // A 5% scale spread between images of the same wall is a useful warning threshold;
        // the report is advisory and never silently changes user-entered room dimensions.
        val mismatches = WallSide.values().filter { side ->
            val scales = room.photos.filter { it.wallSide == side }.mapNotNull { photo ->
                val length = photo.knownLengthMm
                val pixels = photo.calibrationPixels
                if (length != null && pixels != null && length > 0 && pixels > 0)
                    length.toDouble() / pixels.toDouble() else null
            }
            if (scales.size < 2) false else {
                val reference = scales.sorted()[scales.size / 2]
                scales.any { abs(it - reference) / reference > 0.05 }
            }
        }
        return RoomPhotoCoverageReport(walls, unassigned, uncalibrated, mismatches)
    }
}
