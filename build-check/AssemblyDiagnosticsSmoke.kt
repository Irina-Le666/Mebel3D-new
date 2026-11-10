import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, message: String) { check(value) { message }; checks++ }
    val touching = FurnitureAssembly("a", "Каркас", Vec3Mm(0,0,0), listOf(
        FurniturePart("left", "Боковина", Size3Mm(18,500,700), Vec3Mm(0,0,0)),
        FurniturePart("bottom", "Дно", Size3Mm(564,500,18), Vec3Mm(18,0,0)),
        FurniturePart("right", "Вторая боковина", Size3Mm(18,500,700), Vec3Mm(582,0,0))
    ))
    val touchIssues = AssemblyDiagnosticsEngine.inspect(touching)
    check(touchIssues.none { it.code == AssemblyDiagnosticCode.PARTS_OVERLAP }, "face contact must be allowed")
    check(touchIssues.none { it.code == AssemblyDiagnosticCode.DISCONNECTED_PART }, "touching carcass must be connected")

    val overlap = touching.copy(parts = touching.parts + FurniturePart("shelf", "Полка", Size3Mm(560,480,18), Vec3Mm(10,10,100)))
    check(AssemblyDiagnosticsEngine.inspect(overlap).any { it.code == AssemblyDiagnosticCode.PARTS_OVERLAP }, "volume overlap must be reported")

    val isolated = touching.copy(parts = touching.parts + FurniturePart("loose", "Отдельная деталь", Size3Mm(100,100,18), Vec3Mm(1000,1000,100)))
    check(AssemblyDiagnosticsEngine.inspect(isolated).any { it.code == AssemblyDiagnosticCode.DISCONNECTED_PART && it.partIds.contains("loose") }, "isolated part must be reported")

    val negativeElevation = touching.copy(parts = listOf(FurniturePart("bad", "Ниже основания", Size3Mm(10,10,10), Vec3Mm(0,0,-1))))
    check(AssemblyDiagnosticsEngine.inspect(negativeElevation).any { it.code == AssemblyDiagnosticCode.NEGATIVE_POSITION }, "negative elevation must be error")
    val validOverhang = touching.copy(parts = listOf(FurniturePart("overhang", "Вылет столешницы", Size3Mm(100,100,20), Vec3Mm(-20,-10,700))))
    check(AssemblyDiagnosticsEngine.inspect(validOverhang).none { it.code == AssemblyDiagnosticCode.NEGATIVE_POSITION }, "negative XY overhang must be allowed")
    check(GeometryEngine.validateAssembly(validOverhang).isEmpty(), "bounded negative XY overhang must pass geometry validation")
    check(GeometryEngine.validateAssembly(validOverhang.copy(parts = listOf(validOverhang.parts.single().copy(localPosition=Vec3Mm(-100001,0,0))))).isNotEmpty(), "excessive negative XY must be rejected")
    val extremeZ = touching.copy(parts = listOf(FurniturePart("z-max", "Высокая деталь", Size3Mm(10,10,10), Vec3Mm(0,0,Int.MAX_VALUE))))
    check(runCatching { AssemblyDiagnosticsEngine.inspect(extremeZ) }.isSuccess, "z-bound calculations must not overflow")
    val extremeJoint = touching.copy(joints = listOf(AssemblyJoint("j-max", JointKind.SCREW, "left", "right", fastenerDiameterMm = Int.MAX_VALUE, fastenerLengthMm = 1, edgeDistanceMm = 1)))
    check(runCatching { AssemblyDiagnosticsEngine.inspect(extremeJoint) }.isSuccess, "joint recommendation arithmetic must not overflow")

    val duplicate = touching.copy(parts = listOf(touching.parts[0], touching.parts[0].copy(name="Повтор")))
    check(AssemblyDiagnosticsEngine.inspect(duplicate).any { it.code == AssemblyDiagnosticCode.DUPLICATE_PART_ID }, "duplicate IDs must be reported")

    val empty = touching.copy(parts = emptyList())
    check(AssemblyDiagnosticsEngine.inspect(empty).single().code == AssemblyDiagnosticCode.EMPTY_ASSEMBLY, "empty assembly must be reported")
    val rotated = FurnitureAssembly("rot", "Поворот", Vec3Mm(0,0,0), listOf(
        FurniturePart("r1", "Деталь 1", Size3Mm(100,20,100), Vec3Mm(0,0,0), rotationDeg=90),
        FurniturePart("r2", "Деталь 2", Size3Mm(20,100,100), Vec3Mm(20,0,0))
    ))
    check(AssemblyDiagnosticsEngine.inspect(rotated).none { it.code == AssemblyDiagnosticCode.PARTS_OVERLAP }, "quarter turn must swap footprint")
    val diagonalSeparated = FurnitureAssembly("diag", "Диагональные детали", Vec3Mm(0,0,0), listOf(
        FurniturePart("d1", "Планка 1", Size3Mm(100,10,20), Vec3Mm(500,500,0), rotationDeg=45),
        FurniturePart("d2", "Планка 2", Size3Mm(100,10,20), Vec3Mm(486,514,0), rotationDeg=45)
    ))
    check(AssemblyDiagnosticsEngine.inspect(diagonalSeparated).none { it.code == AssemblyDiagnosticCode.PARTS_OVERLAP },
        "parallel diagonal boards with clear gap must not be reported as overlapping")
    val diagonalOverlap = diagonalSeparated.copy(parts = listOf(
        diagonalSeparated.parts[0], diagonalSeparated.parts[1].copy(localPosition=Vec3Mm(496,503,0))
    ))
    check(AssemblyDiagnosticsEngine.inspect(diagonalOverlap).any { it.code == AssemblyDiagnosticCode.PARTS_OVERLAP },
        "diagonal boards with intersecting footprints must be reported")

    val project = ProjectModel("p", "Проект", assemblies=listOf(isolated))
    check(ProjectEditor.diagnosticsForAssembly(project, "a").any { it.code == AssemblyDiagnosticCode.DISCONNECTED_PART }, "editor must expose diagnostics")
    check(ProjectEditor.diagnosticsForProject(project).containsKey("a"), "project diagnostics must aggregate assemblies")

    println("AssemblyDiagnosticsSmoke: $checks checks passed")
}
