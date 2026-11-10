import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(ok: Boolean, message: String) { check(ok) { message }; checks++ }
    val room = RoomModel(id="r", widthMm=4000, depthMm=3000, heightMm=2700)
    val photo = RoomPhotoReference(id="p", uri="content://photo", wallSide=WallSide.NORTH,
        knownLengthMm=4000, calibrationPixels=4000, imageWidthPx=4000, imageHeightPx=2700,
        annotations=listOf(
            PhotoAnnotation("d", PhotoAnnotationKind.DOOR, 500, 1700, 900, 1000),
            PhotoAnnotation("w", PhotoAnnotationKind.WINDOW, 2000, 700, 1200, 900),
            PhotoAnnotation("o", PhotoAnnotationKind.OUTLET, 1000, 2200, 80, 80),
            PhotoAnnotation("g", PhotoAnnotationKind.GAS, 3500, 1800, 50, 50),
            PhotoAnnotation("x", PhotoAnnotationKind.OTHER, 100, 100, 20, 20)
        ))
    val project = ProjectModel("pr", "Test", listOf(room.copy(photos=listOf(photo))))
    val preview = PhotoWallTransfer.preview(project, "r")
    check(preview.annotatedPhotoCount == 1 && preview.readyPhotoCount == 1,
        "preview counts annotated calibrated photos")
    check(preview.plannedOpeningCount == 2 && preview.plannedEngineeringCount == 2,
        "preview predicts the same supported geometry as import")
    check(preview.ignoredAnnotationCount == 1 && preview.hasUnresolvedMarks,
        "preview reports annotations that cannot become room geometry")
    check(project.rooms.single().openings.isEmpty() && project.rooms.single().engineering.isEmpty(),
        "preview never mutates the supplied project")
    val result = PhotoWallTransfer.transfer(project, "r", "p")
    check(result.createdOpenings.size == 2, "two openings")
    check(result.createdEngineering.size == 2, "two engineering points")
    check(result.ignoredAnnotations == listOf("x"), "other ignored")
    val transferredRoom = result.project.rooms.single()
    val door = transferredRoom.openings.first { it.id.endsWith("-d") }
    val window = transferredRoom.openings.first { it.id.endsWith("-w") }
    check(door.side == WallSide.NORTH, "wall side")
    check(door.sillHeightMm == 0 && door.heightMm == 1000, "door height must be measured from floor to marked top")
    check(window.sillHeightMm == 1100 && window.heightMm == 900, "window sill must be measured upward from floor")
    check(transferredRoom.engineering.first { it.id.endsWith("-o") }.position.z == 460, "height mapping")
    check(GeometryEngine.validateRoom(result.project.rooms.single()).isEmpty(), "room geometry valid")
    check(PhotoWallTransfer.transfer(result.project, "r", "p").createdOpenings.isEmpty(), "duplicate IDs ignored")

    // Room-wide transfer imports marks from several calibrated wall photographs in one action.
    val secondPhoto = photo.copy(id = "p-east", wallSide = WallSide.EAST, knownLengthMm = 3000,
        calibrationPixels = 3000, imageWidthPx = 3000, annotations = listOf(
            PhotoAnnotation("east-outlet", PhotoAnnotationKind.OUTLET, 400, 2200, 80, 80)
        ))
    val uncalibratedPhoto = photo.copy(id = "p-unready", wallSide = WallSide.WEST,
        knownLengthMm = null, calibrationPixels = null, annotations = listOf(
            PhotoAnnotation("unready", PhotoAnnotationKind.WATER, 100, 100, 50, 50)
        ))
    val multiPhotoProject = project.copy(rooms = listOf(room.copy(photos = listOf(photo, secondPhoto, uncalibratedPhoto))))
    val multi = PhotoWallTransfer.transferAll(multiPhotoProject, "r")
    check(multi.createdOpenings.size == 2 && multi.createdEngineering.size == 3,
        "room-wide transfer imports openings and engineering from all calibrated photos")
    check(multi.skippedPhotoIds == listOf("p-unready"), "room-wide transfer reports uncalibrated photos")
    check(multi.skippedPhotoReasons["p-unready"] == PhotoTransferSkipReason.NEEDS_CALIBRATION,
        "room-wide transfer explains why an annotated photo was skipped")
    val multiPreview = PhotoWallTransfer.preview(multiPhotoProject, "r")
    check(multiPreview.annotatedPhotoCount == 3 && multiPreview.readyPhotoCount == 2 && multiPreview.skippedPhotoCount == 1,
        "preview summarizes room-wide photo readiness")
    check(multiPreview.skippedReasons[PhotoTransferSkipReason.NEEDS_CALIBRATION] == 1,
        "preview groups skipped photos by actionable reason")
    val unbound = uncalibratedPhoto.copy(id = "p-no-wall", wallSide = null)
    val mixedReadiness = PhotoWallTransfer.transferAll(project.copy(rooms = listOf(room.copy(photos = listOf(unbound, uncalibratedPhoto)))), "r")
    check(mixedReadiness.skippedPhotoReasons["p-no-wall"] == PhotoTransferSkipReason.NO_WALL &&
        mixedReadiness.skippedPhotoReasons["p-unready"] == PhotoTransferSkipReason.NEEDS_CALIBRATION,
        "room-wide transfer distinguishes missing wall assignment from missing calibration")
    check(PhotoWallTransfer.transferAll(multi.project, "r").createdOpenings.isEmpty() &&
        PhotoWallTransfer.transferAll(multi.project, "r").createdEngineering.isEmpty(),
        "room-wide transfer is idempotent")
    // Cropped wall photos can place the wall origin away from the left image edge
    // and the floor line above the bottom image row. Both anchors must affect transfer.
    val croppedPhoto = photo.copy(
        wallOriginXPx = 400, floorLineYPx = 2400,
        annotations = listOf(
            PhotoAnnotation("cropped-window", PhotoAnnotationKind.WINDOW, 500, 500, 1000, 1000),
            PhotoAnnotation("cropped-outlet", PhotoAnnotationKind.OUTLET, 1200, 2100, 80, 100)
        )
    )
    val croppedProject = project.copy(rooms = listOf(room.copy(photos = listOf(croppedPhoto))))
    val cropped = PhotoWallTransfer.transfer(croppedProject, "r", "p")
    val croppedWindow = cropped.project.rooms.single().openings.single()
    check(croppedWindow.offsetMm == 100 && croppedWindow.sillHeightMm == 900 && croppedWindow.heightMm == 1000,
        "cropped photo uses explicit wall origin and floor line")
    val croppedOutlet = cropped.project.rooms.single().engineering.single()
    check(croppedOutlet.position.x == 840 && croppedOutlet.position.z == 250,
        "engineering point uses cropped photo anchors")
    check(GeometryEngine.validateRoom(cropped.project.rooms.single()).isEmpty(), "cropped photo transfer remains valid")
    val reverseWallPhoto = photo.copy(wallSide = WallSide.SOUTH, wallOriginXPx = 4000, floorLineYPx = 2700,
        annotations = listOf(PhotoAnnotation("south-window", PhotoAnnotationKind.WINDOW, 500, 700, 1200, 900)))
    val reverseWallProject = project.copy(rooms = listOf(room.copy(photos = listOf(reverseWallPhoto))))
    val reverseWindow = PhotoWallTransfer.transfer(reverseWallProject, "r", "p").project.rooms.single().openings.single()
    check(reverseWindow.side == WallSide.SOUTH && reverseWindow.offsetMm == 2300,
        "south wall photo is mapped right-to-left into canonical wall coordinates")
    val westWallPhoto = reverseWallPhoto.copy(wallSide = WallSide.WEST, wallOriginXPx = 3000, annotations = listOf(PhotoAnnotation("west-window", PhotoAnnotationKind.WINDOW, 500, 700, 500, 900)))
    val westRoom = room.copy(photos = listOf(westWallPhoto))
    val westProject = project.copy(rooms = listOf(westRoom))
    val westWindow = PhotoWallTransfer.transfer(westProject, "r", "p").project.rooms.single().openings.single()
    check(westWindow.side == WallSide.WEST && westWindow.offsetMm == 2000,
        "west wall photo is mapped right-to-left into canonical wall coordinates")
    val invalidFloorAnchor = croppedProject.copy(rooms = listOf(room.copy(photos = listOf(croppedPhoto.copy(floorLineYPx = 3000)))))
    check(ProjectValidator.validate(invalidFloorAnchor).any { it.objectId == "p" }, "floor anchor outside source image is rejected")
    val invalidWallAnchor = croppedProject.copy(rooms = listOf(room.copy(photos = listOf(croppedPhoto.copy(wallOriginXPx = 20000)))))
    check(ProjectValidator.validate(invalidWallAnchor).any { it.objectId == "p" }, "unbounded wall origin is rejected")
    val outOfHeight = photo.copy(annotations = listOf(PhotoAnnotation("too-high", PhotoAnnotationKind.WINDOW, 100, 0, 200, 2600)))
    val shorterRoom = room.copy(heightMm = 2500, photos = listOf(outOfHeight))
    val outOfHeightProject = project.copy(rooms = listOf(shorterRoom))
    check(PhotoWallTransfer.transfer(outOfHeightProject, "r", "p").ignoredAnnotations == listOf("too-high"), "annotation outside room height must be ignored")
    val floatingDoor = photo.copy(annotations = listOf(PhotoAnnotation("floating-door", PhotoAnnotationKind.DOOR, 100, 100, 900, 1800)))
    val floatingDoorProject = project.copy(rooms = listOf(room.copy(photos = listOf(floatingDoor))))
    check(PhotoWallTransfer.transfer(floatingDoorProject, "r", "p").ignoredAnnotations == listOf("floating-door"), "door mark not reaching floor must be ignored")
    check(runCatching { PhotoWallTransfer.transfer(project, "r", "missing") }.isFailure, "missing photo rejected")
    check(runCatching { PhotoWallTransfer.transfer(project.copy(rooms=listOf(room)), "r", "p") }.isFailure, "missing photo rejected")
    check(runCatching { PhotoWallTransfer.transfer(project.copy(rooms=listOf(room.copy(photos=listOf(photo.copy(wallSide=null))))), "r", "p") }.isFailure, "unbound wall rejected")
    val overflowAnnotation = PhotoAnnotation("overflow", PhotoAnnotationKind.OUTLET, Int.MAX_VALUE - 10, 10, 100, 20)
    check(runCatching { GeometryEngine.addPhotoAnnotation(photo, overflowAnnotation) }.isFailure,
        "pixel rectangle overflow must be rejected")
    // Imported geometry retains photo/annotation provenance and can follow later edits.
    val movedPhoto = photo.copy(annotations = photo.annotations.map {
        if (it.id == "w") it.copy(xPx = 2200, yPx = 600, widthPx = 1000, heightPx = 1000) else it
    })
    val movedSource = result.project.copy(rooms = listOf(transferredRoom.copy(photos = listOf(movedPhoto))))
    val synchronized = PhotoWallTransfer.synchronize(movedSource, "r", "p")
    val movedWindow = synchronized.project.rooms.single().openings.first { it.id.endsWith("-w") }
    check(movedWindow.offsetMm == 2200 && movedWindow.widthMm == 1000 && movedWindow.sillHeightMm == 1100 &&
        movedWindow.sourcePhotoId == "p" && movedWindow.sourceAnnotationId == "w",
        "photo-derived opening follows edited annotation and retains provenance")
    check(synchronized.updatedIds.contains(movedWindow.id), "sync reports changed generated geometry")
    val removedMarkPhoto = movedPhoto.copy(annotations = movedPhoto.annotations.filterNot { it.id == "w" })
    val removedMarkProject = synchronized.project.copy(rooms = listOf(synchronized.project.rooms.single().copy(photos = listOf(removedMarkPhoto))))
    val detached = PhotoWallTransfer.synchronize(removedMarkProject, "r", "p")
    val preservedWindow = detached.project.rooms.single().openings.first { it.id.endsWith("-w") }
    check(preservedWindow.sourcePhotoId == null && preservedWindow.sourceAnnotationId == null &&
        detached.detachedIds.contains(preservedWindow.id),
        "deleting a source mark detaches rather than deletes the user's existing opening")
    val typeChangedPhoto = photo.copy(annotations = photo.annotations.map {
        if (it.id == "d") it.copy(kind = PhotoAnnotationKind.OUTLET) else it
    })
    val typeChangedProject = result.project.copy(rooms = listOf(transferredRoom.copy(photos = listOf(typeChangedPhoto))))
    val typeConflict = PhotoWallTransfer.synchronize(typeChangedProject, "r", "p")
    check(typeConflict.conflicts.contains(door.id) &&
        typeConflict.project.rooms.single().openings.any { it.id == door.id },
        "type-changed annotation is reported as conflict without destructive conversion")
    check(PhotoWallTransfer.synchronize(result.project, "r", "p").updatedIds.isEmpty(),
        "synchronizing unchanged source annotations is idempotent")
    val movedOutletPhoto = photo.copy(annotations = photo.annotations.map {
        if (it.id == "o") it.copy(xPx = 1300, yPx = 2000, widthPx = 120, heightPx = 100) else it
    })
    val movedOutletSource = result.project.copy(rooms = listOf(transferredRoom.copy(photos = listOf(movedOutletPhoto))))
    val movedOutletSync = PhotoWallTransfer.synchronize(movedOutletSource, "r", "p")
    val movedOutlet = movedOutletSync.project.rooms.single().engineering.first { it.id.endsWith("-o") }
    check(movedOutlet.position.x == 1360 && movedOutlet.position.z == 650 &&
        movedOutlet.sourcePhotoId == "p" && movedOutlet.sourceAnnotationId == "o",
        "photo-derived engineering point follows edited source rectangle")
    val deletedOutletPhoto = movedOutletPhoto.copy(annotations = movedOutletPhoto.annotations.filterNot { it.id == "o" })
    val deletedOutletSource = movedOutletSync.project.copy(rooms = listOf(movedOutletSync.project.rooms.single().copy(photos = listOf(deletedOutletPhoto))))
    val deletedOutletSync = PhotoWallTransfer.synchronize(deletedOutletSource, "r", "p")
    val preservedOutlet = deletedOutletSync.project.rooms.single().engineering.first { it.id.endsWith("-o") }
    check(preservedOutlet.sourcePhotoId == null && deletedOutletSync.detachedIds.contains(preservedOutlet.id),
        "removed engineering mark detaches while preserving the existing point")
    println("PhotoWallTransferSmoke: $checks checks passed")
}
