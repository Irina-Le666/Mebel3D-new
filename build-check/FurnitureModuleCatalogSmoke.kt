package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun check(ok: Boolean, label: String) { check(ok) { label }; checks++ }

    val kitchen = FurnitureModuleCatalog.standard(FurnitureRoomCategory.KITCHEN)
    check(kitchen.size >= 9, "kitchen catalog")
    check(kitchen.any { it.kind == FurnitureModuleKind.SINK_BASE }, "sink base preset")
    check(kitchen.any { it.kind == FurnitureModuleKind.TALL_CABINET }, "tall cabinet preset")

    val base = FurnitureModuleEngine.build(kitchen.first { it.id == "base-600" })
    check(base.assembly != null, "base cabinet builds")
    check(base.assembly!!.parts.any { it.name == "Цоколь" }, "toe kick generated")
    check(base.assembly!!.parts.any { it.name == "Столешница" }, "individual countertop generated")
    val individualCounter = base.assembly!!.parts.first { it.name == "Столешница" }
    check(individualCounter.localPosition.x == -20 && individualCounter.size.width == 640, "individual countertop has symmetric side overhang")
    check(individualCounter.localPosition.y == -20 && individualCounter.size.depth == 580,
        "countertop overhang is on the user-facing front edge, not behind the carcass")
    val customBack = FurnitureModuleEngine.build(kitchen.first { it.id == "base-600" }.copy(backMaterialId = "custom-back-hdf"))
    check(customBack.assembly!!.parts.first { it.name == "Задняя стенка" }.materialId == "custom-back-hdf",
        "module-specific rear panel material is propagated into generated geometry")
    check(GeometryEngine.validateAssembly(base.assembly!!).isEmpty(), "individual countertop assembly passes geometry validation")
    check(ProjectValidator.validate(ProjectModel("counter-project", "Столешница", assemblies=listOf(base.assembly!!))).isEmpty(), "overhang assembly passes whole-project validation")
    check(AssemblyDiagnosticsEngine.inspect(base.assembly!!).none { it.code == AssemblyDiagnosticCode.NEGATIVE_POSITION }, "overhang is not a negative-elevation error")
    val baseParts = base.assembly!!.parts
    check(baseParts.filter { it.name.startsWith("Боковина") }.all { it.localPosition.z == 100 }, "carcass lifted above toe kick")
    check(baseParts.first { it.name == "Столешница" }.localPosition.z == 682, "countertop at overall top height")

    val invalid = FurnitureModuleEngine.build(kitchen.first().copy(widthMm = 0))
    check(invalid.assembly == null && invalid.issues.isNotEmpty(), "invalid module rejected")
    val noToeKick = FurnitureModuleEngine.build(kitchen.first().copy(toeKickHeightMm = 0))
    check(noToeKick.assembly != null && noToeKick.assembly!!.parts.none { it.name == "Цоколь" }, "zero-height toe kick is omitted instead of creating invalid geometry")

    val run = FurnitureModuleEngine.buildRun(FurnitureRunSpec(
        "kitchen-line", "Линия кухни", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen.first { it.id == "base-600" }, kitchen.first { it.id == "sink-600" }, kitchen.first { it.id == "drawer-600" }),
        countertop = CountertopKind.CONTINUOUS
    ))
    check(run.issues.isEmpty(), "kitchen run builds")
    check(run.assemblies.size == 3, "module assemblies retained")
    check(run.totalWidthMm == 1804, "run width includes gaps")
    check(run.assemblies.first().parts.any { it.name == "Общая столешница" }, "continuous countertop generated")
    check(run.cutList.isNotEmpty(), "run cut list generated")
    val continuousCounter = run.assemblies.first().parts.first { it.name == "Общая столешница" }
    check(continuousCounter.localPosition.x == -20 && continuousCounter.size.width == run.totalWidthMm + 40, "continuous countertop has symmetric side overhang")
    check(continuousCounter.localPosition.y == -20 && continuousCounter.size.depth == 580,
        "continuous countertop extends toward the room front")
    check(GeometryEngine.validateAssembly(run.assemblies.first()).isEmpty(), "continuous countertop assembly passes validation")
    check(run.assemblies.flatMap { it.parts }.map { it.id }.distinct().size == run.assemblies.sumOf { it.parts.size },
        "all generated run part IDs are globally unique")
    check(run.cutList.size == run.assemblies.sumOf { it.parts.size }, "cut list contains every generated board exactly once")
    check(run.cutList.map { it.partId }.toSet() == run.assemblies.flatMap { it.parts }.map { it.id }.toSet(),
        "cut list part IDs match actual generated geometry")

    val brokenMiddle = kitchen.first { it.id == "sink-600" }.copy(panelThicknessMm = 0)
    val failedRun = FurnitureModuleEngine.buildRun(FurnitureRunSpec(
        "atomic-run", "Композиция с ошибкой", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen.first { it.id == "base-600" }, brokenMiddle, kitchen.first { it.id == "drawer-600" })
    ))
    check(failedRun.issues.any { it.moduleId == "sink-600" }, "failed module issue identifies original catalog module")
    check(failedRun.assemblies.isEmpty() && failedRun.cutList.isEmpty(), "failed run is atomic and exposes no partial manufacturing output")
    check(failedRun.totalWidthMm == 1804, "failed run retains requested footprint instead of compressing later modules")

    val invalidContinuousRun = FurnitureModuleEngine.buildRun(FurnitureRunSpec(
        "invalid-counter-run", "Композиция со столешницей", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen.first { it.id == "base-600" }, kitchen.first { it.id == "sink-600" }),
        countertop = CountertopKind.CONTINUOUS, countertopThicknessMm = 800
    ))
    check(invalidContinuousRun.assemblies.isEmpty() && invalidContinuousRun.cutList.isEmpty(),
        "continuous countertop failure never returns a partially manufacturable run")

    val overflowRun = FurnitureModuleEngine.buildRun(FurnitureRunSpec("overflow", "Переполнение", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen.first().copy(widthMm = 80_000), kitchen.first().copy(id = "second", widthMm = 80_000))))
    check(overflowRun.assemblies.isEmpty() && overflowRun.issues.any { it.code == "INVALID_RUN_DIMENSION_RANGE" }, "oversized run rejected before cursor overflow")
    val oversizedCounter = FurnitureModuleEngine.build(kitchen.first().copy(widthMm = 60_000, countertopOverhangMm = 30_000, countertop = CountertopKind.INDIVIDUAL))
    check(oversizedCounter.assembly == null && oversizedCounter.issues.any { it.code == "INVALID_DIMENSION_RANGE" }, "countertop overhang exceeding geometry limit rejected")
    val badRunCounter = FurnitureModuleEngine.buildRun(FurnitureRunSpec("counter-overflow", "Столешница", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen.first().copy(widthMm = 50_000)), countertop = CountertopKind.CONTINUOUS, countertopOverhangMm = 30_000))
    check(badRunCounter.assemblies.isEmpty() && badRunCounter.issues.any { it.code == "INVALID_COUNTERTOP_DIMENSION" }, "continuous countertop overflow rejected")

    val duplicate = FurnitureModuleEngine.buildRun(FurnitureRunSpec("x", "x", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen[0], kitchen[0])))
    check(duplicate.issues.any { it.code == "DUPLICATE_MODULE_ID" }, "duplicate module IDs rejected")
    val mixed = FurnitureModuleEngine.buildRun(FurnitureRunSpec("x", "x", FurnitureRoomCategory.KITCHEN,
        listOf(kitchen[0], FurnitureModuleCatalog.standard(FurnitureRoomCategory.BATHROOM)[0])))
    check(mixed.issues.any { it.code == "CATEGORY_MISMATCH" }, "mixed categories rejected")

    for (category in FurnitureRoomCategory.values()) {
        val presets = FurnitureModuleCatalog.standard(category)
        check(presets.isNotEmpty(), "catalog $category non-empty")
        check(presets.all { FurnitureModuleEngine.build(it).assembly != null }, "presets $category build")
    }
    println("FurnitureModuleCatalogSmoke: $checks checks passed")
}
