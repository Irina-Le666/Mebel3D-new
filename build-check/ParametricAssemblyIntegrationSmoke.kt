import ru.mebel3d.app.core.*
fun main() {
    var checks = 0
    fun check(ok: Boolean, message: String) { check(ok) { message }; checks++ }
    val empty = ProjectModel("p", "Проект")
    val spec = CarcassSpec("cab", "Шкаф", 900, 550, 2100, shelfCount = 3)
    val created = ProjectEditor.createParametricCarcass(empty, spec)
    check(created.assemblies.size == 1, "carcass not inserted")
    check(ProjectEditor.cutListForProject(created).size == created.assemblies.single().parts.size, "cut list mismatch")
    check(ProjectEditor.materialCutSummary(created).sumOf { it.boardCount } == created.assemblies.single().parts.size, "material summary mismatch")
    val joint = AssemblyJoint("j1", JointKind.CONFIRMAT, "cab-side-left", "cab-shelf-3", 7, 50, 20, 18)
    val withJoint = ProjectEditor.putAssembly(created, created.assemblies.single().copy(joints = listOf(joint)))
    val rebuilt = ProjectEditor.rebuildParametricCarcass(withJoint, spec.copy(widthMm = 1000))
    check(rebuilt.assemblies.single().parts.first { it.id == "cab-side-right" }.localPosition.x == 982, "rebuild dimensions not applied")
    check(rebuilt.assemblies.single().joints.size == 1, "valid joint not preserved")
    val fewerShelves = ProjectEditor.rebuildParametricCarcass(rebuilt, spec.copy(shelfCount = 1))
    check(fewerShelves.assemblies.single().parts.count { it.name.startsWith("Полка") } == 1, "shelf count not regenerated")
    check(fewerShelves.assemblies.single().joints.isEmpty(), "dangling joints not removed")
    println("ParametricAssemblyIntegrationSmoke: $checks checks passed")
}
