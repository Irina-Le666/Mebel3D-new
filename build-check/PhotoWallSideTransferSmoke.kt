import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", widthMm = 4200, depthMm = 3100, heightMm = 2700)
    val cases = listOf(
        WallSide.NORTH to Vec3Mm(355, 0, 500),
        WallSide.SOUTH to Vec3Mm(room.widthMm - 355, room.depthMm, 500),
        WallSide.EAST to Vec3Mm(room.widthMm, 355, 500),
        WallSide.WEST to Vec3Mm(0, room.depthMm - 355, 500)
    )
    cases.forEachIndexed { index, (side, expected) ->
        val length = room.wallLength(side)
        val photo = RoomPhotoReference("photo-$index", "content://$index", side,
            knownLengthMm = length, calibrationPixels = length,
            imageWidthPx = length, imageHeightPx = room.heightMm,
            annotations = listOf(PhotoAnnotation("mark", PhotoAnnotationKind.OUTLET, 350, 2190, 10, 20)))
        val project = ProjectModel("p-$index", "Проверка", listOf(room.copy(photos = listOf(photo))))
        val result = PhotoWallTransfer.transfer(project, room.id, photo.id)
        val actual = result.project.rooms.single().engineering.single().position
        check(actual == expected) { "$side: expected $expected, got $actual" }
    }
    println("PhotoWallSideTransferSmoke: all four wall orientations map to correct room planes")
}
