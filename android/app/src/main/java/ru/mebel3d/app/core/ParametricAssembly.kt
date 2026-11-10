package ru.mebel3d.app.core

/** Parametric carcass generator. Coordinates are assembly-local millimetres; origin is lower-left-front and +Y runs toward the rear panel. */
data class CarcassSpec(
    val id: String,
    val name: String,
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
    val panelThicknessMm: Int = 18,
    val backThicknessMm: Int = 6,
    val shelfCount: Int = 0,
    val shelfThicknessMm: Int = 18,
    val shelfSideClearanceMm: Int = 1,
    val materialId: String = "wood-oak",
    val includeBack: Boolean = true,
    val includeTop: Boolean = true,
    val includeBottom: Boolean = true,
    val backMaterialId: String = "back-panel"
)

enum class CarcassIssueCode { INVALID_SIZE, INVALID_DIMENSION_RANGE, INVALID_THICKNESS, INVALID_SHELF_COUNT, NO_INTERNAL_SPACE, BACK_TOO_THICK }
data class CarcassIssue(val code: CarcassIssueCode, val message: String)
data class CarcassBuildResult(val assembly: FurnitureAssembly?, val issues: List<CarcassIssue>)

data class MaterialCutSummary(
    val materialId: String,
    val boardCount: Int,
    val totalAreaSquareMetres: Double,
    val parts: List<BoardCut>
)

object ParametricAssemblyEngine {
    fun buildCarcass(spec: CarcassSpec): CarcassBuildResult {
        val issues = mutableListOf<CarcassIssue>()
        if (spec.id.isBlank() || spec.name.isBlank() || minOf(spec.widthMm, spec.depthMm, spec.heightMm) <= 0)
            issues += CarcassIssue(CarcassIssueCode.INVALID_SIZE, "Укажите название и положительные габариты корпуса")
        val bounded = listOf(spec.widthMm, spec.depthMm, spec.heightMm, spec.panelThicknessMm,
            spec.backThicknessMm, spec.shelfThicknessMm, spec.shelfSideClearanceMm)
        if (bounded.any { it < 0 || it > ParametricDimensionLimits.MAX_MM } ||
            spec.widthMm > ParametricDimensionLimits.MAX_MM || spec.depthMm > ParametricDimensionLimits.MAX_MM ||
            spec.heightMm > ParametricDimensionLimits.MAX_MM) {
            issues += CarcassIssue(CarcassIssueCode.INVALID_DIMENSION_RANGE,
                "Размеры и толщины должны находиться в диапазоне до ${ParametricDimensionLimits.MAX_MM} мм")
        }
        if (spec.panelThicknessMm <= 0 || spec.shelfThicknessMm <= 0 || spec.backThicknessMm < 0 ||
            spec.shelfSideClearanceMm < 0 || (spec.includeBack && spec.backThicknessMm <= 0))
            issues += CarcassIssue(CarcassIssueCode.INVALID_THICKNESS,
                "Проверьте толщины панелей, задней стенки и боковые зазоры")
        if (spec.shelfCount !in 0..100)
            issues += CarcassIssue(CarcassIssueCode.INVALID_SHELF_COUNT, "Количество полок должно быть от 0 до 100")
        if (issues.isNotEmpty()) return CarcassBuildResult(null, issues)

        // Do all intermediate arithmetic as Long so malformed Int inputs cannot wrap around.
        val innerW = spec.widthMm.toLong() - 2L * spec.panelThicknessMm
        val innerH = spec.heightMm.toLong() -
            (if (spec.includeTop) spec.panelThicknessMm.toLong() else 0L) -
            (if (spec.includeBottom) spec.panelThicknessMm.toLong() else 0L)
        val innerD = spec.depthMm.toLong() - if (spec.includeBack) spec.backThicknessMm.toLong() else 0L
        val shelfW = innerW - 2L * spec.shelfSideClearanceMm
        if (innerW <= 0L || innerH <= 0L || innerD <= 0L || shelfW <= 0L)
            issues += CarcassIssue(CarcassIssueCode.NO_INTERNAL_SPACE, "После вычета толщин не остаётся внутреннего пространства")
        if (spec.includeBack && spec.backThicknessMm >= spec.depthMm)
            issues += CarcassIssue(CarcassIssueCode.BACK_TOO_THICK, "Задняя стенка толще или равна глубине корпуса")
        val usableH = innerH - spec.shelfCount.toLong() * spec.shelfThicknessMm
        if (spec.shelfCount > 0 && usableH < 0L)
            issues += CarcassIssue(CarcassIssueCode.NO_INTERNAL_SPACE, "Полки не помещаются по высоте")
        if (issues.isNotEmpty()) return CarcassBuildResult(null, issues)

        val t = spec.panelThicknessMm
        val safeInnerW = innerW.toInt(); val safeInnerD = innerD.toInt()
        val parts = mutableListOf<FurniturePart>()
        fun part(suffix: String, title: String, w: Int, d: Int, h: Int, x: Int, y: Int, z: Int, material: String = spec.materialId) {
            parts += FurniturePart("${spec.id}-$suffix", title, Size3Mm(w, d, h), Vec3Mm(x, y, z), materialId = material)
        }
        part("side-left", "Боковина левая", t, spec.depthMm, spec.heightMm, 0, 0, 0)
        part("side-right", "Боковина правая", t, spec.depthMm, spec.heightMm, spec.widthMm - t, 0, 0)
        if (spec.includeBottom) part("bottom", "Дно корпуса", safeInnerW, safeInnerD, t, t, 0, 0)
        if (spec.includeTop) part("top", "Крышка корпуса", safeInnerW, safeInnerD, t, t, 0, spec.heightMm - t)
        if (spec.includeBack) part("back", "Задняя стенка", safeInnerW, spec.backThicknessMm,
            innerH.toInt(), t, spec.depthMm - spec.backThicknessMm, if (spec.includeBottom) t else 0, spec.backMaterialId)
        if (spec.shelfCount > 0) {
            val step = (usableH / (spec.shelfCount + 1L)).toInt() + spec.shelfThicknessMm
            repeat(spec.shelfCount) { index ->
                val z = (if (spec.includeBottom) t else 0) + step * (index + 1) - spec.shelfThicknessMm
                part("shelf-${index + 1}", "Полка ${index + 1}", shelfW.toInt(), safeInnerD,
                    spec.shelfThicknessMm, t + spec.shelfSideClearanceMm, 0, z)
            }
        }
        val assembly = FurnitureAssembly(spec.id, spec.name, Vec3Mm(0, 0, 0), parts)
        val validation = GeometryEngine.validateAssembly(assembly)
        if (validation.isNotEmpty()) return CarcassBuildResult(null, validation.map { CarcassIssue(CarcassIssueCode.INVALID_SIZE, it.message) })
        return CarcassBuildResult(assembly, emptyList())
    }

    /** Material grouped cut-list summary; area is based on the two largest panel dimensions. */
    fun materialSummary(assembly: FurnitureAssembly): List<MaterialCutSummary> =
        GeometryEngine.cutList(assembly).groupBy { it.materialId }.toSortedMap().map { (material, cuts) ->
            val area = cuts.sumOf { it.widthMm.toDouble() * it.lengthMm / 1_000_000.0 }
            MaterialCutSummary(material, cuts.size, area, cuts)
        }

    /** Returns a replacement assembly only after all dimensions and part IDs pass validation. */
    fun rebuild(existing: FurnitureAssembly, spec: CarcassSpec): CarcassBuildResult {
        require(existing.id == spec.id) { "ID параметрической модели должен совпадать с ID сборки" }
        return buildCarcass(spec)
    }
}
