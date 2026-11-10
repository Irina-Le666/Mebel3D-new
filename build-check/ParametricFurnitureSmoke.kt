package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun check(value: Boolean, label: String) { check(value) { label }; checks++ }

    val inset = ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(800, 700, 2, 2, 2, 2, 3, FacadeMount.INSET, panelThicknessMm = 18))
    check(inset.issues.isEmpty() && inset.panels.size == 4, "four inset panels")
    check(inset.panels.all { it.widthMm == 396 && it.heightMm == 346 }, "equal panel sizes")
    check(inset.panels[1].xOffsetMm - (inset.panels[0].xOffsetMm + inset.panels[0].widthMm) == 2, "horizontal gap")
    check(inset.panels[2].yOffsetMm - (inset.panels[0].yOffsetMm + inset.panels[0].heightMm) == 2, "vertical gap")
    val overlay = ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(600, 700, mount = FacadeMount.OVERLAY, overlayMm = 15))
    check(overlay.panels.single().widthMm == 630 && overlay.panels.single().xOffsetMm == -15, "overlay dimensions")
    check(ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(100, 100, 0)).issues.any { it.code == FurnitureBuildIssueCode.INVALID_COUNT }, "zero column rejected")
    check(ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(10, 10, 3, 3, 8, 8, mount = FacadeMount.INSET, overlayMm = 0)).issues.any { it.code == FurnitureBuildIssueCode.INSUFFICIENT_OPENING }, "insufficient opening")
    check(ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(100, 100, horizontalGapMm = -1)).issues.any { it.code == FurnitureBuildIssueCode.NEGATIVE_GAP }, "negative gap rejected")
    check(ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(100, 100, panelThicknessMm = 0)).issues.any { it.code == FurnitureBuildIssueCode.INVALID_THICKNESS }, "zero thickness rejected")
    val hugeFacade = ParametricFurnitureEngine.layoutFacades(FacadeLayoutSpec(Int.MAX_VALUE, 700, overlayMm = Int.MAX_VALUE))
    check(hugeFacade.panels.isEmpty() && hugeFacade.issues.any { it.code == FurnitureBuildIssueCode.INVALID_DIMENSION_RANGE }, "oversized facade inputs rejected before overflow")

    val drawer = ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(500, 200, 500, boxHeightMm = 150))
    check(drawer.issues.isEmpty() && drawer.dimensions != null, "valid drawer")
    val d = drawer.dimensions!!
    check(d.outerWidthMm == 474 && d.outerDepthMm == 490 && d.outerHeightMm == 150, "drawer outer dimensions")
    check(d.innerWidthMm == 442 && d.innerDepthMm == 474 && d.bottomWidthMm == 442, "drawer inner and bottom dimensions")
    check(ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(20, 200, 500, boxHeightMm = 150)).issues.any { it.code == FurnitureBuildIssueCode.DRAWER_TOO_WIDE }, "slide clearance width rejection")
    check(ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(500, 100, 20, boxHeightMm = 150)).issues.any { it.code == FurnitureBuildIssueCode.DRAWER_TOO_DEEP }, "drawer depth rejection")
    check(ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(500, 100, 500, boxHeightMm = 150)).issues.any { it.code == FurnitureBuildIssueCode.DRAWER_TOO_TALL }, "drawer height rejection")
    check(ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(500, 200, 500, slideClearancePerSideMm = -1, boxHeightMm = 100)).issues.any { it.code == FurnitureBuildIssueCode.INVALID_SLIDE_CLEARANCE }, "negative slide gap rejected")
    check(ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(500, 200, 500, sideThicknessMm = 0, boxHeightMm = 100)).issues.any { it.code == FurnitureBuildIssueCode.INVALID_THICKNESS }, "zero side thickness rejected")
    val hugeDrawer = ParametricFurnitureEngine.sizeDrawer(DrawerBoxSpec(Int.MAX_VALUE, 200, Int.MAX_VALUE, boxHeightMm = Int.MAX_VALUE))
    check(hugeDrawer.dimensions == null && hugeDrawer.issues.any { it.code == FurnitureBuildIssueCode.INVALID_DIMENSION_RANGE }, "oversized drawer inputs rejected before overflow")
    println("ParametricFurnitureSmoke: $checks checks passed")
}
