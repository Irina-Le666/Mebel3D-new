import ru.mebel3d.app.core.*

fun main() {
    var checks = 0
    fun check(value: Boolean, label: String) { check(value) { label }; checks++ }
    val door = MotionBox("door", 0, 0, 0, 600, 30, 2000)
    val inSwing = MotionBox("wall-unit", 350, 350, 0, 700, 700, 2100)
    val clear = MotionBox("clear", 1200, 1200, 0, 1500, 1500, 2100)
    val swingBad = FurnitureMotionEngine.checkDoorSwing("door", door, DoorHingeSide.LEFT, 90, listOf(inSwing))
    check(!swingBad.isClear && swingBad.issues.any { it.obstacleId == "wall-unit" }, "door collision")
    val swingGood = FurnitureMotionEngine.checkDoorSwing("door", door, DoorHingeSide.LEFT, 90, listOf(clear))
    check(swingGood.isClear && swingGood.testedPositions > 1, "clear door swing")
    val drawer = MotionBox("drawer", 0, 0, 0, 500, 450, 180)
    val frontObstacle = MotionBox("island", 0, -400, 0, 600, -250, 900)
    val drawerBad = FurnitureMotionEngine.checkDrawerExtension("drawer", drawer, 400, DrawerPullDirection.FRONT, listOf(frontObstacle))
    check(!drawerBad.isClear, "drawer collision")
    val drawerGood = FurnitureMotionEngine.checkDrawerExtension("drawer", drawer, 400, DrawerPullDirection.FRONT, listOf(clear))
    check(drawerGood.isClear && drawerGood.testedPositions > 1, "clear drawer extension")
    check(FurnitureMotionEngine.checkDrawerExtension("d", drawer, -1, DrawerPullDirection.FRONT, emptyList()).issues.first().code == MotionIssueCode.INVALID_MOTION, "negative extension")
    check(FurnitureMotionEngine.checkDoorSwing("d", door, DoorHingeSide.LEFT, 90, emptyList(), angularStepDeg = 0).issues.first().code == MotionIssueCode.INVALID_MOTION, "invalid step")
    println("FurnitureMotionSmoke: $checks checks passed")
}
