import ru.mebel3d.app.core.*

fun main() {
    val room = RoomModel(id = "r", name = "Кухня", widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val base = ProjectModel("p", "Проект", listOf(room), emptyList(), emptyList(), "r")
    val first = RoomDesignVariantManager.capture(base, "v1", "Исходный", nowEpochMs = 1)
    val changedRoom = room.copy(name = "Кухня-гостиная", widthMm = 5200, wallColorArgb = 0xFFEEEEEEL,
        openings = listOf(WallOpening("door-1", WallSide.NORTH, 300, 900, 0, 2100, OpeningKind.DOOR)),
        engineering = listOf(EngineeringElement("water-1", "Вода", EngineeringKind.WATER, Vec3Mm(400, 100, 500))),
        photos = listOf(RoomPhotoReference("photo-1", "content://room/photo")))
    val sofa = FurnitureItem("sofa", "Диван", Size3Mm(1800, 900, 800), Vec3Mm(2000, 1500, 0), materialId = "fabric", roomId = "r")
    val assembly = FurnitureAssembly("cab", "Шкаф", Vec3Mm(4000, 1000, 0), listOf(FurniturePart("side", "Боковина", Size3Mm(600, 18, 2100), Vec3Mm(0,0,0))))
    val secondProject = base.copy(rooms = listOf(changedRoom), furniture = listOf(sofa), assemblies = listOf(assembly))
    val second = RoomDesignVariantManager.capture(secondProject, "v2", "Новый", nowEpochMs = 2)
    val diff = RoomDesignVariantManager.compare(first, second)
    check(diff.changes.any { it.kind == VariantChangeKind.ROOM_RENAMED })
    check(diff.changes.any { it.kind == VariantChangeKind.ROOM_RESIZED })
    check(diff.changes.any { it.kind == VariantChangeKind.ROOM_FINISH_CHANGED })
    check(diff.changes.any { it.kind == VariantChangeKind.OPENING_ADDED })
    check(diff.changes.any { it.kind == VariantChangeKind.ENGINEERING_ADDED })
    check(diff.changes.any { it.kind == VariantChangeKind.PHOTO_ADDED })
    check(diff.changes.any { it.kind == VariantChangeKind.FURNITURE_ADDED })
    check(diff.changes.any { it.kind == VariantChangeKind.ASSEMBLY_ADDED })
    check(diff.addedCount == 2)
    check(!diff.isIdentical)
    val applied = RoomDesignVariantManager.apply(base, second)
    check(applied.id == base.id && applied.name == base.name)
    check(applied.furniture.single().id == "sofa")
    check(base.furniture.isEmpty())
    val clone = RoomDesignVariantManager.clone(second, "v3", "Копия", 3)
    check(RoomDesignVariantManager.compare(second, clone).isIdentical)
    println("RoomDesignVariantComparisonSmoke: 14 checks passed")
}
