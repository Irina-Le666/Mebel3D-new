package ru.mebel3d.app.core

import kotlin.math.max

/** Physical design bounds prevent integer overflow and implausible generated geometry. */
object ParametricDimensionLimits { const val MAX_MM: Int = 100_000 }

/** Cabinet front and drawer sizing helpers. Every value is in millimetres. */
enum class FacadeMount { OVERLAY, INSET }

data class FacadeLayoutSpec(
    val openingWidthMm: Int,
    val openingHeightMm: Int,
    val columns: Int = 1,
    val rows: Int = 1,
    val horizontalGapMm: Int = 2,
    val verticalGapMm: Int = 2,
    val perimeterGapMm: Int = 2,
    val mount: FacadeMount = FacadeMount.OVERLAY,
    val overlayMm: Int = 15,
    val panelThicknessMm: Int = 18
)

data class FacadePanelSize(val row: Int, val column: Int, val widthMm: Int, val heightMm: Int,
                           val xOffsetMm: Int, val yOffsetMm: Int)

data class DrawerBoxSpec(
    val openingWidthMm: Int,
    val openingHeightMm: Int,
    val cabinetDepthMm: Int,
    val slideClearancePerSideMm: Int = 13,
    val rearClearanceMm: Int = 10,
    val bottomThicknessMm: Int = 6,
    val sideThicknessMm: Int = 16,
    val backThicknessMm: Int = 16,
    val frontThicknessMm: Int = 16,
    val boxHeightMm: Int,
    val verticalClearanceMm: Int = 10
)

data class DrawerBoxDimensions(
    val outerWidthMm: Int, val outerDepthMm: Int, val outerHeightMm: Int,
    val innerWidthMm: Int, val innerDepthMm: Int, val innerHeightMm: Int,
    val bottomWidthMm: Int, val bottomDepthMm: Int
)

enum class FurnitureBuildIssueCode {
    INVALID_OPENING, INVALID_COUNT, NEGATIVE_GAP, INSUFFICIENT_OPENING,
    INVALID_THICKNESS, INVALID_OVERLAY, INVALID_DIMENSION_RANGE, DRAWER_TOO_WIDE, DRAWER_TOO_DEEP,
    DRAWER_TOO_TALL, INVALID_SLIDE_CLEARANCE
}
data class FurnitureBuildIssue(val code: FurnitureBuildIssueCode, val message: String)
data class FacadeLayoutResult(val panels: List<FacadePanelSize>, val issues: List<FurnitureBuildIssue>)
data class DrawerSizingResult(val dimensions: DrawerBoxDimensions?, val issues: List<FurnitureBuildIssue>)

object ParametricFurnitureEngine {
    /** Equal-size facade panels, with exact integer-mm distribution and no cumulative rounding drift. */
    fun layoutFacades(spec: FacadeLayoutSpec): FacadeLayoutResult {
        val issues = mutableListOf<FurnitureBuildIssue>()
        if (spec.openingWidthMm <= 0 || spec.openingHeightMm <= 0)
            issues += issue(FurnitureBuildIssueCode.INVALID_OPENING, "Размеры проёма должны быть положительными")
        if (listOf(spec.openingWidthMm, spec.openingHeightMm, spec.horizontalGapMm,
                spec.verticalGapMm, spec.perimeterGapMm, spec.overlayMm, spec.panelThicknessMm)
                .any { it > ParametricDimensionLimits.MAX_MM })
            issues += issue(FurnitureBuildIssueCode.INVALID_DIMENSION_RANGE,
                "Размеры фасада не должны превышать ${ParametricDimensionLimits.MAX_MM} мм")
        if (spec.columns <= 0 || spec.rows <= 0 || spec.columns > 20 || spec.rows > 20)
            issues += issue(FurnitureBuildIssueCode.INVALID_COUNT, "Количество фасадов по горизонтали и вертикали должно быть от 1 до 20")
        if (listOf(spec.horizontalGapMm, spec.verticalGapMm, spec.perimeterGapMm).any { it < 0 })
            issues += issue(FurnitureBuildIssueCode.NEGATIVE_GAP, "Зазоры фасадов не могут быть отрицательными")
        if (spec.panelThicknessMm <= 0)
            issues += issue(FurnitureBuildIssueCode.INVALID_THICKNESS, "Толщина фасада должна быть положительной")
        if (spec.mount == FacadeMount.OVERLAY && spec.overlayMm < 0)
            issues += issue(FurnitureBuildIssueCode.INVALID_OVERLAY, "Нахлёст фасада не может быть отрицательным")
        if (issues.isNotEmpty()) return FacadeLayoutResult(emptyList(), issues)

        val inset = spec.mount == FacadeMount.INSET
        val baseW = spec.openingWidthMm - if (inset) 2 * spec.perimeterGapMm else 0
        val baseH = spec.openingHeightMm - if (inset) 2 * spec.perimeterGapMm else 0
        val totalW = baseW + if (inset) 0 else 2 * spec.overlayMm
        val totalH = baseH + if (inset) 0 else 2 * spec.overlayMm
        val usableW = totalW - (spec.columns - 1) * spec.horizontalGapMm
        val usableH = totalH - (spec.rows - 1) * spec.verticalGapMm
        if (usableW < spec.columns || usableH < spec.rows) {
            issues += issue(FurnitureBuildIssueCode.INSUFFICIENT_OPENING, "Проём слишком мал для выбранного количества фасадов и зазоров")
            return FacadeLayoutResult(emptyList(), issues)
        }
        val baseX = if (inset) spec.perimeterGapMm else -spec.overlayMm
        val baseY = if (inset) spec.perimeterGapMm else -spec.overlayMm
        val panels = buildList {
            for (r in 0 until spec.rows) for (c in 0 until spec.columns) {
                val left = c * usableW / spec.columns
                val right = (c + 1) * usableW / spec.columns
                val top = r * usableH / spec.rows
                val bottom = (r + 1) * usableH / spec.rows
                add(FacadePanelSize(r, c, right - left, bottom - top,
                    baseX + left + c * spec.horizontalGapMm,
                    baseY + top + r * spec.verticalGapMm))
            }
        }
        return FacadeLayoutResult(panels, emptyList())
    }

    /** Calculates drawer-box dimensions from the actual opening and slide clearances. */
    fun sizeDrawer(spec: DrawerBoxSpec): DrawerSizingResult {
        val issues = mutableListOf<FurnitureBuildIssue>()
        if (spec.openingWidthMm <= 0 || spec.openingHeightMm <= 0 || spec.cabinetDepthMm <= 0)
            issues += issue(FurnitureBuildIssueCode.INVALID_OPENING, "Размеры ниши и глубина корпуса должны быть положительными")
        if (listOf(spec.openingWidthMm, spec.openingHeightMm, spec.cabinetDepthMm,
                spec.slideClearancePerSideMm, spec.rearClearanceMm, spec.bottomThicknessMm,
                spec.sideThicknessMm, spec.backThicknessMm, spec.frontThicknessMm,
                spec.boxHeightMm, spec.verticalClearanceMm).any { it > ParametricDimensionLimits.MAX_MM })
            issues += issue(FurnitureBuildIssueCode.INVALID_DIMENSION_RANGE,
                "Размеры ящика не должны превышать ${ParametricDimensionLimits.MAX_MM} мм")
        if (spec.slideClearancePerSideMm < 0 || spec.rearClearanceMm < 0 || spec.verticalClearanceMm < 0)
            issues += issue(FurnitureBuildIssueCode.INVALID_SLIDE_CLEARANCE, "Монтажные зазоры направляющих не могут быть отрицательными")
        if (listOf(spec.bottomThicknessMm, spec.sideThicknessMm, spec.backThicknessMm, spec.frontThicknessMm).any { it <= 0 })
            issues += issue(FurnitureBuildIssueCode.INVALID_THICKNESS, "Толщины деталей короба должны быть положительными")
        if (spec.boxHeightMm <= 0) issues += issue(FurnitureBuildIssueCode.DRAWER_TOO_TALL, "Высота короба должна быть положительной")
        if (issues.isNotEmpty()) return DrawerSizingResult(null, issues)

        val outerW = spec.openingWidthMm - 2 * spec.slideClearancePerSideMm
        val outerD = spec.cabinetDepthMm - spec.rearClearanceMm
        val outerH = spec.boxHeightMm
        if (outerW <= 2 * spec.sideThicknessMm)
            issues += issue(FurnitureBuildIssueCode.DRAWER_TOO_WIDE, "После вычета зазоров направляющих не остаётся места для короба")
        if (outerD <= spec.backThicknessMm)
            issues += issue(FurnitureBuildIssueCode.DRAWER_TOO_DEEP, "Глубина ниши недостаточна для короба и задней стенки")
        if (outerH + spec.verticalClearanceMm > spec.openingHeightMm)
            issues += issue(FurnitureBuildIssueCode.DRAWER_TOO_TALL, "Короб с монтажным зазором выше доступной ниши")
        if (issues.isNotEmpty()) return DrawerSizingResult(null, issues)

        val innerW = outerW - 2 * spec.sideThicknessMm
        val innerD = outerD - spec.backThicknessMm
        val innerH = max(1, outerH - spec.bottomThicknessMm)
        return DrawerSizingResult(DrawerBoxDimensions(outerW, outerD, outerH, innerW, innerD, innerH,
            innerW, innerD), emptyList())
    }

    private fun issue(code: FurnitureBuildIssueCode, message: String) = FurnitureBuildIssue(code, message)
}
