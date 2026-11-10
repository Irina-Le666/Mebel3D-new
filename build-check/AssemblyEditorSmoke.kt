import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    val room = RoomModel(id = "r1", name = "Кухня")
    var project = ProjectModel("p", "Тест", rooms = listOf(room))
    val parts = GeometryEngine.createCabinetParts(800, 600, 2100, 18)
    val assembly = FurnitureAssembly("cab-1", "Шкаф 800", Vec3Mm(0,0,0), parts)
    project = ProjectEditor.putAssembly(project, assembly); checks++
    check(project.assemblies.single().parts.size == 6); checks++
    val shelf = parts.first { it.id == "shelf" }.copy(name = "Полка регулируемая", materialId = "laminate-white")
    project = ProjectEditor.replaceAssemblyPart(project, "cab-1", shelf); checks++
    check(project.assemblies.single().parts.first { it.id == "shelf" }.name == "Полка регулируемая"); checks++
    val cuts = ProjectEditor.cutListForProject(project)
    check(cuts.size == 6); checks++
    check(cuts.any { it.partId == "shelf" && it.materialId == "laminate-white" }); checks++
    val replacement = assembly.copy(name = "Шкаф обновлён")
    project = ProjectEditor.putAssembly(project, replacement); checks++
    check(project.assemblies.size == 1); checks++
    project = ProjectEditor.removeAssembly(project, "cab-1"); checks++
    check(project.assemblies.isEmpty()); checks++
    println("AssemblyEditorSmoke: $checks checks passed")
}
