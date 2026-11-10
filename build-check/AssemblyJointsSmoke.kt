import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, message: String) { check(value) { message }; checks++ }
    val base = FurnitureAssembly("cab", "Шкаф", Vec3Mm(0,0,0), listOf(
        FurniturePart("side", "Боковина", Size3Mm(18,500,700), Vec3Mm(0,0,0)),
        FurniturePart("shelf", "Полка", Size3Mm(564,500,18), Vec3Mm(18,0,100))
    ))
    val good = AssemblyJoint("j1", JointKind.CONFIRMAT, "side", "shelf", 7, 50, 20, 18)
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good))).none { it.code == AssemblyDiagnosticCode.INVALID_JOINT_DIMENSIONS }, "valid joint dimensions")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good))).none { it.code == AssemblyDiagnosticCode.JOINT_MISSING_PART }, "valid part references")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good.copy(secondPartId="ghost")))).any { it.code == AssemblyDiagnosticCode.JOINT_MISSING_PART }, "missing part reference")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good, good.copy(firstPartId="shelf")))).any { it.code == AssemblyDiagnosticCode.DUPLICATE_JOINT_ID }, "duplicate joint IDs")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good.copy(firstPartId="side", secondPartId="side")))).any { it.code == AssemblyDiagnosticCode.SELF_JOIN }, "self connection")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good.copy(fastenerDiameterMm=-1)))).any { it.code == AssemblyDiagnosticCode.INVALID_JOINT_DIMENSIONS }, "negative dimensions")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good.copy(edgeDistanceMm=8)))).any { it.code == AssemblyDiagnosticCode.INSUFFICIENT_EDGE_DISTANCE }, "edge distance warning")
    check(AssemblyDiagnosticsEngine.inspect(base.copy(joints=listOf(good.copy(materialThicknessMm=8)))).any { it.code == AssemblyDiagnosticCode.INSUFFICIENT_MATERIAL_THICKNESS }, "material thickness warning")
    check(base.joints.isEmpty(), "legacy assembly defaults to no joints")
    println("AssemblyJointsSmoke: $checks checks passed")
}
