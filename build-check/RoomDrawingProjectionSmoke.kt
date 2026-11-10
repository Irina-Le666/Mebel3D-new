package ru.mebel3d.app.core

object RoomDrawingProjectionSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val room = RoomModel(
            id = "r", widthMm = 5000, depthMm = 3500, heightMm = 2700,
            openings = listOf(
                WallOpening("door-n", WallSide.NORTH, 800, 900, 0, 2100, OpeningKind.DOOR),
                WallOpening("window-e", WallSide.EAST, 500, 1200, 900, 1200, OpeningKind.WINDOW),
                WallOpening("door-s", WallSide.SOUTH, 3600, 800, 0, 2100, OpeningKind.DOOR),
                WallOpening("window-w", WallSide.WEST, 900, 1000, 850, 1200, OpeningKind.WINDOW)
            ),
            engineering = listOf(
                EngineeringElement("socket-n", "Розетка", EngineeringKind.OUTLET, Vec3Mm(1800, 0, 300), WallSide.NORTH),
                EngineeringElement("water-e", "Вода", EngineeringKind.WATER, Vec3Mm(5000, 1700, 600), WallSide.EAST),
                EngineeringElement("gas-s", "Газ", EngineeringKind.GAS, Vec3Mm(2400, 3500, 500), WallSide.SOUTH),
                EngineeringElement("switch-w", "Выключатель", EngineeringKind.SWITCH, Vec3Mm(0, 2000, 1100), WallSide.WEST)
            )
        )
        val furniture = listOf(
            FurnitureItem("cab", "Шкаф", Size3Mm(1000, 600, 2200), Vec3Mm(1000, 400, 0), rotationDeg = 90, roomId = "r"),
            FurnitureItem("other-room", "Диван", Size3Mm(2000, 900, 800), Vec3Mm(2500, 1700, 0), roomId = "other")
        )
        val drawings = RoomDrawingProjection.build(room, furniture)
        check(drawings.plan.widthMm == 5000 && drawings.plan.depthMm == 3500)
        check(drawings.plan.openings.size == 4)
        check(drawings.plan.engineering.size == 4)
        check(drawings.plan.furniture.size == 1) { "plan must filter furniture by room ownership" }
        val north = drawings.elevations.getValue(WallSide.NORTH)
        check(north.lengthMm == 5000 && north.heightMm == 2700)
        check(north.openings.single().bounds == DrawingRect(800.0, 0.0, 1700.0, 2100.0))
        check(north.engineering.single().alongMm == 1800 && north.engineering.single().elevationMm == 300)
        check(north.horizontalDimensions.map { it.lengthMm }.sum() == 5000) { "horizontal chain must close to wall length" }
        check(north.verticalDimensions.any { it.label == "Высота стены" && it.lengthMm == 2700 })
        check(drawings.elevations.getValue(WallSide.EAST).engineering.single().alongMm == 1700)
        check(drawings.elevations.getValue(WallSide.SOUTH).engineering.single().alongMm == 2400)
        check(drawings.elevations.getValue(WallSide.WEST).engineering.single().alongMm == 2000)
        check(north.solidPanels.isNotEmpty())
        check(north.furniture.any { it.id == "cab" }) { "rotated cabinet near north wall must appear in elevation" }
        check(drawings.elevations.values.all { it.horizontalDimensions.sumOf(DrawingDimension::lengthMm) == it.lengthMm })
        check(RoomDrawingProjection.build(room, emptyList()).plan.furniture.isEmpty())
        println("RoomDrawingProjectionSmoke: 14 checks passed")
    }
}
