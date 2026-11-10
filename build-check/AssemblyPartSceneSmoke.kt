import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    val parts = GeometryEngine.createCabinetParts(1200, 600, 2100, 18)
    val assembly = FurnitureAssembly("cabinet-1", "Шкаф", Vec3Mm(500, 400, 0), parts)
    var project = ProjectModel("p", "Тест", listOf(RoomModel(widthMm = 5000, depthMm = 3500)), assemblies = listOf(assembly))
    val selected = project.assemblies.first().parts.first { it.id == "shelf" }
    check(selected.name == "Полка" || selected.name.contains("полк", true)); checks++
    project = ProjectEditor.resizeAssemblyPart(project, assembly.id, selected.id, 1100, 560, 18)
    val resized = project.assemblies.first().parts.first { it.id == selected.id }
    check(resized.size == Size3Mm(1100, 560, 18)); checks++
    check(resized.materialId == selected.materialId && resized.localPosition == selected.localPosition); checks++
    project = ProjectEditor.moveAssemblyPart(project, assembly.id, selected.id, 75, 125, 900, 25)
    val moved = project.assemblies.first().parts.first { it.id == selected.id }
    check(moved.localPosition == Vec3Mm(75, 125, 900)); checks++
    project = ProjectEditor.rotateAssemblyPart(project, assembly.id, selected.id, 90)
    check(project.assemblies.first().parts.first { it.id == selected.id }.rotationDeg == 90); checks++
    val before = project
    val invalid = runCatching { ProjectEditor.resizeAssemblyPart(project, assembly.id, selected.id, 0, 10, 10) }.isFailure
    check(invalid && project == before); checks++
    val duplicateIdRejected = runCatching { ProjectEditor.putAssembly(project, assembly.copy(parts = parts + parts.first())) }.isFailure
    check(duplicateIdRejected); checks++
    val missingPartRejected = runCatching { ProjectEditor.moveAssemblyPart(project, assembly.id, "missing", 0, 0, 0) }.isFailure
    check(missingPartRejected); checks++
    val cutList = ProjectEditor.cutListForProject(project)
    check(cutList.any { it.partId == selected.id }); checks++
    println("AssemblyPartSceneSmoke: $checks checks passed")
}
