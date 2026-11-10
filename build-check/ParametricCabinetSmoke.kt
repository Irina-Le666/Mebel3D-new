import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    val originalParts = GeometryEngine.createCabinetParts(800, 600, 2100, 18)
    val assembly = FurnitureAssembly("cab", "Кухонный пенал", Vec3Mm(120, 250, 0), originalParts)
    val project = ProjectModel("p", "Кухня", assemblies = listOf(assembly))
    val withCustomMaterial = ProjectEditor.replaceAssemblyPart(project, "cab", originalParts.first { it.id == "shelf" }.copy(materialId = "laminate"))
    val resized = ProjectEditor.resizeCabinetAssembly(withCustomMaterial, "cab", 900, 650, 2200, 20)
    val result = resized.assemblies.single()
    check(result.position == assembly.position); checks++
    check(result.parts.size == 6); checks++
    check(result.parts.map { it.id }.toSet() == originalParts.map { it.id }.toSet()); checks++
    check(result.parts.first { it.id == "left" }.size == Size3Mm(20, 650, 2200)); checks++
    check(result.parts.first { it.id == "right" }.localPosition.x == 880); checks++
    check(result.parts.first { it.id == "shelf" }.size == Size3Mm(860, 630, 20)); checks++
    check(result.parts.first { it.id == "shelf" }.materialId == "laminate"); checks++
    check(GeometryEngine.validateAssembly(result).isEmpty()); checks++
    check(runCatching { ProjectEditor.resizeCabinetAssembly(resized, "cab", 30, 650, 2200, 20) }.isFailure); checks++
    check(runCatching { ProjectEditor.resizeCabinetAssembly(resized, "missing", 900, 650, 2200, 20) }.isFailure); checks++
    println("ParametricCabinetSmoke: $checks checks passed")
}
