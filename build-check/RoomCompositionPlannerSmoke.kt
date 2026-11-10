import ru.mebel3d.app.core.*
fun main() {
    var checks=0
    val room=RoomModel(id="r",widthMm=4000,depthMm=3000,heightMm=2700)
    val project=ProjectModel("p","Тест",rooms=listOf(room),activeRoomId="r")
    fun module(id:String,w:Int=600,d:Int=560)=FurnitureModuleSpec(id,id,FurnitureRoomCategory.KITCHEN,FurnitureModuleKind.BASE_CABINET,w,d,900,countertop=CountertopKind.INDIVIDUAL)
    val north=RoomWallRun(RoomLayoutRequest("r",WallSide.NORTH,listOf(module("n1"),module("n2")),minimumAisleMm=900))
    val east=RoomWallRun(RoomLayoutRequest("r",WallSide.EAST,listOf(module("e1")),minimumAisleMm=900))
    val req=RoomCompositionRequest("kitchen","Кухня","r",listOf(north,east))
    val result=RoomCompositionPlanner.plan(project,req)
    check(result.wallResults.size==2);checks++
    check(result.totalModules==3);checks++
    check(result.isValid);checks++
    val expectedCuts = result.wallResults.flatMap { it.placements }.flatMap { GeometryEngine.cutList(it.assembly) }
    val expectedBoardArea = expectedCuts.sumOf { it.widthMm.toDouble() * it.lengthMm.toDouble() / 1_000_000.0 }
    check(kotlin.math.abs(result.totalMaterialAreaSquareMetres - expectedBoardArea) < 1e-9);checks++
    check(result.materialAreaByIdSquareMetres.keys == expectedCuts.map { it.materialId }.toSet());checks++
    check(kotlin.math.abs(result.materialAreaByIdSquareMetres.values.sum() - result.totalMaterialAreaSquareMetres) < 1e-9);checks++
    val applied=RoomCompositionPlanner.apply(project,result)
    check(applied.assemblies.size==3);checks++
    check(applied.assemblies.map{it.id}.distinct().size==3);checks++
    val duplicateWall=RoomCompositionPlanner.plan(project,req.copy(wallRuns=listOf(north,north)))
    check(duplicateWall.issues.any{it.code=="DUPLICATE_WALL"});checks++
    val mismatch=RoomCompositionPlanner.plan(project,req.copy(wallRuns=listOf(north.copy(request=north.request.copy(roomId="other")))))
    check(mismatch.issues.any{it.code=="ROOM_MISMATCH"});checks++
    val duplicateModule=RoomCompositionPlanner.plan(project,req.copy(wallRuns=listOf(north,RoomWallRun(east.request.copy(modules=listOf(module("n1")))))))
    check(duplicateModule.issues.any{it.code=="DUPLICATE_MODULE"});checks++
    val noRoom=RoomCompositionPlanner.plan(project,req.copy(roomId="missing"))
    check(noRoom.issues.any{it.code=="INVALID_COMPOSITION"});checks++
    val triangle=RoomCompositionPlanner.plan(project,req.copy(requireKitchenWorkTriangle=true,sinkModuleId="n1",hobModuleId="n2",refrigeratorModuleId="e1",minimumTriangleSideMm=1,maximumTriangleSideMm=10000))
    check(triangle.isValid);checks++
    val badTriangle=RoomCompositionPlanner.plan(project,req.copy(requireKitchenWorkTriangle=true,sinkModuleId="missing",hobModuleId="n2",refrigeratorModuleId="e1"))
    check(badTriangle.issues.any{it.code=="TRIANGLE_MODULE_MISSING"});checks++
    val cornerRoom=room.copy(widthMm=1200,depthMm=1200)
    val cornerProject=ProjectModel("cp","Угол",rooms=listOf(cornerRoom),activeRoomId="r")
    val cornerNorth=RoomWallRun(RoomLayoutRequest("r",WallSide.NORTH,listOf(module("cn",900,600)),minimumAisleMm=0))
    val cornerEast=RoomWallRun(RoomLayoutRequest("r",WallSide.EAST,listOf(module("ce",900,600)),minimumAisleMm=0))
    val corner=RoomCompositionPlanner.plan(cornerProject,RoomCompositionRequest("c","Угловая","r",listOf(cornerNorth,cornerEast)))
    check(corner.issues.any{it.code=="CORNER_COLLISION"});checks++
    // Upright side panels must be accounted by their actual board face (height x depth),
    // not by the floor footprint (width x depth).
    val sidePanelArea = result.wallResults.flatMap { it.placements }.flatMap { GeometryEngine.cutList(it.assembly) }
        .filter { it.partName.startsWith("Боковина") }
        .sumOf { it.widthMm.toDouble() * it.lengthMm.toDouble() / 1_000_000.0 }
    check(sidePanelArea > 0.0 && result.materialAreaByIdSquareMetres.values.sum() >= sidePanelArea);checks++
    var rejected=false
    try { RoomCompositionPlanner.apply(project,badTriangle) } catch (_: IllegalArgumentException) { rejected=true } catch (_: IllegalStateException) { rejected=true }
    check(rejected);checks++
    println("RoomCompositionPlannerSmoke: $checks checks passed")
}
