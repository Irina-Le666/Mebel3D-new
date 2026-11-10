import ru.mebel3d.app.core.*
fun main() {
    val first = RoomModel(id="r1", name="Кухня")
    val second = RoomModel(id="r2", name="Спальня")
    val rooms = RoomManager.addRoom(listOf(first), second)
    check(rooms.size == 2)
    val duplicate = RoomManager.duplicateRoom(rooms, "r1", "r3", "Кухня — копия")
    check(duplicate.size == 3 && duplicate.last().id == "r3")
    val renamed = RoomManager.updateRoom(duplicate, second.copy(name="Детская"))
    check(renamed.first { it.id == "r2" }.name == "Детская")
    check(RoomManager.removeRoom(renamed, "r3").size == 2)
    check(runCatching { RoomManager.removeRoom(listOf(first), "r1") }.isFailure)
    check(runCatching { RoomManager.addRoom(listOf(first), first) }.isFailure)
    println("RoomManager smoke: 6 checks passed")
}
