import ru.mebel3d.app.core.*
fun main() {
  val room = RoomModel(openings=listOf(WallOpening("w",WallSide.NORTH,1000,1200,900,1200,OpeningKind.WINDOW)))
  check(GeometryEngine.wallSegments(room,WallSide.NORTH).size == 4)
  check(GeometryEngine.snap(1237,50)==1250)
  val assembly=FurnitureAssembly("cab","Шкаф",Vec3Mm(0,0),GeometryEngine.createCabinetParts(800,500,2100,18))
  check(GeometryEngine.cutList(assembly).size==6)
  check(GeometryEngine.validateAssembly(assembly).isEmpty())
  println("PASS: wall segmentation, grid snapping, six-part cabinet cut list, assembly validation")
}
