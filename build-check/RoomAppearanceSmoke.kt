import ru.mebel3d.app.core.RoomModel

fun main() {
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }

    val room = RoomModel()
    verify(room.wallColorArgb == 0xFFF0ECE5L, "default neutral wall finish")
    verify(room.floorColorArgb == 0xFFEADCC8L, "default oak floor finish")
    verify(room.lightingPreset == "Дневной", "default daylight preset")

    val styled = room.copy(wallColorArgb = 0xFFFAFAF7L, floorColorArgb = 0xFF806E5DL, lightingPreset = "Тёплый")
    verify(styled.id == room.id && styled.widthMm == room.widthMm, "appearance update preserves room geometry")
    verify(styled.wallColorArgb != room.wallColorArgb, "wall finish can be independently changed")
    verify(styled.floorColorArgb != room.floorColorArgb, "floor finish can be independently changed")
    verify(styled.lightingPreset == "Тёплый", "lighting preset is room scoped")
    verify(room.wallColorArgb == 0xFFF0ECE5L, "immutable original room remains unchanged")
    println("RoomAppearanceSmoke: $checks checks passed")
}
