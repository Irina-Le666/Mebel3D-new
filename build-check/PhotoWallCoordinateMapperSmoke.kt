package ru.mebel3d.app.core

object PhotoWallCoordinateMapperSmoke {
    @JvmStatic fun main(args: Array<String>) {
        var checks = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; checks++ }
        val room = RoomModel(id = "mapper-room", widthMm = 4000, depthMm = 3000, heightMm = 2700)
        fun photo(
            id: String, side: WallSide, imageWidth: Int = 4000, imageHeight: Int = 2700,
            known: Int = 4000, pixels: Int = 4000, origin: Int? = null, floor: Int? = null
        ) = RoomPhotoReference(id, "content://$id", side, known, pixels, imageWidth, imageHeight,
            wallOriginXPx = origin, floorLineYPx = floor)

        val north = photo("north", WallSide.NORTH)
        verify(PhotoWallCoordinateMapper.isTransferReady(north), "fully calibrated photo is transfer-ready")
        verify(PhotoWallCoordinateMapper.coverageInterval(north, room.widthMm) == PhotoWallCoverageInterval(0, 4000),
            "north image coverage uses canonical left-to-right wall coordinates")
        val northMark = PhotoAnnotation("north-window", PhotoAnnotationKind.WINDOW, 500, 700, 1200, 900)
        val northBounds = requireNotNull(PhotoWallCoordinateMapper.annotationBounds(room, north, northMark))
        verify(northBounds == PhotoWallAnnotationBounds(500, 1200, 1100, 2000),
            "annotation uses floor-relative coordinates and common pixel scale")

        val east = photo("east", WallSide.EAST, imageWidth = 3000, known = 3000, pixels = 3000)
        val eastBounds = requireNotNull(PhotoWallCoordinateMapper.annotationBounds(room, east,
            PhotoAnnotation("east-outlet", PhotoAnnotationKind.OUTLET, 200, 1800, 100, 100)))
        verify(eastBounds.offsetMm == 200L && eastBounds.widthMm == 100L,
            "east wall uses direct along-wall coordinates and its depth calibration")
        verify(PhotoWallCoordinateMapper.coverageInterval(east, room.depthMm) == PhotoWallCoverageInterval(0, 3000),
            "east wall coverage is clipped against room depth")

        val south = photo("south", WallSide.SOUTH, origin = 4000)
        val southBounds = requireNotNull(PhotoWallCoordinateMapper.annotationBounds(room, south, northMark))
        verify(southBounds.offsetMm == 2300L && southBounds.widthMm == 1200L,
            "south wall maps image coordinates right-to-left")
        verify(PhotoWallCoordinateMapper.coverageInterval(south, room.widthMm) == PhotoWallCoverageInterval(0, 4000),
            "south coverage uses the same reversed coordinate convention")

        val west = photo("west", WallSide.WEST, imageWidth = 3000, known = 3000, pixels = 3000, origin = 3000)
        val westBounds = requireNotNull(PhotoWallCoordinateMapper.annotationBounds(room, west,
            PhotoAnnotation("west-window", PhotoAnnotationKind.WINDOW, 500, 700, 500, 900)))
        verify(westBounds.offsetMm == 2000L && westBounds.widthMm == 500L,
            "west wall maps along its depth with canonical reverse direction")

        val cropped = photo("cropped", WallSide.NORTH, imageWidth = 2000, known = 4000, pixels = 4000,
            origin = 400, floor = 2400)
        val croppedBounds = requireNotNull(PhotoWallCoordinateMapper.annotationBounds(room, cropped,
            PhotoAnnotation("crop-mark", PhotoAnnotationKind.WINDOW, 500, 500, 1000, 1000)))
        verify(croppedBounds == PhotoWallAnnotationBounds(100, 1000, 900, 1900),
            "cropped wall origin and explicit floor anchor are applied together")
        verify(PhotoWallCoordinateMapper.coverageInterval(cropped, room.widthMm) == PhotoWallCoverageInterval(0, 1600),
            "cropped image coverage is clipped against the canonical wall")

        val fractional = photo("fractional", WallSide.NORTH, imageWidth = 101, known = 100, pixels = 100)
        verify(PhotoWallCoordinateMapper.coverageInterval(fractional, 4000) == PhotoWallCoverageInterval(0, 101),
            "fractional scale coverage rounds outward without losing boundary millimetres")
        val noCalibration = north.copy(calibrationPixels = null, knownLengthMm = null)
        verify(!PhotoWallCoordinateMapper.isHorizontallyCalibrated(noCalibration) &&
            PhotoWallCoordinateMapper.coverageInterval(noCalibration, 4000) == null,
            "uncalibrated photo contributes no measured wall coverage")
        verify(PhotoWallCoordinateMapper.annotationBounds(room, north,
            PhotoAnnotation("outside", PhotoAnnotationKind.WINDOW, 3900, 700, 200, 900)) != null,
            "mapper provides projection; room-bound validation remains with transfer stage")
        verify(PhotoWallCoordinateMapper.annotationBounds(room, north,
            PhotoAnnotation("invalid", PhotoAnnotationKind.WINDOW, 10, 10, 0, 10)) == null,
            "invalid annotation rectangle is rejected")
        println("PhotoWallCoordinateMapperSmoke: $checks checks passed")
    }
}
