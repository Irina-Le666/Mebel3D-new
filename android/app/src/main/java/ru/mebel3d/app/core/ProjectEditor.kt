package ru.mebel3d.app.core
import kotlin.math.roundToInt

/** Outcome of a proximity snap. If snapped=false, the original immutable project is returned. */
data class FurnitureSnapResult(val project: ProjectModel, val snapped: Boolean, val target: String?, val distanceMm: Int)

/** A scene guide shown while dragging one part near another part of the same assembly. */
data class AssemblySnapGuide(
    val axis: String,
    val coordinateMm: Int,
    val label: String,
    val distanceMm: Int
)

private data class AssemblySnapCandidate(
    val x: Int, val y: Int, val z: Int, val axis: String, val label: String, val distance: Int
)
private data class FurnitureSnapCandidate(val item: FurnitureItem, val target: String, val distanceMm: Int)

/** Pure project editing operations. Every edit returns a new immutable snapshot for history. */
object ProjectEditor {
    fun activateRoom(project: ProjectModel, roomId: String): ProjectModel {
        require(project.rooms.any { it.id == roomId }) { "Помещение не найдено" }
        return project.copy(activeRoomId = roomId)
    }

    fun addRoom(project: ProjectModel, room: RoomModel): ProjectModel =
        project.copy(rooms = RoomManager.addRoom(project.rooms, room), activeRoomId = room.id)

    fun renameRoom(project: ProjectModel, roomId: String, name: String): ProjectModel {
        require(name.isNotBlank()) { "Название помещения не может быть пустым" }
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        return project.copy(rooms = RoomManager.updateRoom(project.rooms, room.copy(name = name)))
    }

    fun duplicateRoom(project: ProjectModel, sourceId: String, newId: String, newName: String): ProjectModel {
        require(project.rooms.any { it.id == sourceId }) { "Помещение не найдено" }
        val duplicated = RoomManager.duplicateRoom(project.rooms, sourceId, newId, newName).last()
        val furnitureCopies = project.furniture.filter { it.roomId == sourceId }.map { it.copy(id = "$newId-${it.id}", roomId = newId) }
        return project.copy(rooms = project.rooms + duplicated, furniture = project.furniture + furnitureCopies,
            activeRoomId = newId)
    }

    fun removeRoom(project: ProjectModel, roomId: String): ProjectModel {
        require(project.rooms.size > 1) { "В проекте должно остаться хотя бы одно помещение" }
        val rooms = RoomManager.removeRoom(project.rooms, roomId)
        val nextActive = if (project.activeRoomId == roomId) rooms.first().id else project.activeRoomId
        return project.copy(rooms = rooms, furniture = project.furniture.filterNot { it.roomId == roomId },
            activeRoomId = nextActive)
    }

    fun putFurniture(project: ProjectModel, item: FurnitureItem): ProjectModel {
        require(project.rooms.any { it.id == item.roomId }) { "Помещение для мебели не найдено" }
        val scoped = project.furniture.filterNot { it.id == item.id } + item
        return project.copy(furniture = scoped)
    }

    /** Moves a selected object in room coordinates, optionally snapping to a millimetre grid. */
    fun moveFurniture(project: ProjectModel, itemId: String, xMm: Int, yMm: Int, zMm: Int? = null, gridMm: Int = 50): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение для мебели не найдено")
        val candidate = GeometryEngine.move(item,
            if (gridMm > 0) GeometryEngine.snap(xMm, gridMm) else xMm,
            if (gridMm > 0) GeometryEngine.snap(yMm, gridMm) else yMm,
            zMm ?: item.position.z)
        val scoped = project.furniture.filter { it.roomId == item.roomId }
            .map { if (it.id == itemId) candidate else it }
        val issues = GeometryEngine.validateFurniture(room, scoped)
        require(issues.none { it.objectId == itemId || it.code == GeometryIssueCode.COLLISION }) {
            issues.firstOrNull()?.message ?: "Нельзя разместить объект в этой точке"
        }
        return project.copy(furniture = project.furniture.map { if (it.id == itemId) candidate else it })
    }

    /** Creates an independent copy, preserving dimensions, rotation, elevation and material. */
    fun duplicateFurniture(project: ProjectModel, itemId: String, newId: String, offsetMm: Int = 300): ProjectModel {
        require(newId.isNotBlank() && project.furniture.none { it.id == newId }) { "Новый идентификатор пуст или уже используется" }
        require(offsetMm > 0) { "Смещение копии должно быть положительным" }
        val source = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == source.roomId } ?: error("Помещение для мебели не найдено")
        val roomItems = project.furniture.filter { it.roomId == source.roomId }
        val step = maxOf(50, offsetMm)
        val directions = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to 1, -1 to -1, 1 to -1)
        for (ring in 1..40) for ((dx, dy) in directions) {
            val candidate = source.copy(id = newId, name = if (source.name.endsWith(" (копия)")) source.name else "${source.name} (копия)",
                position = source.position.copy(x = source.position.x + dx * step * ring, y = source.position.y + dy * step * ring))
            val issues = GeometryEngine.validateFurniture(room, roomItems + candidate)
            if (issues.none { it.objectId == newId || it.code == GeometryIssueCode.COLLISION })
                return project.copy(furniture = project.furniture + candidate)
        }
        error("Рядом нет свободного места для копии детали")
    }
    /** Resizes one furniture item atomically; invalid bounds and collisions reject the whole edit. */
    fun resizeFurniture(project: ProjectModel, itemId: String, widthMm: Int, depthMm: Int, heightMm: Int): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение для мебели не найдено")
        val candidate = GeometryEngine.resize(item, widthMm, if (item.footprintShape == FurnitureFootprintShape.CIRCLE) widthMm else depthMm, heightMm)
        val scoped = project.furniture.filter { it.roomId == item.roomId }.map { if (it.id == itemId) candidate else it }
        val issues = GeometryEngine.validateFurniture(room, scoped)
        require(issues.none { it.objectId == itemId || it.code == GeometryIssueCode.COLLISION }) {
            issues.firstOrNull()?.message ?: "Новые размеры создают пересечение"
        }
        return project.copy(furniture = project.furniture.map { if (it.id == itemId) candidate else it })
    }

    /** Applies a complete interior arrangement preview as one atomic project edit. */
    fun applyFurnitureArrangement(project: ProjectModel, plan: RoomFurnitureArrangementPlan): ProjectModel {
        require(plan.isComplete) { "Не все предметы удалось разместить; сначала скорректируйте план" }
        val room = project.rooms.firstOrNull { it.id == plan.roomId } ?: error("Помещение не найдено")
        val roomItems = project.furniture.filter { it.roomId == plan.roomId }
        val byId = plan.suggestions.associateBy { it.furnitureId }
        require(byId.keys == roomItems.map { it.id }.toSet()) { "План расстановки устарел или относится к другому составу мебели" }
        val updated = project.furniture.map { item ->
            if (item.roomId != plan.roomId) item else item.copy(position = requireNotNull(byId[item.id]).to)
        }
        val issues = GeometryEngine.validateFurniture(room, updated.filter { it.roomId == plan.roomId })
        require(issues.isEmpty()) { issues.firstOrNull()?.message ?: "Расстановка больше не допустима" }
        return project.copy(furniture = updated)
    }

    /** Dimension labels for a selected item, including exact clearances to each room wall. */
    fun furnitureDimensions(project: ProjectModel, itemId: String): FurnitureDimensions {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val (footW, footD) = GeometryEngine.rotatedFootprint(item)
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        return FurnitureDimensions(
            footW, footD, item.size.height,
            item.position.x - footW / 2, room.widthMm - item.position.x - footW / 2,
            item.position.y - footD / 2, room.depthMm - item.position.y - footD / 2
        )
    }

    /** Applies rotation in 90-degree increments and rejects collisions or out-of-room placement. */
    fun rotateFurniture(project: ProjectModel, itemId: String, degrees: Int): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение для мебели не найдено")
        val candidate = GeometryEngine.rotate(item, degrees)
        val scoped = project.furniture.filter { it.roomId == item.roomId }.map { if (it.id == itemId) candidate else it }
        val issues = GeometryEngine.validateFurniture(room, scoped)
        require(issues.none { it.objectId == itemId || it.code == GeometryIssueCode.COLLISION }) {
            issues.firstOrNull()?.message ?: "Поворот создаёт пересечение"
        }
        return project.copy(furniture = project.furniture.map { if (it.id == itemId) candidate else it })
    }


    /** Applies an arbitrary integer angle while retaining full room/collision validation. */
    fun rotateFurnitureTo(project: ProjectModel, itemId: String, angleDeg: Int): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val candidate = item.copy(rotationDeg = FurnitureTransformMath.normalize360(angleDeg))
        val scoped = project.furniture.filter { it.roomId == item.roomId }.map { if (it.id == itemId) candidate else it }
        val issues = GeometryEngine.validateFurniture(room, scoped)
        require(issues.none { it.objectId == itemId || it.code == GeometryIssueCode.COLLISION }) {
            issues.firstOrNull()?.message ?: "Поворот создаёт пересечение или выходит за границы комнаты"
        }
        return project.copy(furniture = project.furniture.map { if (it.id == itemId) candidate else it })
    }

    /** Changes only the selected item's height; footprint and position remain unchanged. */
    fun resizeFurnitureHeight(project: ProjectModel, itemId: String, heightMm: Int): ProjectModel {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        return resizeFurniture(project, itemId, item.size.width, item.size.depth, heightMm)
    }

    /** Aligns a furniture footprint to a selected room wall with a millimetre gap. */
    fun alignFurnitureToWall(project: ProjectModel, itemId: String, side: WallSide, gapMm: Int = 0): ProjectModel {
        require(gapMm >= 0) { "Отступ не может быть отрицательным" }
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val (width, depth) = GeometryEngine.rotatedFootprint(item)
        val x = when (side) {
            WallSide.WEST -> gapMm + width / 2
            WallSide.EAST -> room.widthMm - gapMm - width / 2
            else -> item.position.x
        }
        val y = when (side) {
            WallSide.NORTH -> gapMm + depth / 2
            WallSide.SOUTH -> room.depthMm - gapMm - depth / 2
            else -> item.position.y
        }
        return moveFurniture(project, itemId, x, y, item.position.z, gridMm = 1)
    }

    /** Aligns left/right edges (X) or near/far edges (Y) of two items in the same room. */
    fun alignFurnitureToItem(project: ProjectModel, itemId: String, targetId: String, axis: String, edge: String): ProjectModel {
        require(itemId != targetId) { "Нельзя выровнять объект относительно самого себя" }
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val target = project.furniture.firstOrNull { it.id == targetId } ?: error("Объект-ориентир не найден")
        require(item.roomId == target.roomId) { "Объекты должны находиться в одном помещении" }
        val (itemWidth, itemDepth) = GeometryEngine.rotatedFootprint(item)
        val (targetWidth, targetDepth) = GeometryEngine.rotatedFootprint(target)
        val itemHalf = when (axis.lowercase()) {
            "x" -> itemWidth / 2
            "y" -> itemDepth / 2
            else -> error("Ось должна быть X или Y")
        }
        val targetHalf = when (axis.lowercase()) {
            "x" -> targetWidth / 2
            else -> targetDepth / 2
        }
        val targetCenter = if (axis.lowercase() == "x") target.position.x else target.position.y
        val targetMin = targetCenter - targetHalf
        val targetMax = targetCenter + targetHalf
        val coordinate = when (edge.lowercase()) {
            "start" -> targetMin + itemHalf
            "end" -> targetMax - itemHalf
            "center" -> targetCenter
            else -> error("Край должен быть start, end или center")
        }
        return if (axis.lowercase() == "x") moveFurniture(project, itemId, coordinate, item.position.y, item.position.z, 1)
        else moveFurniture(project, itemId, item.position.x, coordinate, item.position.z, 1)
    }

    /**
     * Snaps a dragged furniture item to nearby wall edges or neighbouring furniture edges.
     * The closest valid candidate within tolerance is chosen; collisions and out-of-room
     * placements are never committed. Coordinates are room millimetres.
     */
    fun snapFurnitureToNearby(
        project: ProjectModel, itemId: String, toleranceMm: Int = 80, gridMm: Int = 1
    ): FurnitureSnapResult {
        require(toleranceMm >= 0 && gridMm > 0) { "Неверные параметры привязки" }
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val (w, d) = GeometryEngine.rotatedFootprint(item)
        val halfW = w / 2
        val halfD = d / 2
        val candidates = mutableListOf<Triple<Int, Int, String>>()

        // Wall candidates preserve the other coordinate and align the nearest footprint edge.
        candidates += Triple(halfW, item.position.y, "Западная стена")
        candidates += Triple(room.widthMm - halfW, item.position.y, "Восточная стена")
        candidates += Triple(item.position.x, halfD, "Северная стена")
        candidates += Triple(item.position.x, room.depthMm - halfD, "Южная стена")

        // Adjacent-edge and centre-line candidates relative to other objects in this room.
        project.furniture.filter { it.roomId == item.roomId && it.id != itemId }.forEach { other ->
            val (ow, od) = GeometryEngine.rotatedFootprint(other)
            val ohw = ow / 2; val ohd = od / 2
            val alignedY = other.position.y
            val alignedX = other.position.x
            candidates += Triple(other.position.x - ohw - halfW, alignedY, "Слева от ${other.name}")
            candidates += Triple(other.position.x + ohw + halfW, alignedY, "Справа от ${other.name}")
            candidates += Triple(alignedX, other.position.y - ohd - halfD, "Перед ${other.name}")
            candidates += Triple(alignedX, other.position.y + ohd + halfD, "За ${other.name}")
            candidates += Triple(alignedX, alignedY, "По центру ${other.name}")
        }

        val valid = candidates.mapNotNull { (rawX, rawY, label) ->
            val x = GeometryEngine.snap(rawX, gridMm)
            val y = GeometryEngine.snap(rawY, gridMm)
            val dx = x - item.position.x; val dy = y - item.position.y
            val distance = kotlin.math.sqrt(dx.toDouble() * dx + dy.toDouble() * dy).roundToInt()
            if (distance > toleranceMm) return@mapNotNull null
            val candidate = item.copy(position = item.position.copy(x = x, y = y))
            val scoped = project.furniture.filter { it.roomId == item.roomId }.map { if (it.id == itemId) candidate else it }
            if (GeometryEngine.validateFurniture(room, scoped).isNotEmpty()) null
            else FurnitureSnapCandidate(candidate, label, distance)
        }.minByOrNull { it.distanceMm }

        if (valid == null) return FurnitureSnapResult(project, false, null, 0)
        val updated = project.copy(furniture = project.furniture.map { if (it.id == itemId) valid.item else it })
        return FurnitureSnapResult(updated, true, valid.target, valid.distanceMm)
    }

    /** Exact clearances from a furniture footprint to the four room walls, in millimetres. */
    fun furnitureClearances(project: ProjectModel, itemId: String): Map<WallSide, Int> {
        val item = project.furniture.firstOrNull { it.id == itemId } ?: error("Объект мебели не найден")
        val room = project.rooms.firstOrNull { it.id == item.roomId } ?: error("Помещение не найдено")
        val swapped = item.rotationDeg % 180 != 0
        val halfW = (if (swapped) item.size.depth else item.size.width) / 2
        val halfD = (if (swapped) item.size.width else item.size.depth) / 2
        return mapOf(
            WallSide.WEST to item.position.x - halfW,
            WallSide.EAST to room.widthMm - item.position.x - halfW,
            WallSide.NORTH to item.position.y - halfD,
            WallSide.SOUTH to room.depthMm - item.position.y - halfD
        )
    }

    /** Creates a carcass from a parametric specification and inserts it as one editable assembly. */
    fun createParametricCarcass(project: ProjectModel, spec: CarcassSpec): ProjectModel {
        require(project.assemblies.none { it.id == spec.id }) { "Сборка с таким ID уже существует" }
        val result = ParametricAssemblyEngine.buildCarcass(spec)
        require(result.issues.isEmpty()) { result.issues.firstOrNull()?.message ?: "Не удалось построить корпус" }
        return putAssembly(project, requireNotNull(result.assembly))
    }

    /** Rebuilds generated carcass panels while retaining only joints whose part IDs still exist. */
    fun rebuildParametricCarcass(project: ProjectModel, spec: CarcassSpec): ProjectModel {
        val existing = project.assemblies.firstOrNull { it.id == spec.id } ?: error("Сборка не найдена")
        val result = ParametricAssemblyEngine.rebuild(existing, spec)
        require(result.issues.isEmpty()) { result.issues.firstOrNull()?.message ?: "Не удалось перестроить корпус" }
        val rebuilt = requireNotNull(result.assembly)
        val validIds = rebuilt.parts.map { it.id }.toSet()
        val preservedJoints = existing.joints.filter { it.firstPartId in validIds && it.secondPartId in validIds }
        return project.copy(assemblies = project.assemblies.map { if (it.id == spec.id) rebuilt.copy(joints = preservedJoints) else it })
    }

    fun materialCutSummary(project: ProjectModel): List<MaterialCutSummary> =
        project.assemblies.flatMap { ParametricAssemblyEngine.materialSummary(it) }
            .groupBy { it.materialId }.toSortedMap().map { (material, groups) ->
                MaterialCutSummary(material, groups.sumOf { it.boardCount }, groups.sumOf { it.totalAreaSquareMetres }, groups.flatMap { it.parts })
            }


    /** Plans a complete wall-mounted module run without mutating the project. */
    fun planWallFurniture(project: ProjectModel, request: RoomLayoutRequest): RoomLayoutResult =
        RoomLayoutPlanner.plan(project, request)

    /** Commits a previously validated wall layout atomically. */
    fun applyWallFurniture(project: ProjectModel, result: RoomLayoutResult, idPrefix: String = "layout"): ProjectModel =
        RoomLayoutPlanner.apply(project, result, idPrefix)


    /** Plans a coordinated composition across multiple walls, including corner and kitchen-zone checks. */
    fun planRoomComposition(project: ProjectModel, request: RoomCompositionRequest): RoomCompositionResult =
        RoomCompositionPlanner.plan(project, request)

    /** Applies all wall runs in one transaction; invalid compositions cannot partially modify a project. */
    fun applyRoomComposition(project: ProjectModel, result: RoomCompositionResult): ProjectModel =
        RoomCompositionPlanner.apply(project, result)

    /** Adds or replaces a furniture assembly while keeping IDs unique within the project. */
    fun putAssembly(project: ProjectModel, assembly: FurnitureAssembly): ProjectModel {
        require(assembly.id.isNotBlank()) { "У сборки должен быть идентификатор" }
        require(assembly.name.isNotBlank()) { "У сборки должно быть название" }
        require(assembly.parts.isNotEmpty()) { "Сборка должна содержать хотя бы одну деталь" }
        require(assembly.parts.map { it.id }.distinct().size == assembly.parts.size) { "ID деталей должны быть уникальными" }
        require(GeometryEngine.validateAssembly(assembly).isEmpty()) { "Сборка содержит некорректные детали" }
        return project.copy(assemblies = project.assemblies.filterNot { it.id == assembly.id } + assembly)
    }

    fun removeAssembly(project: ProjectModel, assemblyId: String): ProjectModel {
        require(project.assemblies.any { it.id == assemblyId }) { "Сборка не найдена" }
        return project.copy(assemblies = project.assemblies.filterNot { it.id == assemblyId })
    }

    /** Creates an independent copy of one selected part with a new stable ID and offset. */
    fun duplicateAssemblyPart(
        project: ProjectModel, assemblyId: String, partId: String, newPartId: String, offsetMm: Int = 100
    ): ProjectModel {
        require(newPartId.isNotBlank() && offsetMm > 0) { "Нужен новый ID и положительное смещение копии" }
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val source = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        require(assembly.parts.none { it.id == newPartId }) { "Деталь с таким ID уже существует" }
        val copy = source.copy(
            id = newPartId,
            name = "${source.name} · копия",
            localPosition = source.localPosition.copy(x = source.localPosition.x + offsetMm)
        )
        val updated = assembly.copy(parts = assembly.parts + copy)
        require(GeometryEngine.validateAssembly(updated).isEmpty()) { "Копию нельзя разместить с выбранным смещением" }
        return project.copy(assemblies = project.assemblies.map { if (it.id == assemblyId) updated else it })
    }

    /** Removes one independently editable part and any joints that referenced it. */
    fun removeAssemblyPart(project: ProjectModel, assemblyId: String, partId: String): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        require(assembly.parts.any { it.id == partId }) { "Деталь не найдена" }
        require(assembly.parts.size > 1) { "Нельзя удалить последнюю деталь сборки" }
        val updated = assembly.copy(
            parts = assembly.parts.filterNot { it.id == partId },
            joints = assembly.joints.filterNot { it.firstPartId == partId || it.secondPartId == partId }
        )
        return project.copy(assemblies = project.assemblies.map { if (it.id == assemblyId) updated else it })
    }

    fun replaceAssemblyPart(project: ProjectModel, assemblyId: String, part: FurniturePart): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        require(assembly.parts.any { it.id == part.id }) { "Деталь не найдена" }
        val updated = assembly.copy(parts = assembly.parts.map { if (it.id == part.id) part else it })
        require(GeometryEngine.validateAssembly(updated).isEmpty()) { "Изменение создаёт некорректную деталь" }
        return project.copy(assemblies = project.assemblies.map { if (it.id == assemblyId) updated else it })
    }

    /** Moves one independently selectable part in assembly-local millimetre coordinates. */
    fun moveAssemblyPart(
        project: ProjectModel,
        assemblyId: String,
        partId: String,
        xMm: Int,
        yMm: Int,
        zMm: Int,
        gridMm: Int = 1
    ): ProjectModel {
        require(gridMm > 0) { "Шаг сетки должен быть положительным" }
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        val moved = part.copy(localPosition = Vec3Mm(
            GeometryEngine.snap(xMm, gridMm), GeometryEngine.snap(yMm, gridMm), GeometryEngine.snap(zMm, gridMm)
        ))
        return replaceAssemblyPart(project, assemblyId, moved)
    }

    /** Resizes a selected board while preserving its local origin, orientation and material. */
    fun resizeAssemblyPart(
        project: ProjectModel,
        assemblyId: String,
        partId: String,
        widthMm: Int,
        depthMm: Int,
        heightMm: Int
    ): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        val resized = part.copy(size = Size3Mm(widthMm, depthMm, heightMm))
        return replaceAssemblyPart(project, assemblyId, resized)
    }

    /**
     * Returns temporary alignment guides for a dragged part. Guides are suggestions only;
     * they do not mutate the project and can be rendered as coloured lines on the scene.
     * The closest sibling edge/centre alignment is reported independently on X, Y and Z.
     */
    fun assemblyPartSnapGuides(
        project: ProjectModel, assemblyId: String, partId: String, toleranceMm: Int = 80
    ): List<AssemblySnapGuide> {
        require(toleranceMm >= 0) { "Допуск привязки не может быть отрицательным" }
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        val half = partHalfExtents(part)
        val candidates = mutableListOf<AssemblySnapGuide>()
        assembly.parts.filter { it.id != partId }.forEach { other ->
            val otherHalf = partHalfExtents(other)
            val axes = listOf(
                Triple("X" to part.localPosition.x, other.localPosition.x, Pair(half.first, otherHalf.first)),
                Triple("Y" to part.localPosition.y, other.localPosition.y, Pair(half.second, otherHalf.second)),
                Triple("Z" to part.localPosition.z, other.localPosition.z, Pair(half.third, otherHalf.third))
            )
            axes.forEach { (axisAndCurrent, target, extents) ->
                val (axis, current) = axisAndCurrent
                val (movingHalf, targetHalf) = extents
                val options = listOf(
                    (target - targetHalf - movingHalf) to "стык перед ${other.name}",
                    (target + targetHalf + movingHalf) to "стык за ${other.name}",
                    target to "центр ${other.name}"
                )
                options.forEach { (coordinate, label) ->
                    val delta = kotlin.math.abs(coordinate - current)
                    if (delta <= toleranceMm) candidates += AssemblySnapGuide(axis, coordinate, label, delta)
                }
            }
        }
        return candidates.groupBy { it.axis }.values.mapNotNull { it.minByOrNull(AssemblySnapGuide::distanceMm) }
            .sortedBy { it.axis }
    }

    /** Snaps a dragged assembly part to the nearest sibling edge/centre guide within tolerance. */
    fun snapAssemblyPartToNearby(
        project: ProjectModel, assemblyId: String, partId: String, toleranceMm: Int = 80, gridMm: Int = 1
    ): ProjectModel {
        require(toleranceMm >= 0 && gridMm > 0) { "Неверные параметры привязки" }
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        val guides = assemblyPartSnapGuides(project, assemblyId, partId, toleranceMm)
        if (guides.isEmpty()) return project
        val position = part.localPosition
        val x = guides.firstOrNull { it.axis == "X" }?.coordinateMm?.let { GeometryEngine.snap(it, gridMm) } ?: position.x
        val y = guides.firstOrNull { it.axis == "Y" }?.coordinateMm?.let { GeometryEngine.snap(it, gridMm) } ?: position.y
        val z = guides.firstOrNull { it.axis == "Z" }?.coordinateMm?.let { GeometryEngine.snap(it, gridMm) } ?: position.z
        val moved = part.copy(localPosition = Vec3Mm(x, y, z))
        return replaceAssemblyPart(project, assemblyId, moved)
    }

    private fun partHalfExtents(part: FurniturePart): Triple<Int, Int, Int> {
        val swapped = part.rotationDeg % 180 != 0
        val width = if (swapped) part.size.depth else part.size.width
        val depth = if (swapped) part.size.width else part.size.depth
        return Triple(width / 2, depth / 2, part.size.height / 2)
    }

    /** Rotates a selected board around its local vertical axis in 90-degree steps. */
    fun rotateAssemblyPart(project: ProjectModel, assemblyId: String, partId: String, degrees: Int): ProjectModel {
        require(degrees % 90 == 0) { "Деталь можно поворачивать шагом 90 градусов" }
        val part = project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
            ?: error("Сборка или деталь не найдена")
        return rotateAssemblyPartTo(project, assemblyId, partId, part.rotationDeg + degrees)
    }

    /** Applies an arbitrary normalized angle to a board; geometry is validated before commit. */
    fun rotateAssemblyPartTo(project: ProjectModel, assemblyId: String, partId: String, angleDeg: Int): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        return replaceAssemblyPart(project, assemblyId, part.copy(rotationDeg = FurnitureTransformMath.normalize360(angleDeg)))
    }

    /** Updates dimensions, local position and arbitrary angle in one atomic immutable edit. */
    fun updateAssemblyPart(
        project: ProjectModel, assemblyId: String, partId: String,
        widthMm: Int, depthMm: Int, heightMm: Int,
        xMm: Int, yMm: Int, zMm: Int, angleDeg: Int
    ): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val part = assembly.parts.firstOrNull { it.id == partId } ?: error("Деталь не найдена")
        val updated = part.copy(
            size = Size3Mm(widthMm, depthMm, heightMm),
            localPosition = Vec3Mm(xMm, yMm, zMm),
            rotationDeg = FurnitureTransformMath.normalize360(angleDeg)
        )
        val nextAssembly = assembly.copy(parts = assembly.parts.map { if (it.id == partId) updated else it })
        require(GeometryEngine.validateAssembly(nextAssembly).isEmpty()) { "Параметры детали некорректны" }
        return project.copy(assemblies = project.assemblies.map { if (it.id == assemblyId) nextAssembly else it })
    }

    /** Applies cabinet dimensions as one atomic edit and validates the rebuilt assembly. */
    fun resizeCabinetAssembly(
        project: ProjectModel,
        assemblyId: String,
        widthMm: Int,
        depthMm: Int,
        heightMm: Int,
        thicknessMm: Int
    ): ProjectModel {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        val resized = GeometryEngine.resizeCabinetAssembly(assembly, widthMm, depthMm, heightMm, thicknessMm)
        require(GeometryEngine.validateAssembly(resized).isEmpty()) { "После изменения размеров сборка некорректна" }
        return project.copy(assemblies = project.assemblies.map { if (it.id == assemblyId) resized else it })
    }

    fun putEngineeringElement(project: ProjectModel, roomId: String, element: EngineeringElement): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val updated = GeometryEngine.addEngineeringElement(room, element)
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updated else it })
    }

    fun updateEngineeringElement(project: ProjectModel, roomId: String, element: EngineeringElement): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val updated = GeometryEngine.updateEngineeringElement(room, element)
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updated else it })
    }

    fun removeEngineeringElement(project: ProjectModel, roomId: String, elementId: String): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val updated = GeometryEngine.removeEngineeringElement(room, elementId)
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updated else it })
    }

    /** Moves a door/window along its wall while preserving its dimensions and photo provenance. */
    fun moveOpening(project: ProjectModel, roomId: String, openingId: String, offsetMm: Int): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val opening = room.openings.firstOrNull { it.id == openingId } ?: error("Проём не найден")
        val moved = opening.copy(offsetMm = offsetMm.coerceAtLeast(0))
        return updateOpening(project, roomId, moved)
    }

    /** Moves an engineering marker in room coordinates; wall-mounted markers stay on their wall. */
    fun moveEngineeringElement(
        project: ProjectModel, roomId: String, elementId: String,
        xMm: Int, yMm: Int, zMm: Int, gridMm: Int = 10
    ): ProjectModel {
        require(gridMm >= 0) { "Шаг сетки не может быть отрицательным" }
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val element = room.engineering.firstOrNull { it.id == elementId } ?: error("Инженерный элемент не найден")
        fun snap(value: Int): Int = if (gridMm == 0) value else GeometryEngine.snap(value, gridMm)
        val x = snap(xMm).coerceIn(0, room.widthMm)
        val y = snap(yMm).coerceIn(0, room.depthMm)
        val z = snap(zMm).coerceIn(0, room.heightMm)
        val position = when (element.wallSide) {
            WallSide.NORTH -> Vec3Mm(x, 0, z)
            WallSide.SOUTH -> Vec3Mm(x, room.depthMm, z)
            WallSide.WEST -> Vec3Mm(0, y, z)
            WallSide.EAST -> Vec3Mm(room.widthMm, y, z)
            null -> Vec3Mm(x, y, z)
        }
        return updateEngineeringElement(project, roomId, element.copy(position = position))
    }

    /** Moves a wall-mounted marker along its wall and vertically in the elevation view. */
    fun moveEngineeringOnWall(
        project: ProjectModel, roomId: String, elementId: String,
        alongMm: Int, elevationMm: Int, gridMm: Int = 10
    ): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val element = room.engineering.firstOrNull { it.id == elementId } ?: error("Инженерный элемент не найден")
        val side = element.wallSide ?: error("Элемент не привязан к стене")
        val along = (if (gridMm > 0) GeometryEngine.snap(alongMm, gridMm) else alongMm)
            .coerceIn(0, room.wallLength(side))
        val height = (if (gridMm > 0) GeometryEngine.snap(elevationMm, gridMm) else elevationMm)
            .coerceIn(0, room.heightMm)
        val position = when (side) {
            WallSide.NORTH -> Vec3Mm(along, 0, height)
            WallSide.SOUTH -> Vec3Mm(along, room.depthMm, height)
            WallSide.WEST -> Vec3Mm(0, along, height)
            WallSide.EAST -> Vec3Mm(room.widthMm, along, height)
        }
        return updateEngineeringElement(project, roomId, element.copy(position = position))
    }

    fun updateOpening(project: ProjectModel, roomId: String, opening: WallOpening): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        require(room.openings.any { it.id == opening.id }) { "Проём не найден" }
        val updated = room.copy(openings = room.openings.map { if (it.id == opening.id) opening else it })
        require(GeometryEngine.validateRoom(updated).none { it.objectId == opening.id }) { "Проём выходит за границы стены или пересекается с другим проёмом" }
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updated else it })
    }

    fun removeOpening(project: ProjectModel, roomId: String, openingId: String): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        require(room.openings.any { it.id == openingId }) { "Проём не найден" }
        val updated = room.copy(openings = room.openings.filterNot { it.id == openingId })
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updated else it })
    }

    /** Adds a phone-selected photo reference to a room without copying image bytes into project JSON. */
    fun addRoomPhoto(project: ProjectModel, roomId: String, photo: RoomPhotoReference): ProjectModel {
        require(photo.id.isNotBlank() && photo.uri.isNotBlank()) { "У фотографии должны быть ID и URI" }
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        require(room.photos.none { it.id == photo.id }) { "Фотография с таким ID уже добавлена" }
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) room.copy(photos = room.photos + photo) else it })
    }

    fun removeRoomPhoto(project: ProjectModel, roomId: String, photoId: String): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        require(room.photos.any { it.id == photoId }) { "Фотография не найдена" }
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) room.copy(photos = room.photos.filterNot { photo -> photo.id == photoId }) else it })
    }

    fun setPhotoWall(project: ProjectModel, roomId: String, photoId: String, wallSide: WallSide?): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        require(room.photos.any { it.id == photoId }) { "Фотография не найдена" }
        val photos = room.photos.map { if (it.id == photoId) it.copy(wallSide = wallSide) else it }
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) room.copy(photos = photos) else it })
    }

    fun calibrateRoomPhoto(
        project: ProjectModel, roomId: String, photoId: String, knownLengthMm: Int, measuredPixels: Int,
        wallOriginXPx: Int? = null, floorLineYPx: Int? = null
    ): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val calibrated = GeometryEngine.calibratePhoto(photo, knownLengthMm, measuredPixels)
            .copy(wallOriginXPx = wallOriginXPx, floorLineYPx = floorLineYPx)
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) room.copy(photos = room.photos.map { p -> if (p.id == photoId) calibrated else p }) else it })
    }

    fun addPhotoAnnotation(project: ProjectModel, roomId: String, photoId: String, annotation: PhotoAnnotation): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val updatedPhoto = GeometryEngine.addPhotoAnnotation(photo, annotation)
        val updatedRoom = room.copy(photos = room.photos.map { if (it.id == photoId) updatedPhoto else it })
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updatedRoom else it })
    }

    fun removePhotoAnnotation(project: ProjectModel, roomId: String, photoId: String, annotationId: String): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val updatedPhoto = GeometryEngine.removePhotoAnnotation(photo, annotationId)
        val updatedRoom = room.copy(photos = room.photos.map { if (it.id == photoId) updatedPhoto else it })
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updatedRoom else it })
    }

    fun updatePhotoAnnotation(project: ProjectModel, roomId: String, photoId: String, annotation: PhotoAnnotation): ProjectModel {
        val room = project.rooms.firstOrNull { it.id == roomId } ?: error("Помещение не найдено")
        val photo = room.photos.firstOrNull { it.id == photoId } ?: error("Фотография не найдена")
        val updatedPhoto = GeometryEngine.updatePhotoAnnotation(photo, annotation)
        val updatedRoom = room.copy(photos = room.photos.map { if (it.id == photoId) updatedPhoto else it })
        return project.copy(rooms = project.rooms.map { if (it.id == roomId) updatedRoom else it })
    }

    /** Updates only photo-derived geometry after a user edits a source annotation. */
    fun synchronizePhotoAnnotations(project: ProjectModel, roomId: String, photoId: String): PhotoTransferSyncResult =
        PhotoWallTransfer.synchronize(project, roomId, photoId)

    /** Transfers calibrated photo marks into real room openings and engineering points. */
    fun transferPhotoAnnotations(project: ProjectModel, roomId: String, photoId: String): PhotoTransferResult =
        PhotoWallTransfer.transfer(project, roomId, photoId)

    /** Non-blocking construction diagnostics for one assembly; warnings do not mutate the project. */
    fun diagnosticsForAssembly(project: ProjectModel, assemblyId: String): List<AssemblyDiagnostic> {
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        return AssemblyDiagnosticsEngine.inspect(assembly)
    }

    /** Aggregates construction diagnostics across all furniture assemblies in the project. */
    fun diagnosticsForProject(project: ProjectModel): Map<String, List<AssemblyDiagnostic>> =
        project.assemblies.associate { it.id to AssemblyDiagnosticsEngine.inspect(it) }

    fun cutListForProject(project: ProjectModel): List<BoardCut> =
        project.assemblies.flatMap { GeometryEngine.cutList(it) }

    /** Applies a visual finish to a furniture object. Material IDs remain backward compatible. */
    fun applyFurnitureMaterial(project: ProjectModel, itemId: String, materialId: String): ProjectModel {
        val material = InteriorMaterialLibrary.find(materialId) ?: error("Материал не найден")
        require(SurfaceUse.FURNITURE in material.uses) { "Этот материал нельзя применить к мебели" }
        require(project.furniture.any { it.id == itemId }) { "Объект мебели не найден" }
        return project.copy(furniture = project.furniture.map { item ->
            if (item.id == itemId) item.copy(materialId = material.id, colorArgb = material.argb) else item
        })
    }

    /** Applies a visual finish to one editable component without changing the rest of the assembly. */
    fun applyAssemblyPartMaterial(project: ProjectModel, assemblyId: String, partId: String, materialId: String): ProjectModel {
        val material = InteriorMaterialLibrary.find(materialId) ?: error("Материал не найден")
        require(SurfaceUse.FURNITURE in material.uses || SurfaceUse.COUNTERTOP in material.uses || SurfaceUse.FIXTURE in material.uses) {
            "Этот материал нельзя применить к мебельной детали"
        }
        val assembly = project.assemblies.firstOrNull { it.id == assemblyId } ?: error("Сборка не найдена")
        require(assembly.parts.any { it.id == partId }) { "Деталь не найдена" }
        return project.copy(assemblies = project.assemblies.map { a ->
            if (a.id != assemblyId) a else a.copy(parts = a.parts.map { part ->
                if (part.id == partId) part.copy(materialId = material.id) else part
            })
        })
    }

    /** Updates one room finish independently; furniture and room geometry are preserved. */
    fun applyRoomSurface(project: ProjectModel, roomId: String, use: SurfaceUse, materialId: String): ProjectModel {
        val material = InteriorMaterialLibrary.find(materialId) ?: error("Материал не найден")
        require(use == SurfaceUse.WALL || use == SurfaceUse.FLOOR || use == SurfaceUse.CEILING) { "Неизвестная поверхность помещения" }
        require(use in material.uses) { "Материал несовместим с выбранной поверхностью" }
        require(project.rooms.any { it.id == roomId }) { "Помещение не найдено" }
        return project.copy(rooms = project.rooms.map { room ->
            if (room.id != roomId) room else when (use) {
                SurfaceUse.WALL -> room.copy(wallMaterialId = material.id, wallColorArgb = material.argb)
                SurfaceUse.FLOOR -> room.copy(floorMaterialId = material.id, floorColorArgb = material.argb)
                SurfaceUse.CEILING -> room.copy(ceilingMaterialId = material.id, ceilingColorArgb = material.argb)
                else -> room
            }
        })
    }

    fun applyWallFinish(project: ProjectModel, roomId: String, side: WallSide, materialId: String): ProjectModel {
        require(project.rooms.any { it.id == roomId }) { "Помещение не найдено" }
        val updated = project.rooms.map { room ->
            if (room.id == roomId) RoomSurfaceAppearance.applyWall(room, side, materialId) else room
        }
        val result = project.copy(rooms = updated)
        ProjectValidator.requireValid(result)
        return result
    }

    fun inheritWallFinish(project: ProjectModel, roomId: String, side: WallSide): ProjectModel {
        require(project.rooms.any { it.id == roomId }) { "Помещение не найдено" }
        val result = project.copy(rooms = project.rooms.map { room ->
            if (room.id == roomId) RoomSurfaceAppearance.inheritWallFinish(room, side) else room
        })
        ProjectValidator.requireValid(result)
        return result
    }

    fun furnitureInActiveRoom(project: ProjectModel): List<FurnitureItem> =
        project.furniture.filter { it.roomId == project.activeRoomId }
}
