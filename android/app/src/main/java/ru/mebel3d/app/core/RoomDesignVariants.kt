package ru.mebel3d.app.core

/** A named, immutable design snapshot. It deliberately contains the complete project state,
 * not a delta, so switching concepts cannot leave stale furniture or room details behind. */
data class RoomDesignVariant(
    val id: String,
    val title: String,
    val note: String,
    val createdAtEpochMs: Long,
    val snapshot: ProjectModel
)

enum class VariantChangeKind { ROOM_ADDED, ROOM_REMOVED, ROOM_RENAMED, ROOM_RESIZED, ROOM_FINISH_CHANGED,
    OPENING_ADDED, OPENING_REMOVED, OPENING_CHANGED, ENGINEERING_ADDED, ENGINEERING_REMOVED,
    ENGINEERING_CHANGED, PHOTO_ADDED, PHOTO_REMOVED, PHOTO_CHANGED,
    FURNITURE_ADDED, FURNITURE_REMOVED, FURNITURE_CHANGED,
    ASSEMBLY_ADDED, ASSEMBLY_REMOVED, ASSEMBLY_CHANGED, ACTIVE_ROOM_CHANGED }

data class VariantChange(
    val kind: VariantChangeKind,
    val elementId: String,
    val title: String,
    val before: String? = null,
    val after: String? = null
)

data class VariantComparison(val firstTitle: String, val secondTitle: String, val changes: List<VariantChange>) {
    val isIdentical: Boolean get() = changes.isEmpty()
    val addedCount: Int get() = changes.count { it.kind in setOf(VariantChangeKind.ROOM_ADDED, VariantChangeKind.FURNITURE_ADDED, VariantChangeKind.ASSEMBLY_ADDED) }
    val removedCount: Int get() = changes.count { it.kind in setOf(VariantChangeKind.ROOM_REMOVED, VariantChangeKind.FURNITURE_REMOVED, VariantChangeKind.ASSEMBLY_REMOVED) }
}

/** Pure Kotlin variant workflow. Persistence is intentionally delegated to the project storage layer. */
object RoomDesignVariantManager {
    fun capture(project: ProjectModel, id: String, title: String, note: String = "", nowEpochMs: Long = System.currentTimeMillis()): RoomDesignVariant {
        require(id.isNotBlank()) { "У варианта должен быть ID" }
        require(title.isNotBlank()) { "Укажите название варианта" }
        ProjectValidator.requireValid(project)
        return RoomDesignVariant(id, title.trim(), note.trim(), nowEpochMs, deepSnapshot(project))
    }

    fun clone(source: RoomDesignVariant, newId: String, newTitle: String, nowEpochMs: Long = System.currentTimeMillis()): RoomDesignVariant {
        require(newId.isNotBlank() && newId != source.id) { "Для копии нужен новый ID" }
        require(newTitle.isNotBlank()) { "Укажите название копии" }
        return source.copy(id = newId, title = newTitle.trim(), createdAtEpochMs = nowEpochMs,
            snapshot = deepSnapshot(source.snapshot))
    }

    fun apply(current: ProjectModel, variant: RoomDesignVariant): ProjectModel {
        val restored = variant.snapshot.copy(id = current.id, name = current.name)
        return ProjectValidator.requireValid(restored)
    }

    fun add(variants: List<RoomDesignVariant>, variant: RoomDesignVariant): List<RoomDesignVariant> {
        require(variants.none { it.id == variant.id }) { "Вариант с таким ID уже существует" }
        return variants + variant
    }

    fun remove(variants: List<RoomDesignVariant>, id: String): List<RoomDesignVariant> {
        require(variants.any { it.id == id }) { "Вариант не найден" }
        return variants.filterNot { it.id == id }
    }

    fun compare(first: RoomDesignVariant, second: RoomDesignVariant): VariantComparison {
        val a = first.snapshot; val b = second.snapshot
        val changes = mutableListOf<VariantChange>()
        val ar = a.rooms.associateBy { it.id }; val br = b.rooms.associateBy { it.id }
        (ar.keys - br.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.ROOM_REMOVED, it, ar.getValue(it).name) }
        (br.keys - ar.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.ROOM_ADDED, it, br.getValue(it).name) }
        (ar.keys intersect br.keys).sorted().forEach { id ->
            val x = ar.getValue(id); val y = br.getValue(id)
            if (x.name != y.name) changes += VariantChange(VariantChangeKind.ROOM_RENAMED, id, "Название помещения", x.name, y.name)
            if (listOf(x.widthMm, x.depthMm, x.heightMm, x.wallThicknessMm) != listOf(y.widthMm, y.depthMm, y.heightMm, y.wallThicknessMm))
                changes += VariantChange(VariantChangeKind.ROOM_RESIZED, id, x.name,
                    "${x.widthMm}×${x.depthMm}×${x.heightMm} мм; стенка ${x.wallThicknessMm} мм", "${y.widthMm}×${y.depthMm}×${y.heightMm} мм; стенка ${y.wallThicknessMm} мм")
            if (listOf(x.wallColorArgb, x.floorColorArgb, x.ceilingColorArgb, x.wallMaterialId, x.floorMaterialId, x.ceilingMaterialId, x.northWallMaterialId, x.eastWallMaterialId, x.southWallMaterialId, x.westWallMaterialId, x.lightingPreset, x.lightIntensityPercent, x.lightTemperatureKelvin, x.shadowStrengthPercent) != listOf(y.wallColorArgb, y.floorColorArgb, y.ceilingColorArgb, y.wallMaterialId, y.floorMaterialId, y.ceilingMaterialId, y.northWallMaterialId, y.eastWallMaterialId, y.southWallMaterialId, y.westWallMaterialId, y.lightingPreset, y.lightIntensityPercent, y.lightTemperatureKelvin, y.shadowStrengthPercent))
                changes += VariantChange(VariantChangeKind.ROOM_FINISH_CHANGED, id, "Отделка: ${x.name}",
                    "стены ${x.wallMaterialId ?: x.wallColorArgb}; пол ${x.floorMaterialId ?: x.floorColorArgb}; потолок ${x.ceilingMaterialId ?: x.ceilingColorArgb}; стены N/E/S/W ${x.northWallMaterialId}/${x.eastWallMaterialId}/${x.southWallMaterialId}/${x.westWallMaterialId}; ${x.lightingPreset}; свет ${x.lightIntensityPercent}%/${x.lightTemperatureKelvin}K; тени ${x.shadowStrengthPercent}%", "стены ${y.wallMaterialId ?: y.wallColorArgb}; пол ${y.floorMaterialId ?: y.floorColorArgb}; потолок ${y.ceilingMaterialId ?: y.ceilingColorArgb}; стены N/E/S/W ${y.northWallMaterialId}/${y.eastWallMaterialId}/${y.southWallMaterialId}/${y.westWallMaterialId}; ${y.lightingPreset}; свет ${y.lightIntensityPercent}%/${y.lightTemperatureKelvin}K; тени ${y.shadowStrengthPercent}%")
            fun <T> compareElements(before: List<T>, after: List<T>, key: (T) -> String, added: VariantChangeKind, removed: VariantChangeKind, changed: VariantChangeKind, label: String) {
                val old = before.associateBy(key); val new = after.associateBy(key)
                (old.keys - new.keys).sorted().forEach { eid -> changes += VariantChange(removed, eid, "$label удалён", old[eid].toString(), null) }
                (new.keys - old.keys).sorted().forEach { eid -> changes += VariantChange(added, eid, "$label добавлен", null, new[eid].toString()) }
                (old.keys intersect new.keys).sorted().filter { old[it] != new[it] }.forEach { eid ->
                    changes += VariantChange(changed, eid, "$label изменён", old[eid].toString(), new[eid].toString())
                }
            }
            compareElements(x.openings, y.openings, { it.id }, VariantChangeKind.OPENING_ADDED, VariantChangeKind.OPENING_REMOVED, VariantChangeKind.OPENING_CHANGED, "Проём")
            compareElements(x.engineering, y.engineering, { it.id }, VariantChangeKind.ENGINEERING_ADDED, VariantChangeKind.ENGINEERING_REMOVED, VariantChangeKind.ENGINEERING_CHANGED, "Коммуникация")
            compareElements(x.photos, y.photos, { it.id }, VariantChangeKind.PHOTO_ADDED, VariantChangeKind.PHOTO_REMOVED, VariantChangeKind.PHOTO_CHANGED, "Фото")
        }
        val af = a.furniture.associateBy { it.id }; val bf = b.furniture.associateBy { it.id }
        (af.keys - bf.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.FURNITURE_REMOVED, it, af.getValue(it).name) }
        (bf.keys - af.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.FURNITURE_ADDED, it, bf.getValue(it).name) }
        (af.keys intersect bf.keys).sorted().forEach { id ->
            val x = af.getValue(id); val y = bf.getValue(id)
            if (x != y) changes += VariantChange(VariantChangeKind.FURNITURE_CHANGED, id, x.name,
                "${x.size.width}×${x.size.depth}×${x.size.height} мм; (${x.position.x}, ${x.position.y}, ${x.position.z}); ${x.rotationDeg}°; ${x.materialId}; комната ${x.roomId}",
                "${y.size.width}×${y.size.depth}×${y.size.height} мм; (${y.position.x}, ${y.position.y}, ${y.position.z}); ${y.rotationDeg}°; ${y.materialId}; комната ${y.roomId}")
        }
        val aa = a.assemblies.associateBy { it.id }; val ba = b.assemblies.associateBy { it.id }
        (aa.keys - ba.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.ASSEMBLY_REMOVED, it, aa.getValue(it).name) }
        (ba.keys - aa.keys).sorted().forEach { changes += VariantChange(VariantChangeKind.ASSEMBLY_ADDED, it, ba.getValue(it).name) }
        (aa.keys intersect ba.keys).sorted().filter { aa[it] != ba[it] }.forEach { id ->
            val old = aa.getValue(id); val new = ba.getValue(id)
            changes += VariantChange(VariantChangeKind.ASSEMBLY_CHANGED, id, old.name,
                "${old.parts.size} деталей; ${old.joints.size} соединений; позиция ${old.position}",
                "${new.parts.size} деталей; ${new.joints.size} соединений; позиция ${new.position}")
        }
        if (a.activeRoomId != b.activeRoomId) changes += VariantChange(VariantChangeKind.ACTIVE_ROOM_CHANGED, "active-room", "Активное помещение", a.activeRoomId, b.activeRoomId)
        return VariantComparison(first.title, second.title, changes)
    }

    private fun deepSnapshot(project: ProjectModel): ProjectModel = project.copy(
        rooms = project.rooms.map { room -> room.copy(
            openings = room.openings.map { it.copy() },
            engineering = room.engineering.map { it.copy() },
            photos = room.photos.map { photo -> photo.copy(annotations = photo.annotations.map { it.copy() }) }
        ) },
        furniture = project.furniture.map { it.copy(size = it.size.copy(), position = it.position.copy()) },
        assemblies = project.assemblies.map { assembly -> assembly.copy(position = assembly.position.copy(),
            parts = assembly.parts.map { it.copy(size = it.size.copy(), localPosition = it.localPosition.copy()) },
            joints = assembly.joints.map { it.copy() }) }
    )
}
