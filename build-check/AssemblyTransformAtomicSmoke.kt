import ru.mebel3d.app.core.*

fun main() {
    val parts = GeometryEngine.createCabinetParts(1200, 600, 2200, 18)
    val assembly = FurnitureAssembly("cab", "Шкаф", Vec3Mm(0, 0, 0), parts)
    val base = ProjectModel("p", "Проект", assemblies = listOf(assembly))

    val arbitrary = ProjectEditor.rotateAssemblyPartTo(base, "cab", "shelf", 37)
    check(arbitrary.assemblies.single().parts.first { it.id == "shelf" }.rotationDeg == 37)
    val wrapped = ProjectEditor.rotateAssemblyPartTo(arbitrary, "cab", "shelf", -15)
    check(wrapped.assemblies.single().parts.first { it.id == "shelf" }.rotationDeg == 345)

    val changed = ProjectEditor.updateAssemblyPart(
        wrapped, "cab", "shelf", 800, 500, 24, 30, 12, 1050, 52
    )
    val shelf = changed.assemblies.single().parts.first { it.id == "shelf" }
    check(shelf.size == Size3Mm(800, 500, 24))
    check(shelf.localPosition == Vec3Mm(30, 12, 1050))
    check(shelf.rotationDeg == 52)
    check(base.assemblies.single().parts.first { it.id == "shelf" }.size.width == 1164)

    check(runCatching {
        ProjectEditor.updateAssemblyPart(base, "cab", "shelf", 0, 500, 24, 30, 12, 1050, 52)
    }.isFailure)
    check(base.assemblies.single().parts.first { it.id == "shelf" }.rotationDeg == 0)

    val session = ProjectSession(base)
    session.edit { ProjectEditor.updateAssemblyPart(it, "cab", "shelf", 900, 500, 22, 18, 0, 1000, 33) }
    check(session.project.assemblies.single().parts.first { it.id == "shelf" }.rotationDeg == 33)
    check(session.undo() == base)
    check(session.redo().assemblies.single().parts.first { it.id == "shelf" }.size.width == 900)

    println("AssemblyTransformAtomicSmoke: 9 checks passed")
}
