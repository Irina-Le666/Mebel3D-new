import ru.mebel3d.app.core.*

fun main() {
    val a = FurniturePart("a", "Полка А", Size3Mm(100, 40, 18), Vec3Mm(0, 0, 100))
    val b = FurniturePart("b", "Полка Б", Size3Mm(100, 40, 18), Vec3Mm(104, 0, 100))
    val c = FurniturePart("c", "Перегородка", Size3Mm(18, 40, 180), Vec3Mm(300, 0, 100))
    val base = ProjectModel("p", "Проект", assemblies = listOf(FurnitureAssembly("cab", "Шкаф", Vec3Mm(0,0,0), listOf(a,b,c))))

    val guides = ProjectEditor.assemblyPartSnapGuides(base, "cab", "b", 20)
    check(guides.any { it.axis == "X" && it.coordinateMm == 100 && it.label.contains("Полка А") })
    check(guides.any { it.axis == "Y" && it.coordinateMm == 0 })
    check(guides.any { it.axis == "Z" && it.coordinateMm == 100 })

    val snapped = ProjectEditor.snapAssemblyPartToNearby(base, "cab", "b", 20)
    val newB = snapped.assemblies.single().parts.first { it.id == "b" }
    check(newB.localPosition.x == 100)
    check(newB.localPosition.y == 0 && newB.localPosition.z == 100)
    check(snapped.assemblies.single().parts.first { it.id == "a" } == a)

    val unchanged = ProjectEditor.snapAssemblyPartToNearby(base, "cab", "b", 1)
    check(unchanged == base)
    check(runCatching { ProjectEditor.assemblyPartSnapGuides(base, "cab", "b", -1) }.isFailure)
    check(runCatching { ProjectEditor.snapAssemblyPartToNearby(base, "cab", "b", 10, 0) }.isFailure)

    val session = ProjectSession(base)
    session.edit { ProjectEditor.snapAssemblyPartToNearby(it, "cab", "b", 20) }
    check(session.project.assemblies.single().parts.first { it.id == "b" }.localPosition.x == 100)
    check(session.undo().assemblies.single().parts.first { it.id == "b" }.localPosition.x == 104)
    check(session.redo().assemblies.single().parts.first { it.id == "b" }.localPosition.x == 100)
    println("AssemblySnapGuidesSmoke: 10 checks passed")
}
