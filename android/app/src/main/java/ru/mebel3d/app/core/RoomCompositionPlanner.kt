package ru.mebel3d.app.core

import kotlin.math.hypot

/** A coordinated multi-wall furniture composition. Requests must belong to one room. */
data class RoomWallRun(val request: RoomLayoutRequest)
data class RoomCompositionRequest(
    val id: String,
    val name: String,
    val roomId: String,
    val wallRuns: List<RoomWallRun>,
    val requireKitchenWorkTriangle: Boolean = false,
    val sinkModuleId: String? = null,
    val hobModuleId: String? = null,
    val refrigeratorModuleId: String? = null,
    val minimumTriangleSideMm: Int = 900,
    val maximumTriangleSideMm: Int = 3000
)
data class RoomCompositionIssue(val code: String, val message: String, val moduleIds: List<String> = emptyList())
data class RoomCompositionResult(
    val request: RoomCompositionRequest,
    val wallResults: List<RoomLayoutResult>,
    val issues: List<RoomCompositionIssue>,
    val totalModules: Int,
    val totalWallLengthMm: Int,
    /** Sum of the two largest cut dimensions for every physical board, in square metres. */
    val totalMaterialAreaSquareMetres: Double,
    /** Net board-face area grouped by material ID; excludes kerf, edge banding and waste allowance. */
    val materialAreaByIdSquareMetres: Map<String, Double> = emptyMap()
) { val isValid: Boolean get() = issues.isEmpty() && wallResults.all { it.isValid } }

object RoomCompositionPlanner {
    private data class Footprint(val id: String, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)

    fun plan(project: ProjectModel, request: RoomCompositionRequest): RoomCompositionResult {
        val issues = mutableListOf<RoomCompositionIssue>()
        val room = project.rooms.firstOrNull { it.id == request.roomId }
        if (request.id.isBlank() || request.name.isBlank() || room == null || request.wallRuns.isEmpty()) {
            return RoomCompositionResult(request, emptyList(), listOf(RoomCompositionIssue("INVALID_COMPOSITION", "Укажите проект, помещение и хотя бы одну стену")), 0, 0, 0.0)
        }
        if (request.wallRuns.any { it.request.roomId != request.roomId })
            issues += RoomCompositionIssue("ROOM_MISMATCH", "Все части композиции должны относиться к одному помещению")
        val sides = request.wallRuns.map { it.request.wallSide }
        if (sides.distinct().size != sides.size)
            issues += RoomCompositionIssue("DUPLICATE_WALL", "Для каждой стены задайте одну общую линию модулей")
        val moduleIds = request.wallRuns.flatMap { it.request.modules.map(FurnitureModuleSpec::id) }
        if (moduleIds.distinct().size != moduleIds.size)
            issues += RoomCompositionIssue("DUPLICATE_MODULE", "ID модулей должны быть уникальными во всей композиции")
        if (issues.isNotEmpty()) return RoomCompositionResult(request, emptyList(), issues, moduleIds.size, 0, 0.0)

        val results = request.wallRuns.map { RoomLayoutPlanner.plan(project, it.request) }
        results.flatMap { it.issues }.forEach { issues += RoomCompositionIssue(it.code, it.message, listOfNotNull(it.relatedId)) }

        // Check collisions between different wall runs, including corner intersections.
        val footprints = mutableListOf<Footprint>()
        request.wallRuns.zip(results).forEach { (run, result) ->
            result.placements.forEach { p ->
                val spec = run.request.modules.first { it.id == p.moduleId }
                val (w, d) = GeometryEngine.rotatedFootprint(Size3Mm(spec.widthMm, spec.depthMm, spec.heightMm), p.rotationDeg)
                footprints += Footprint(p.moduleId, p.center.x-w/2, p.center.x+w/2, p.center.y-d/2, p.center.y+d/2)
            }
        }
        for (i in footprints.indices) for (j in i+1 until footprints.size) {
            val a = footprints[i]; val b = footprints[j]
            if (a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY)
                issues += RoomCompositionIssue("CORNER_COLLISION", "Модули ${a.id} и ${b.id} пересекаются в углу или на соседних стенах", listOf(a.id, b.id))
        }

        if (request.requireKitchenWorkTriangle) {
            val required = listOf(request.sinkModuleId, request.hobModuleId, request.refrigeratorModuleId)
            if (required.any { it == null } || required.filterNotNull().any { id -> footprints.none { it.id == id } }) {
                issues += RoomCompositionIssue("TRIANGLE_MODULE_MISSING", "Для рабочего треугольника укажите существующие модули мойки, варочной поверхности и холодильника")
            } else if (request.minimumTriangleSideMm <= 0 || request.maximumTriangleSideMm < request.minimumTriangleSideMm) {
                issues += RoomCompositionIssue("INVALID_TRIANGLE_LIMITS", "Некорректные пределы рабочего треугольника")
            } else {
                val centers = required.filterNotNull().map { id -> footprints.first { it.id == id }.let { ((it.minX+it.maxX)/2.0) to ((it.minY+it.maxY)/2.0) } }
                val sides = listOf(hypot(centers[0].first-centers[1].first, centers[0].second-centers[1].second),
                    hypot(centers[1].first-centers[2].first, centers[1].second-centers[2].second),
                    hypot(centers[2].first-centers[0].first, centers[2].second-centers[0].second))
                if (sides.any { it < request.minimumTriangleSideMm || it > request.maximumTriangleSideMm })
                    issues += RoomCompositionIssue("WORK_TRIANGLE_OUT_OF_RANGE", "Одна или несколько сторон рабочего треугольника выходят за заданный диапазон")
            }
        }
        // Material area must follow the same physical-board orientation as the cut list.
        // width*depth is wrong for upright side panels; cutList sorts all three dimensions
        // and uses the two largest as the board face.
        val cuts = results.flatMap { it.placements }.flatMap { GeometryEngine.cutList(it.assembly) }
        val areaByMaterial = cuts.groupBy { it.materialId }.mapValues { (_, materialCuts) ->
            materialCuts.sumOf { it.widthMm.toDouble() * it.lengthMm.toDouble() / 1_000_000.0 }
        }.toSortedMap()
        val area = areaByMaterial.values.sum()
        return RoomCompositionResult(request, results, issues.distinctBy { Triple(it.code, it.moduleIds, it.message) },
            moduleIds.size, results.sumOf { it.usedWallLengthMm }, area, areaByMaterial)
    }

    /** Atomic application: either all wall runs are committed or the original project is returned nowhere. */
    fun apply(project: ProjectModel, result: RoomCompositionResult): ProjectModel {
        require(result.isValid) { result.issues.firstOrNull()?.message ?: "Композиция содержит ошибки" }
        val prefix = result.request.id
        val allIds = result.wallResults.flatMap { it.placements }.map { "$prefix-${it.moduleId}" }
        require(allIds.distinct().size == allIds.size && project.assemblies.none { it.id in allIds }) { "ID сборок композиции уже используются" }
        val assemblies = result.wallResults.flatMapIndexed { index, wall ->
            RoomLayoutPlanner.apply(project, wall, "$prefix-${index}").assemblies.filter { candidate -> candidate.id.startsWith("$prefix-$index-") }
        }
        require(assemblies.map { it.id }.distinct().size == assemblies.size)
        return project.copy(assemblies = project.assemblies + assemblies)
    }
}
