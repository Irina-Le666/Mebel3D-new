package ru.mebel3d.app.core

/** Reusable parametric furniture modules. Coordinates and dimensions are millimetres. */
enum class FurnitureRoomCategory { KITCHEN, BEDROOM, LIVING_ROOM, BATHROOM, UTILITY }
enum class FurnitureModuleKind {
    BASE_CABINET, WALL_CABINET, TALL_CABINET, CORNER_BASE, CORNER_WALL,
    DRAWER_BASE, SINK_BASE, OVEN_BASE, OPEN_SHELF, WARDROBE, BED_SIDE_TABLE,
    DRESSER, VANITY, BATHROOM_TALL, TV_STAND
}
enum class CountertopKind { NONE, CONTINUOUS, INDIVIDUAL }

data class FurnitureModuleSpec(
    val id: String,
    val name: String,
    val category: FurnitureRoomCategory,
    val kind: FurnitureModuleKind,
    val widthMm: Int,
    val depthMm: Int,
    val heightMm: Int,
    val panelThicknessMm: Int = 18,
    val backThicknessMm: Int = 6,
    val shelfCount: Int = 0,
    val shelfThicknessMm: Int = 18,
    val toeKickHeightMm: Int = 100,
    val toeKickDepthInsetMm: Int = 50,
    val countertopThicknessMm: Int = 38,
    val countertopOverhangMm: Int = 20,
    val countertop: CountertopKind = CountertopKind.NONE,
    val materialId: String = "wood-oak",
    val backMaterialId: String = "back-panel",
    val includeBack: Boolean = true,
    val includeTop: Boolean = true,
    val includeBottom: Boolean = true
)

data class FurnitureModuleIssue(val code: String, val message: String, val moduleId: String? = null)
data class FurnitureModuleResult(val assembly: FurnitureAssembly?, val issues: List<FurnitureModuleIssue>)

data class FurnitureRunSpec(
    val id: String,
    val name: String,
    val category: FurnitureRoomCategory,
    val modules: List<FurnitureModuleSpec>,
    val gapBetweenModulesMm: Int = 2,
    val countertop: CountertopKind = CountertopKind.NONE,
    val countertopThicknessMm: Int = 38,
    val countertopOverhangMm: Int = 20
)
data class FurnitureRunResult(
    val assemblies: List<FurnitureAssembly>,
    val issues: List<FurnitureModuleIssue>,
    val totalWidthMm: Int,
    val maximumHeightMm: Int,
    val maximumDepthMm: Int,
    val cutList: List<BoardCut>
)

object FurnitureModuleCatalog {
    fun standard(category: FurnitureRoomCategory): List<FurnitureModuleSpec> = when (category) {
        FurnitureRoomCategory.KITCHEN -> listOf(
            spec("base-600", "Нижний шкаф 600", category, FurnitureModuleKind.BASE_CABINET, 600, 560, 720, 1, CountertopKind.INDIVIDUAL),
            spec("base-450", "Нижний шкаф 450", category, FurnitureModuleKind.BASE_CABINET, 450, 560, 720, 1, CountertopKind.INDIVIDUAL),
            spec("drawer-600", "Тумба с ящиками 600", category, FurnitureModuleKind.DRAWER_BASE, 600, 560, 720, 0, CountertopKind.INDIVIDUAL),
            spec("sink-600", "Мойка 600", category, FurnitureModuleKind.SINK_BASE, 600, 560, 720, 0, CountertopKind.INDIVIDUAL),
            spec("oven-600", "Духовой шкаф 600", category, FurnitureModuleKind.OVEN_BASE, 600, 560, 720, 0, CountertopKind.INDIVIDUAL),
            spec("wall-600", "Верхний шкаф 600", category, FurnitureModuleKind.WALL_CABINET, 600, 320, 720, 2),
            spec("wall-450", "Верхний шкаф 450", category, FurnitureModuleKind.WALL_CABINET, 450, 320, 720, 2),
            spec("tall-600", "Пенал 600", category, FurnitureModuleKind.TALL_CABINET, 600, 580, 2100, 3),
            spec("corner-900", "Угловая нижняя тумба 900", category, FurnitureModuleKind.CORNER_BASE, 900, 900, 720, 1),
            spec("open-600", "Открытая полка 600", category, FurnitureModuleKind.OPEN_SHELF, 600, 300, 360, 2)
        )
        FurnitureRoomCategory.BEDROOM -> listOf(
            spec("wardrobe-600", "Секция шкафа 600", category, FurnitureModuleKind.WARDROBE, 600, 600, 2400, 4),
            spec("wardrobe-900", "Секция шкафа 900", category, FurnitureModuleKind.WARDROBE, 900, 600, 2400, 4),
            spec("dresser-800", "Комод 800", category, FurnitureModuleKind.DRESSER, 800, 450, 850, 2),
            spec("bedside-500", "Прикроватная тумба 500", category, FurnitureModuleKind.BED_SIDE_TABLE, 500, 400, 500, 1)
        )
        FurnitureRoomCategory.BATHROOM -> listOf(
            spec("vanity-800", "Тумба под раковину 800", category, FurnitureModuleKind.VANITY, 800, 450, 600, 1),
            spec("bath-tall-400", "Пенал для ванной 400", category, FurnitureModuleKind.BATHROOM_TALL, 400, 350, 1900, 4),
            spec("bath-wall-600", "Навесной шкаф 600", category, FurnitureModuleKind.WALL_CABINET, 600, 300, 700, 2)
        )
        FurnitureRoomCategory.LIVING_ROOM -> listOf(
            spec("tv-1800", "Тумба под ТВ 1800", category, FurnitureModuleKind.TV_STAND, 1800, 450, 500, 2),
            spec("living-shelf-800", "Стеллаж 800", category, FurnitureModuleKind.OPEN_SHELF, 800, 350, 2100, 5),
            spec("living-wardrobe-900", "Шкаф 900", category, FurnitureModuleKind.WARDROBE, 900, 550, 2200, 4)
        )
        FurnitureRoomCategory.UTILITY -> listOf(
            spec("utility-600", "Хозяйственный шкаф 600", category, FurnitureModuleKind.TALL_CABINET, 600, 600, 2100, 4),
            spec("utility-shelf-900", "Стеллаж 900", category, FurnitureModuleKind.OPEN_SHELF, 900, 400, 2000, 5)
        )
    }

    private fun spec(id: String, name: String, category: FurnitureRoomCategory, kind: FurnitureModuleKind,
                     w: Int, d: Int, h: Int, shelves: Int,
                     top: CountertopKind = CountertopKind.NONE) = FurnitureModuleSpec(
        id, name, category, kind, w, d, h, shelfCount = shelves,
        countertop = top, includeTop = kind != FurnitureModuleKind.OPEN_SHELF,
        includeBottom = kind != FurnitureModuleKind.OPEN_SHELF
    )
}

object FurnitureModuleEngine {
    fun build(spec: FurnitureModuleSpec): FurnitureModuleResult {
        val errors = mutableListOf<FurnitureModuleIssue>()
        if (spec.id.isBlank() || spec.name.isBlank() || minOf(spec.widthMm, spec.depthMm, spec.heightMm) <= 0)
            errors += FurnitureModuleIssue("INVALID_SIZE", "Название и габариты модуля должны быть заданы", spec.id)
        val boundedDimensions = listOf(spec.widthMm, spec.depthMm, spec.heightMm, spec.panelThicknessMm,
            spec.backThicknessMm, spec.shelfThicknessMm, spec.toeKickHeightMm, spec.toeKickDepthInsetMm,
            spec.countertopThicknessMm, spec.countertopOverhangMm)
        if (boundedDimensions.any { it < 0 || it > ParametricDimensionLimits.MAX_MM })
            errors += FurnitureModuleIssue("INVALID_DIMENSION_RANGE", "Размеры модуля и аксессуаров должны быть в диапазоне до ${ParametricDimensionLimits.MAX_MM} мм", spec.id)
        if (spec.panelThicknessMm <= 0 || spec.backThicknessMm < 0 || spec.shelfThicknessMm <= 0)
            errors += FurnitureModuleIssue("INVALID_THICKNESS", "Некорректная толщина панелей", spec.id)
        if (spec.toeKickHeightMm < 0 || spec.toeKickDepthInsetMm < 0 || spec.countertopThicknessMm <= 0 || spec.countertopOverhangMm < 0)
            errors += FurnitureModuleIssue("INVALID_ACCESSORY", "Некорректные параметры цоколя или столешницы", spec.id)
        if (spec.toeKickHeightMm.toLong() >= spec.heightMm.toLong() ||
            spec.toeKickDepthInsetMm.toLong() >= spec.depthMm.toLong())
            errors += FurnitureModuleIssue("INVALID_ACCESSORY", "Цоколь не должен занимать всю высоту или глубину модуля", spec.id)
        if (spec.countertop != CountertopKind.NONE &&
            (spec.widthMm.toLong() + 2L * spec.countertopOverhangMm > ParametricDimensionLimits.MAX_MM.toLong() ||
                spec.depthMm.toLong() + spec.countertopOverhangMm > ParametricDimensionLimits.MAX_MM.toLong()))
            errors += FurnitureModuleIssue("INVALID_DIMENSION_RANGE", "Столешница с вылетом превышает допустимый габарит", spec.id)
        if (spec.shelfCount !in 0..80) errors += FurnitureModuleIssue("INVALID_SHELF_COUNT", "Количество полок должно быть от 0 до 80", spec.id)
        if (errors.isNotEmpty()) return FurnitureModuleResult(null, errors)

        val toeKick = spec.kind in setOf(FurnitureModuleKind.BASE_CABINET, FurnitureModuleKind.DRAWER_BASE,
            FurnitureModuleKind.SINK_BASE, FurnitureModuleKind.OVEN_BASE, FurnitureModuleKind.CORNER_BASE)
        val hasCountertop = spec.countertop != CountertopKind.NONE
        val carcassHeight = spec.heightMm - (if (toeKick) spec.toeKickHeightMm else 0) -
            (if (hasCountertop) spec.countertopThicknessMm else 0)
        if (carcassHeight <= 2 * spec.panelThicknessMm)
            return FurnitureModuleResult(null, listOf(FurnitureModuleIssue("NO_INTERNAL_SPACE", "Цоколь и панели не оставляют места для корпуса", spec.id)))

        val carcass = ParametricAssemblyEngine.buildCarcass(CarcassSpec(
            id = spec.id, name = spec.name, widthMm = spec.widthMm, depthMm = spec.depthMm,
            heightMm = carcassHeight, panelThicknessMm = spec.panelThicknessMm,
            backThicknessMm = spec.backThicknessMm, shelfCount = spec.shelfCount,
            shelfThicknessMm = spec.shelfThicknessMm, materialId = spec.materialId,
            includeBack = spec.includeBack, includeTop = spec.includeTop, includeBottom = spec.includeBottom,
            backMaterialId = spec.backMaterialId
        ))
        if (carcass.assembly == null) return FurnitureModuleResult(null,
            carcass.issues.map { FurnitureModuleIssue(it.code.name, it.message, spec.id) })

        val verticalOffset = if (toeKick) spec.toeKickHeightMm else 0
        val parts = carcass.assembly.parts.map { part ->
            part.copy(localPosition = part.localPosition.copy(z = part.localPosition.z + verticalOffset))
        }.toMutableList()
        if (toeKick && spec.toeKickHeightMm > 0) {
            val kickDepth = (spec.depthMm - spec.toeKickDepthInsetMm).coerceAtLeast(1)
            parts += FurniturePart("${spec.id}-toe-kick", "Цоколь", Size3Mm(spec.widthMm, kickDepth, spec.toeKickHeightMm),
                Vec3Mm(0, spec.toeKickDepthInsetMm, 0), materialId = spec.materialId)
        }
        val wantsTop = hasCountertop
        if (wantsTop) {
            parts += FurniturePart("${spec.id}-countertop", "Столешница",
                Size3Mm(spec.widthMm + 2 * spec.countertopOverhangMm,
                    spec.depthMm + spec.countertopOverhangMm, spec.countertopThicknessMm),
                Vec3Mm(-spec.countertopOverhangMm, -spec.countertopOverhangMm, spec.heightMm - spec.countertopThicknessMm),
                materialId = "countertop")
        }
        val assembly = carcass.assembly.copy(parts = parts)
        val duplicateIds = parts.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) return FurnitureModuleResult(null,
            listOf(FurnitureModuleIssue("DUPLICATE_PART_ID", "Повторяются ID деталей: ${duplicateIds.joinToString()}", spec.id)))
        return FurnitureModuleResult(assembly, emptyList())
    }

    /** Places a run left-to-right. Modules retain individual assemblies for editing and manufacturing. */
    fun buildRun(spec: FurnitureRunSpec): FurnitureRunResult {
        val issues = mutableListOf<FurnitureModuleIssue>()
        if (spec.id.isBlank() || spec.name.isBlank()) issues += FurnitureModuleIssue("INVALID_RUN", "Укажите название композиции")
        if (spec.modules.isEmpty()) issues += FurnitureModuleIssue("EMPTY_RUN", "Добавьте хотя бы один модуль")
        if (spec.gapBetweenModulesMm < 0 || spec.countertopThicknessMm <= 0 || spec.countertopOverhangMm < 0 ||
            spec.gapBetweenModulesMm > ParametricDimensionLimits.MAX_MM ||
            spec.countertopThicknessMm > ParametricDimensionLimits.MAX_MM ||
            spec.countertopOverhangMm > ParametricDimensionLimits.MAX_MM)
            issues += FurnitureModuleIssue("INVALID_RUN_GAP", "Проверьте зазоры и параметры столешницы")
        if (spec.modules.any { it.category != spec.category }) issues += FurnitureModuleIssue("CATEGORY_MISMATCH", "Все модули композиции должны относиться к одной категории")
        if (spec.modules.map { it.id }.distinct().size != spec.modules.size) issues += FurnitureModuleIssue("DUPLICATE_MODULE_ID", "ID модулей в композиции должны быть уникальными")
        if (issues.isNotEmpty()) return FurnitureRunResult(emptyList(), issues, 0, 0, 0, emptyList())

        // Validate aggregate dimensions before any Int cursor arithmetic or geometry allocation.
        val totalWidthLong = spec.modules.sumOf { it.widthMm.toLong() } +
            spec.gapBetweenModulesMm.toLong() * (spec.modules.size - 1).toLong()
        val maxDimension = ParametricDimensionLimits.MAX_MM.toLong()
        if (spec.modules.any { it.widthMm <= 0 || it.depthMm <= 0 || it.heightMm <= 0 ||
                it.widthMm > maxDimension || it.depthMm > maxDimension || it.heightMm > maxDimension } ||
            totalWidthLong <= 0L || totalWidthLong > maxDimension) {
            issues += FurnitureModuleIssue("INVALID_RUN_DIMENSION_RANGE", "Суммарная ширина или габариты модулей выходят за допустимый диапазон")
            return FurnitureRunResult(emptyList(), issues, 0, 0, 0, emptyList())
        }
        if (spec.countertop == CountertopKind.CONTINUOUS &&
            (totalWidthLong + 2L * spec.countertopOverhangMm > maxDimension ||
                spec.modules.maxOf { it.depthMm }.toLong() + spec.countertopOverhangMm > maxDimension ||
                spec.modules.any { it.heightMm <= spec.countertopThicknessMm })) {
            issues += FurnitureModuleIssue("INVALID_COUNTERTOP_DIMENSION", "Общая столешница или высота модулей выходит за допустимые границы")
            return FurnitureRunResult(emptyList(), issues, 0, 0, 0, emptyList())
        }

        var cursorX = 0L
        val assemblies = mutableListOf<FurnitureAssembly>()
        spec.modules.forEachIndexed { index, module ->
            val moduleSpec = module.copy(id = "${spec.id}-${module.id}",
                heightMm = if (spec.countertop == CountertopKind.CONTINUOUS) module.heightMm - spec.countertopThicknessMm else module.heightMm,
                countertop = if (spec.countertop == CountertopKind.INDIVIDUAL) CountertopKind.INDIVIDUAL else CountertopKind.NONE)
            val built = build(moduleSpec)
            if (built.assembly == null) {
                issues += built.issues.map { it.copy(moduleId = module.id) }
            } else {
                val placed = built.assembly.copy(position = Vec3Mm(cursorX.toInt(), 0, 0))
                val geometryIssues = GeometryEngine.validateAssembly(placed)
                if (geometryIssues.isNotEmpty()) {
                    issues += geometryIssues.map { FurnitureModuleIssue("INVALID_GENERATED_GEOMETRY", it.message, module.id) }
                } else {
                    assemblies += placed
                }
            }
            // Advance by the requested module footprint even when it fails to build. This
            // keeps diagnostics and all later offsets tied to the user's original layout.
            cursorX += module.widthMm.toLong() + if (index < spec.modules.lastIndex) spec.gapBetweenModulesMm.toLong() else 0L
        }
        if (issues.isNotEmpty()) return FurnitureRunResult(
            emptyList(), issues, totalWidthLong.toInt(),
            spec.modules.maxOfOrNull { it.heightMm } ?: 0,
            spec.modules.maxOfOrNull { it.depthMm } ?: 0,
            emptyList()
        )

        if (spec.countertop == CountertopKind.CONTINUOUS) {
            val maxDepth = spec.modules.maxOf { it.depthMm }
            val maxTop = spec.modules.maxOf { it.heightMm }
            val counter = FurniturePart("${spec.id}-continuous-countertop", "Общая столешница",
                Size3Mm((cursorX + 2L * spec.countertopOverhangMm).toInt(),
                    (maxDepth.toLong() + spec.countertopOverhangMm).toInt(), spec.countertopThicknessMm),
                Vec3Mm(-spec.countertopOverhangMm, -spec.countertopOverhangMm, maxTop - spec.countertopThicknessMm), materialId = "countertop")
            val first = assemblies.first()
            val withCountertop = first.copy(parts = first.parts.filterNot { it.name == "Столешница" } + counter)
            val counterIssues = GeometryEngine.validateAssembly(withCountertop)
            if (counterIssues.isNotEmpty()) return FurnitureRunResult(emptyList(), counterIssues.map {
                FurnitureModuleIssue("INVALID_COUNTERTOP_GEOMETRY", it.message, spec.modules.first().id)
            }, totalWidthLong.toInt(), maxTop, maxDepth, emptyList())
            assemblies[0] = withCountertop
        }
        val cuts = assemblies.flatMap { GeometryEngine.cutList(it) }
        return FurnitureRunResult(assemblies, emptyList(), totalWidthLong.toInt(),
            spec.modules.maxOf { it.heightMm }, spec.modules.maxOf { it.depthMm }, cuts)
    }
}