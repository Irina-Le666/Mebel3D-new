import ru.mebel3d.app.core.*
fun main() {
  val door = WallOpening("d", WallSide.NORTH, 500, 900, 0, 2100, OpeningKind.DOOR)
  val window = WallOpening("w", WallSide.NORTH, 1000, 900, 900, 1200, OpeningKind.WINDOW)
  check(GeometryEngine.validateRoom(RoomModel(openings=listOf(door,window))).any { it.code == GeometryIssueCode.INVALID_OPENING })
  val a = FurnitureItem("a","Стол",Size3Mm(1200,300,750),Vec3Mm(2000,1500),45)
  val b = FurnitureItem("b","Тумба",Size3Mm(300,300,600),Vec3Mm(2000,1900))
  check(GeometryEngine.validateFurniture(RoomModel(), listOf(a,b)).any { it.code == GeometryIssueCode.COLLISION })
  check(GeometryEngine.snappedMove(a,1237,1224).position == Vec3Mm(1250,1200))
  println("4 stage smoke checks passed")
}
