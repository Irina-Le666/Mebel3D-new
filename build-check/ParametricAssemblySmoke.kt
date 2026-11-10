import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(ok: Boolean, message: String) { check(ok) { message }; checks++ }
    val built = ParametricAssemblyEngine.buildCarcass(CarcassSpec("cab-1", "Шкаф", 1200, 600, 2200, shelfCount = 4))
    check(built.issues.isEmpty(), "valid carcass rejected")
    val a = requireNotNull(built.assembly)
    check(a.parts.size == 9, "expected 2 sides + top + bottom + back + 4 shelves")
    check(a.parts.map { it.id }.distinct().size == a.parts.size, "duplicate part IDs")
    check(a.parts.first { it.id.endsWith("side-right") }.localPosition.x == 1182, "right side position")
    check(a.parts.filter { it.name.startsWith("Полка") }.all { it.size.width == 1162 }, "shelf width")
    val back = a.parts.single { it.id.endsWith("-back") }
    check(back.size.width == 1164 && back.size.height == 2164 && back.localPosition.x == 18 && back.localPosition.z == 18,
        "back panel must fit between carcass sides, top and bottom")
    check(GeometryEngine.validateAssembly(a).isEmpty(), "generated assembly invalid")
    val summary = ParametricAssemblyEngine.materialSummary(a)
    check(summary.sumOf { it.boardCount } == a.parts.size, "cut summary count mismatch")
    check(summary.any { it.materialId == "back-panel" }, "back panel material missing")
    check(ParametricAssemblyEngine.buildCarcass(CarcassSpec("bad", "", 20, 20, 20, shelfCount = 2)).assembly == null, "invalid carcass accepted")
    check(ParametricAssemblyEngine.buildCarcass(CarcassSpec("bad2", "Корпус", 1000, 500, 1000, shelfCount = 101)).issues.any { it.code == CarcassIssueCode.INVALID_SHELF_COUNT }, "shelf count validation missing")
    val overflow = ParametricAssemblyEngine.buildCarcass(CarcassSpec("huge", "Переполнение", Int.MAX_VALUE, 600, 2200, panelThicknessMm = Int.MAX_VALUE))
    check(overflow.assembly == null && overflow.issues.any { it.code == CarcassIssueCode.INVALID_DIMENSION_RANGE }, "oversized Int dimensions must be rejected before arithmetic")
    val extremeShelf = ParametricAssemblyEngine.buildCarcass(CarcassSpec("shelf-huge", "Полки", 1200, 600, 2200, shelfCount = 100, shelfThicknessMm = Int.MAX_VALUE))
    check(extremeShelf.assembly == null && extremeShelf.issues.any { it.code == CarcassIssueCode.INVALID_DIMENSION_RANGE }, "oversized shelf thickness must be rejected")
    val zeroBack = ParametricAssemblyEngine.buildCarcass(CarcassSpec("zero-back", "Без толщины", 800, 500, 1000, backThicknessMm = 0))
    check(zeroBack.assembly == null && zeroBack.issues.any { it.code == CarcassIssueCode.INVALID_THICKNESS }, "included back panel cannot have zero thickness")
    println("ParametricAssemblySmoke: $checks checks passed")
}
