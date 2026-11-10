import ru.mebel3d.app.core.*

fun main() {
    val parts = GeometryEngine.createCabinetParts(1200, 600, 2200, 18)
    val assembly = FurnitureAssembly("cab", "Шкаф", Vec3Mm(0, 0, 0), parts)
    val base = ProjectModel("p", "Проект", assemblies = listOf(assembly))

    val moved = ProjectEditor.moveAssemblyPart(base, "cab", "shelf", 120, 0, 1080, 10)
    check(moved.assemblies.single().parts.first { it.id == "shelf" }.localPosition == Vec3Mm(120, 0, 1080))

    val resized = ProjectEditor.resizeAssemblyPart(moved, "cab", "shelf", 900, 560, 18)
    val shelf = resized.assemblies.single().parts.first { it.id == "shelf" }
    check(shelf.size == Size3Mm(900, 560, 18))
    check(shelf.localPosition == Vec3Mm(120, 0, 1080))

    val rotated = ProjectEditor.rotateAssemblyPart(resized, "cab", "shelf", 90)
    check(rotated.assemblies.single().parts.first { it.id == "shelf" }.rotationDeg == 90)
    check(runCatching { ProjectEditor.rotateAssemblyPart(rotated, "cab", "shelf", 45) }.isFailure)
    check(runCatching { ProjectEditor.resizeAssemblyPart(rotated, "cab", "shelf", 0, 560, 18) }.isFailure)

    val session = ProjectSession(base)
    session.edit { ProjectEditor.moveAssemblyPart(it, "cab", "shelf", 100, 0, 1000) }
    check(session.project.assemblies.single().parts.first { it.id == "shelf" }.localPosition.x == 100)
    check(session.undo().assemblies.single().parts.first { it.id == "shelf" }.localPosition.x == 18)
    check(session.redo().assemblies.single().parts.first { it.id == "shelf" }.localPosition.x == 100)

    check(ProjectEditor.cutListForProject(resized).size == parts.size)
    println("AssemblyPartManipulationSmoke: 9 checks passed")
}
