package ru.mebel3d.app.core

object RoomPhotoCoverageSmoke {
    @JvmStatic fun main(args: Array<String>) {
        fun photo(id: String, side: WallSide?, width: Int? = 500, length: Int? = 2500,
                  pixels: Int? = 250, origin: Int? = null) = RoomPhotoReference(
            id = id, uri = "content://$id", wallSide = side, knownLengthMm = length,
            calibrationPixels = pixels, imageWidthPx = width, imageHeightPx = 400,
            wallOriginXPx = origin
        )
        val room = RoomModel(widthMm = 5000, depthMm = 3000, photos = listOf(
            photo("north-a", WallSide.NORTH),
            photo("north-b", WallSide.NORTH, width = 250, length = 2500, pixels = 250, origin = 0),
            photo("south", WallSide.SOUTH, width = 500, length = 2500, pixels = 250, origin = 500),
            photo("east", WallSide.EAST, width = 300, length = 3000, pixels = 300),
            photo("unassigned", null),
            photo("uncalibrated", WallSide.WEST, width = null, length = null, pixels = null)
        ))
        val report = RoomPhotoCoverageAnalyzer.analyze(room)
        check(report.walls.first { it.side == WallSide.NORTH }.coveragePercent == 100)
        check(report.walls.first { it.side == WallSide.SOUTH }.coveragePercent == 100)
        check(report.walls.first { it.side == WallSide.EAST }.coveragePercent == 100)
        check(report.walls.first { it.side == WallSide.WEST }.coveragePercent == 0)
        check(report.walls.first { it.side == WallSide.WEST }.state == WallCoverageState.NEEDS_CALIBRATION)
        check(report.walls.first { it.side == WallSide.NORTH }.state == WallCoverageState.COMPLETE)
        check(report.unassignedPhotoIds == listOf("unassigned"))
        check(report.uncalibratedPhotoIds == listOf("uncalibrated"))
        check(report.hasAllWallsPhotographed)
        val emptyWall = report.walls.first { it.side == WallSide.WEST }
        check(emptyWall.uncalibratedPhotoCount == 1)
        val noPhotoRoom = room.copy(photos = emptyList())
        check(RoomPhotoCoverageAnalyzer.analyze(noPhotoRoom).walls.all { it.state == WallCoverageState.NO_PHOTOS })
        check(!report.hasCompleteCalibratedCoverage)

        val mismatchRoom = room.copy(photos = listOf(
            photo("a", WallSide.NORTH, width = 500, length = 2500, pixels = 250),
            photo("b", WallSide.NORTH, width = 500, length = 3000, pixels = 250)
        ))
        check(WallSide.NORTH in RoomPhotoCoverageAnalyzer.analyze(mismatchRoom).scaleMismatchWallSides)

        val cropped = room.copy(photos = listOf(photo("crop", WallSide.NORTH, width = 200,
            length = 1000, pixels = 100, origin = -100)))
        val croppedReport = RoomPhotoCoverageAnalyzer.analyze(cropped)
        check(croppedReport.walls.first { it.side == WallSide.NORTH }.coveredLengthMm == 2000)

        // Fractional mm/px scales must merge at touching boundaries without rounding gaps.
        val fractional = room.copy(widthMm = 1000, photos = listOf(
            photo("fraction-a", WallSide.NORTH, width = 101, length = 100, pixels = 100, origin = 0),
            photo("fraction-b", WallSide.NORTH, width = 101, length = 100, pixels = 100, origin = -101)
        ))
        val fractionalCoverage = RoomPhotoCoverageAnalyzer.analyze(fractional)
            .walls.first { it.side == WallSide.NORTH }
        check(fractionalCoverage.coveredLengthMm == 202)
        check(fractionalCoverage.coveragePercent == 20)
        check(fractionalCoverage.uncoveredGaps == listOf(WallCoverageGap(202, 1000)))

        // Photos entirely outside the wall do not create phantom coverage.
        val outside = room.copy(widthMm = 1000, photos = listOf(
            photo("outside", WallSide.NORTH, width = 100, length = 100, pixels = 100, origin = 1500)
        ))
        val outsideWall = RoomPhotoCoverageAnalyzer.analyze(outside).walls.first { it.side == WallSide.NORTH }
        check(outsideWall.coveredLengthMm == 0)
        check(outsideWall.uncoveredGaps == listOf(WallCoverageGap(0, 1000)))
        val split = room.copy(widthMm = 1000, photos = listOf(
            photo("left", WallSide.NORTH, width = 200, length = 200, pixels = 200, origin = 0),
            photo("right", WallSide.NORTH, width = 200, length = 200, pixels = 200, origin = -800)
        ))
        val splitGaps = RoomPhotoCoverageAnalyzer.analyze(split).walls.first { it.side == WallSide.NORTH }.uncoveredGaps
        check(splitGaps == listOf(WallCoverageGap(200, 800)))
        check(RoomPhotoCoverageAnalyzer.analyze(split).totalUncoveredLengthMm == 7600)
        check(RoomPhotoCoverageAnalyzer.analyze(split).largestUncoveredWall?.uncoveredLengthMm == 3000)
        check(RoomPhotoCoverageAnalyzer.analyze(split).wallsNeedingCapture.first().uncoveredLengthMm == 3000)

        // Rounded display percentages must never mark a wall complete while a real gap remains.
        val nearComplete = room.copy(widthMm = 1000, photos = listOf(
            photo("near-complete", WallSide.NORTH, width = 995, length = 995, pixels = 995)
        ))
        val nearCompleteWall = RoomPhotoCoverageAnalyzer.analyze(nearComplete).walls.first { it.side == WallSide.NORTH }
        check(nearCompleteWall.coveragePercent == 99)
        check(!nearCompleteWall.isFullyCovered)
        check(nearCompleteWall.state == WallCoverageState.PARTIAL)
        check(!RoomPhotoCoverageAnalyzer.analyze(nearComplete).hasCompleteCalibratedCoverage)
        println("RoomPhotoCoverageSmoke: 25 assertions passed")
    }
}
