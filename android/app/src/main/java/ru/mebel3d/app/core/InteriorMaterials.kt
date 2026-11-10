package ru.mebel3d.app.core

/** Visual surface library. Values describe appearance, not certified physical product data. */
enum class SurfaceFamily { WOOD, STONE, CERAMIC, METAL, GLASS, TEXTILE, PAINT, PLASTIC }
enum class SurfaceUse { FURNITURE, WALL, FLOOR, CEILING, COUNTERTOP, UPHOLSTERY, FIXTURE }
enum class SurfacePattern { SOLID, WOOD_GRAIN, STONE_GRAIN, TILE, BRUSHED, TRANSPARENT, FABRIC }

data class InteriorMaterial(
    val id: String,
    val name: String,
    val family: SurfaceFamily,
    val argb: Long,
    val roughness: Float,
    val pattern: SurfacePattern,
    val uses: Set<SurfaceUse>,
    val description: String = ""
) {
    init { require(id.isNotBlank() && name.isNotBlank()); require(roughness in 0f..1f) }
}

object InteriorMaterialLibrary {
    val all: List<InteriorMaterial> = listOf(
        InteriorMaterial("wood-oak", "Дуб натуральный", SurfaceFamily.WOOD, 0xFFD8C2A5, .72f, SurfacePattern.WOOD_GRAIN, setOf(SurfaceUse.FURNITURE, SurfaceUse.FLOOR, SurfaceUse.COUNTERTOP)),
        InteriorMaterial("wood-walnut", "Орех", SurfaceFamily.WOOD, 0xFF8B6548, .68f, SurfacePattern.WOOD_GRAIN, setOf(SurfaceUse.FURNITURE, SurfaceUse.FLOOR, SurfaceUse.COUNTERTOP)),
        InteriorMaterial("wood-ash", "Ясень светлый", SurfaceFamily.WOOD, 0xFFE5D8C2, .74f, SurfacePattern.WOOD_GRAIN, setOf(SurfaceUse.FURNITURE, SurfaceUse.FLOOR)),
        InteriorMaterial("wood-dark", "Тёмное дерево", SurfaceFamily.WOOD, 0xFF594236, .7f, SurfacePattern.WOOD_GRAIN, setOf(SurfaceUse.FURNITURE, SurfaceUse.FLOOR)),
        InteriorMaterial("solid-white", "Белый матовый", SurfaceFamily.PAINT, 0xFFF2F1EC, .82f, SurfacePattern.SOLID, setOf(SurfaceUse.FURNITURE, SurfaceUse.WALL, SurfaceUse.CEILING)),
        InteriorMaterial("ceiling-white", "Потолок белый матовый", SurfaceFamily.PAINT, 0xFFF8F7F3, .9f, SurfacePattern.SOLID, setOf(SurfaceUse.CEILING)),
        InteriorMaterial("ceiling-warm", "Потолок тёплый белый", SurfaceFamily.PAINT, 0xFFFFF0D8, .9f, SurfacePattern.SOLID, setOf(SurfaceUse.CEILING)),
        InteriorMaterial("ceiling-wood", "Потолок светлое дерево", SurfaceFamily.WOOD, 0xFFE5D8C2, .74f, SurfacePattern.WOOD_GRAIN, setOf(SurfaceUse.CEILING)),
        InteriorMaterial("solid-graphite", "Графит", SurfaceFamily.PAINT, 0xFF555B63, .78f, SurfacePattern.SOLID, setOf(SurfaceUse.FURNITURE, SurfaceUse.WALL)),
        InteriorMaterial("solid-sand", "Песочный", SurfaceFamily.PAINT, 0xFFD9C6A5, .8f, SurfacePattern.SOLID, setOf(SurfaceUse.FURNITURE, SurfaceUse.WALL)),
        InteriorMaterial("solid-blue", "Приглушённый синий", SurfaceFamily.PAINT, 0xFF557FA8, .8f, SurfacePattern.SOLID, setOf(SurfaceUse.FURNITURE, SurfaceUse.WALL)),
        InteriorMaterial("solid-green", "Шалфей", SurfaceFamily.PAINT, 0xFF718B75, .8f, SurfacePattern.SOLID, setOf(SurfaceUse.FURNITURE, SurfaceUse.WALL)),
        InteriorMaterial("stone-marble", "Мрамор светлый", SurfaceFamily.STONE, 0xFFE5E1DA, .28f, SurfacePattern.STONE_GRAIN, setOf(SurfaceUse.COUNTERTOP, SurfaceUse.FLOOR, SurfaceUse.WALL, SurfaceUse.FURNITURE)),
        InteriorMaterial("stone-concrete", "Бетон", SurfaceFamily.STONE, 0xFFAAA9A4, .88f, SurfacePattern.STONE_GRAIN, setOf(SurfaceUse.COUNTERTOP, SurfaceUse.FLOOR, SurfaceUse.WALL, SurfaceUse.FURNITURE)),
        InteriorMaterial("stone-black", "Камень чёрный", SurfaceFamily.STONE, 0xFF343638, .3f, SurfacePattern.STONE_GRAIN, setOf(SurfaceUse.COUNTERTOP, SurfaceUse.FLOOR)),
        InteriorMaterial("tile-white", "Плитка белая", SurfaceFamily.CERAMIC, 0xFFF1F0EC, .24f, SurfacePattern.TILE, setOf(SurfaceUse.WALL, SurfaceUse.FLOOR)),
        InteriorMaterial("tile-sand", "Плитка бежевая", SurfaceFamily.CERAMIC, 0xFFDCCDB7, .3f, SurfacePattern.TILE, setOf(SurfaceUse.WALL, SurfaceUse.FLOOR)),
        InteriorMaterial("tile-dark", "Плитка антрацит", SurfaceFamily.CERAMIC, 0xFF55575A, .28f, SurfacePattern.TILE, setOf(SurfaceUse.WALL, SurfaceUse.FLOOR)),
        InteriorMaterial("metal-chrome", "Хром", SurfaceFamily.METAL, 0xFFBFC6CB, .16f, SurfacePattern.BRUSHED, setOf(SurfaceUse.FIXTURE, SurfaceUse.FURNITURE)),
        InteriorMaterial("metal-black", "Чёрный металл", SurfaceFamily.METAL, 0xFF292D31, .32f, SurfacePattern.BRUSHED, setOf(SurfaceUse.FIXTURE, SurfaceUse.FURNITURE)),
        InteriorMaterial("glass-clear", "Прозрачное стекло", SurfaceFamily.GLASS, 0x887FC5D8, .08f, SurfacePattern.TRANSPARENT, setOf(SurfaceUse.FURNITURE, SurfaceUse.FIXTURE)),
        InteriorMaterial("fabric-linen", "Лён светлый", SurfaceFamily.TEXTILE, 0xFFD8D0C2, .96f, SurfacePattern.FABRIC, setOf(SurfaceUse.UPHOLSTERY, SurfaceUse.FURNITURE)),
        InteriorMaterial("fabric-grey", "Ткань серая", SurfaceFamily.TEXTILE, 0xFF888B8C, .94f, SurfacePattern.FABRIC, setOf(SurfaceUse.UPHOLSTERY, SurfaceUse.FURNITURE)),
        InteriorMaterial("fabric-olive", "Ткань оливковая", SurfaceFamily.TEXTILE, 0xFF777B58, .95f, SurfacePattern.FABRIC, setOf(SurfaceUse.UPHOLSTERY, SurfaceUse.FURNITURE)),
        InteriorMaterial("plastic-white", "Белый пластик", SurfaceFamily.PLASTIC, 0xFFECEDEB, .48f, SurfacePattern.SOLID, setOf(SurfaceUse.FIXTURE, SurfaceUse.FURNITURE))
    )

    fun find(id: String): InteriorMaterial? = all.firstOrNull { it.id == id }
    fun forUse(use: SurfaceUse): List<InteriorMaterial> = all.filter { use in it.uses }
    fun validate(id: String, use: SurfaceUse): String? {
        val material = find(id) ?: return "Материал не найден: $id"
        return if (use !in material.uses) "Материал «${material.name}» нельзя использовать для этой поверхности" else null
    }
}
