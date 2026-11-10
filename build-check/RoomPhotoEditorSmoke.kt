package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun check(ok: Boolean, message: String) { check(ok) { message }; checks++ }
    val room = RoomModel(id = "r-photo", widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val base = ProjectModel("p", "Фото", listOf(room), activeRoomId = room.id)
    val photo = RoomPhotoReference("ph1", "content://photos/1")
    val added = ProjectEditor.addRoomPhoto(base, room.id, photo)
    check(added.rooms.single().photos.single() == photo, "photo added")
    check(base.rooms.single().photos.isEmpty(), "immutable source")
    val north = ProjectEditor.setPhotoWall(added, room.id, photo.id, WallSide.NORTH)
    check(north.rooms.single().photos.single().wallSide == WallSide.NORTH, "wall assigned")
    val calibrated = ProjectEditor.calibrateRoomPhoto(north, room.id, photo.id, 2000, 1000)
    check(GeometryEngine.millimetresPerPixel(calibrated.rooms.single().photos.single()) == 2.0, "scale calculated")
    check(runCatching { ProjectEditor.addRoomPhoto(added, room.id, photo) }.isFailure, "duplicate rejected")
    check(runCatching { ProjectEditor.calibrateRoomPhoto(added, room.id, photo.id, 0, 100) }.isFailure, "invalid calibration rejected")
    val removed = ProjectEditor.removeRoomPhoto(calibrated, room.id, photo.id)
    check(removed.rooms.single().photos.isEmpty(), "photo removed")
    check(runCatching { ProjectEditor.removeRoomPhoto(removed, room.id, photo.id) }.isFailure, "missing photo rejected")
    check(runCatching { ProjectEditor.addRoomPhoto(base, "missing", photo) }.isFailure, "unknown room rejected")
    val second = ProjectEditor.addRoomPhoto(added, room.id, RoomPhotoReference("ph2", "content://photos/2"))
    check(second.rooms.single().photos.size == 2, "multiple photos")
    check(second.rooms.single().photos.first().id == photo.id, "existing photo retained")
    println("RoomPhotoEditorSmoke: $checks checks passed")
}
