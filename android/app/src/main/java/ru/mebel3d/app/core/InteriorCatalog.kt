package ru.mebel3d.app.core

/** Visual catalog definitions used by the room editor. All dimensions are millimetres. */
data class InteriorCatalogEntry(
    val id: String,
    val name: String,
    val category: FurnitureRoomCategory,
    val size: Size3Mm,
    val glyph: String,
    val defaultMaterialId: String = "wood-oak",
    val tags: Set<String> = emptySet()
)

data class InteriorStylePreset(
    val id: String,
    val name: String,
    val materialId: String,
    val colorArgb: Long,
    val description: String
)

data class CatalogPlacementResult(
    val item: FurnitureItem?,
    val issues: List<String>,
    val testedPositions: Int
)

object InteriorCatalogEngine {
    val entries: List<InteriorCatalogEntry> = listOf(
        e("sofa-2", "Диван", FurnitureRoomCategory.LIVING_ROOM, 2200, 900, 850, "🛋", "fabric-linen", "мягкая мебель", "отдых"),
        e("armchair", "Кресло", FurnitureRoomCategory.LIVING_ROOM, 850, 850, 900, "🪑", "fabric-linen", "мягкая мебель"),
        e("coffee-table", "Журнальный стол", FurnitureRoomCategory.LIVING_ROOM, 1000, 600, 420, "▱"),
        e("dining-table", "Обеденный стол", FurnitureRoomCategory.LIVING_ROOM, 1400, 800, 750, "▱"),
        e("chair", "Стул", FurnitureRoomCategory.LIVING_ROOM, 450, 500, 850, "🪑"),
        e("tv-stand", "ТВ-тумба", FurnitureRoomCategory.LIVING_ROOM, 1800, 450, 500, "▤"),
        e("bookcase", "Стеллаж", FurnitureRoomCategory.LIVING_ROOM, 900, 350, 2100, "▥"),
        e("display-cabinet", "Шкаф-витрина", FurnitureRoomCategory.LIVING_ROOM, 900, 450, 2000, "▥", "glass-clear"),
        e("bed", "Кровать", FurnitureRoomCategory.BEDROOM, 1600, 2000, 550, "▰", "fabric-linen"),
        e("wardrobe", "Шкаф", FurnitureRoomCategory.BEDROOM, 1800, 600, 2300, "▥"),
        e("dresser", "Комод", FurnitureRoomCategory.BEDROOM, 1000, 450, 850, "▤"),
        e("bedside", "Тумба прикроватная", FurnitureRoomCategory.BEDROOM, 500, 400, 500, "▦"),
        e("vanity-desk", "Туалетный столик", FurnitureRoomCategory.BEDROOM, 1000, 450, 780, "▱"),
        e("desk", "Письменный стол", FurnitureRoomCategory.BEDROOM, 1400, 700, 750, "▱"),
        e("kitchen-base", "Нижний кухонный модуль", FurnitureRoomCategory.KITCHEN, 600, 560, 900, "▤"),
        e("kitchen-wall", "Верхний кухонный модуль", FurnitureRoomCategory.KITCHEN, 600, 320, 720, "▤"),
        e("kitchen-corner", "Угловой модуль", FurnitureRoomCategory.KITCHEN, 900, 900, 900, "◩"),
        e("kitchen-tall", "Пенал", FurnitureRoomCategory.KITCHEN, 600, 600, 2100, "▥"),
        e("fridge", "Холодильник", FurnitureRoomCategory.KITCHEN, 700, 700, 2000, "▯", "metal-chrome"),
        e("oven", "Духовой шкаф", FurnitureRoomCategory.KITCHEN, 600, 560, 600, "▣", "metal-chrome"),
        e("dishwasher", "Посудомоечная машина", FurnitureRoomCategory.KITCHEN, 600, 560, 820, "▣", "metal-chrome"),
        e("hob", "Варочная поверхность", FurnitureRoomCategory.KITCHEN, 600, 520, 55, "▦", "stone-black"),
        e("hood", "Вытяжка", FurnitureRoomCategory.KITCHEN, 600, 500, 450, "▤", "metal-chrome"),
        e("microwave", "Микроволновая печь", FurnitureRoomCategory.KITCHEN, 500, 400, 300, "▣", "metal-chrome"),
        e("vanity", "Тумба под раковину", FurnitureRoomCategory.BATHROOM, 800, 450, 600, "▤"),
        e("bath", "Ванна", FurnitureRoomCategory.BATHROOM, 1700, 750, 600, "▱", "ceramic-white"),
        e("shower", "Душевая", FurnitureRoomCategory.BATHROOM, 900, 900, 2100, "▦", "glass-clear"),
        e("basin", "Раковина", FurnitureRoomCategory.BATHROOM, 600, 500, 180, "♧", "ceramic-white"),
        e("bath-tall", "Пенал для ванной", FurnitureRoomCategory.BATHROOM, 400, 350, 1900, "▥"),
        e("washer", "Стиральная машина", FurnitureRoomCategory.BATHROOM, 600, 600, 850, "▣", "metal-chrome"),
        e("toilet", "Унитаз", FurnitureRoomCategory.BATHROOM, 380, 700, 800, "◉", "ceramic-white"),
        e("mirror", "Зеркало", FurnitureRoomCategory.BATHROOM, 600, 40, 900, "▯", "glass-clear"),
        e("bath-shelf", "Полка настенная", FurnitureRoomCategory.BATHROOM, 900, 250, 40, "▬"),
        e("rug", "Ковёр", FurnitureRoomCategory.LIVING_ROOM, 1800, 1200, 15, "▧", "fabric-linen"),
        e("floor-lamp", "Торшер", FurnitureRoomCategory.LIVING_ROOM, 350, 350, 1650, "♧", "metal-chrome"),
        e("chandelier", "Люстра", FurnitureRoomCategory.LIVING_ROOM, 500, 500, 250, "✧", "metal-chrome"),
        e("full-mirror", "Зеркало напольное", FurnitureRoomCategory.BEDROOM, 600, 40, 1600, "▯", "glass-clear")
    )

    val styles = listOf(
        InteriorStylePreset("scandi", "Скандинавский", "wood-oak", 0xFFE2D2B9, "Светлое дерево и спокойные нейтральные оттенки"),
        InteriorStylePreset("walnut", "Тёплый орех", "wood-walnut", 0xFF79543A, "Тёплая древесная отделка"),
        InteriorStylePreset("minimal", "Белый минимализм", "paint-matte", 0xFFF2F1ED, "Матовые светлые поверхности"),
        InteriorStylePreset("graphite", "Современный графит", "paint-graphite", 0xFF454A50, "Контрастная тёмная отделка"),
        InteriorStylePreset("natural", "Натуральный камень", "stone-marble", 0xFFD4D0C8, "Светлый камень с мягким рисунком"),
        InteriorStylePreset("soft", "Мягкий текстиль", "fabric-linen", 0xFFB8A99A, "Нейтральная тканевая обивка")
    )

    fun search(query: String = "", category: FurnitureRoomCategory? = null): List<InteriorCatalogEntry> {
        val q = query.trim().lowercase()
        return entries.filter { (category == null || it.category == category) &&
            (q.isBlank() || it.name.lowercase().contains(q) || it.tags.any { tag -> tag.lowercase().contains(q) }) }
    }

    /** Finds a collision-free floor position near the room centre, then searches outward deterministically. */
    fun place(
        project: ProjectModel,
        entryId: String,
        itemId: String,
        roomId: String = project.activeRoomId,
        preferred: Vec3Mm? = null,
        gridMm: Int = 100
    ): CatalogPlacementResult {
        require(itemId.isNotBlank() && gridMm > 0)
        val entry = entries.firstOrNull { it.id == entryId }
            ?: return CatalogPlacementResult(null, listOf("Позиция каталога не найдена"), 0)
        val room = project.rooms.firstOrNull { it.id == roomId }
            ?: return CatalogPlacementResult(null, listOf("Помещение не найдено"), 0)
        if (project.furniture.any { it.id == itemId }) return CatalogPlacementResult(null, listOf("ID объекта уже используется"), 0)
        val base = preferred ?: Vec3Mm(room.widthMm / 2, room.depthMm / 2, 0)
        val maxRadius = maxOf(room.widthMm, room.depthMm) / gridMm + 1
        var tested = 0
        for (radius in 0..maxRadius) {
            val candidates = if (radius == 0) listOf(0 to 0) else buildList {
                for (dx in -radius..radius) { add(dx to -radius); add(dx to radius) }
                for (dy in -radius + 1 until radius) { add(-radius to dy); add(radius to dy) }
            }
            for ((dx, dy) in candidates) {
                val x = base.x + dx * gridMm
                val y = base.y + dy * gridMm
                val candidate = FurnitureItem(itemId, entry.name, entry.size, Vec3Mm(x, y, 0),
                    materialId = entry.defaultMaterialId, roomId = roomId)
                tested++
                val scoped = project.furniture.filter { it.roomId == roomId } + candidate
                if (GeometryEngine.validateFurniture(room, scoped).isEmpty())
                    return CatalogPlacementResult(candidate, emptyList(), tested)
            }
        }
        return CatalogPlacementResult(null, listOf("Не найдено свободное место для «${entry.name}». Уменьшите размер или освободите пространство."), tested)
    }

    fun applyStyle(item: FurnitureItem, styleId: String): FurnitureItem {
        val style = styles.firstOrNull { it.id == styleId } ?: error("Стиль отделки не найден")
        return item.copy(materialId = style.materialId, colorArgb = style.colorArgb)
    }

    private fun e(id: String, name: String, category: FurnitureRoomCategory, w: Int, d: Int, h: Int,
                  glyph: String, material: String = "wood-oak", vararg tags: String) =
        InteriorCatalogEntry(id, name, category, Size3Mm(w, d, h), glyph, material, tags.toSet())
}
