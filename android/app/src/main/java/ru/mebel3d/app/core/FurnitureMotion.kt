package ru.mebel3d.app.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Motion-envelope checks for rectangular furniture parts. Millimetres throughout. */
data class MotionBox(
    val id: String,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int
) {
    init { require(minX <= maxX && minY <= maxY && minZ <= maxZ) }
}

enum class MotionKind { DOOR_SWING, DRAWER_EXTENSION }
enum class MotionIssueCode { INVALID_MOTION, COLLISION, INSUFFICIENT_CLEARANCE }
data class MotionIssue(val code: MotionIssueCode, val movingId: String, val obstacleId: String?, val message: String)
data class MotionCheckResult(val issues: List<MotionIssue>, val testedPositions: Int) {
    val isClear: Boolean get() = issues.isEmpty()
}

enum class DoorHingeSide { LEFT, RIGHT }
enum class DrawerPullDirection { FRONT, BACK }

object FurnitureMotionEngine {
    /** Conservative sampled swing-envelope test; a collision at any sample is reported. */
    fun checkDoorSwing(
        doorId: String,
        closed: MotionBox,
        hingeSide: DoorHingeSide,
        openingAngleDeg: Int,
        obstacles: List<MotionBox>,
        clearanceMm: Int = 2,
        angularStepDeg: Int = 5
    ): MotionCheckResult {
        if (openingAngleDeg !in -180..180 || clearanceMm < 0 || angularStepDeg !in 1..30 ||
            closed.maxX == closed.minX || closed.maxY == closed.minY) {
            return MotionCheckResult(listOf(MotionIssue(MotionIssueCode.INVALID_MOTION, doorId, null,
                "Неверный угол, зазор или размеры дверцы")), 0)
        }
        val hingeX = if (hingeSide == DoorHingeSide.LEFT) closed.minX else closed.maxX
        val steps = maxOf(1, (abs(openingAngleDeg) + angularStepDeg - 1) / angularStepDeg)
        val issues = linkedMapOf<String, MotionIssue>()
        var tested = 0
        for (step in 0..steps) {
            val sideSign = if (hingeSide == DoorHingeSide.LEFT) 1.0 else -1.0
            val angle = Math.toRadians(openingAngleDeg.toDouble() * step / steps) * sideSign
            val c = cos(angle); val s = sin(angle)
            // The hinge is a vertical axis along the door edge; only the X offset rotates.
            val corners = listOf(closed.minX to closed.minY, closed.minX to closed.maxY,
                closed.maxX to closed.minY, closed.maxX to closed.maxY).map { (x, y) ->
                val dx = x - hingeX
                (hingeX + dx * c) to (y + dx * s)
            }
            val moving = closed.copy(
                minX = kotlin.math.floor(corners.minOf { it.first }).toInt(),
                maxX = kotlin.math.ceil(corners.maxOf { it.first }).toInt(),
                minY = kotlin.math.floor(corners.minOf { it.second }).toInt(),
                maxY = kotlin.math.ceil(corners.maxOf { it.second }).toInt()
            )
            tested++
            obstacles.filter { it.id != doorId && overlaps(moving, it, clearanceMm) }.forEach { obstacle ->
                issues.putIfAbsent(obstacle.id, MotionIssue(MotionIssueCode.COLLISION, doorId, obstacle.id,
                    "Траектория открывания дверцы пересекается с объектом ${obstacle.id}"))
            }
        }
        return MotionCheckResult(issues.values.toList(), tested)
    }

    /** Checks a drawer at closed, intermediate and fully extended positions. */
    fun checkDrawerExtension(
        drawerId: String,
        closed: MotionBox,
        extensionMm: Int,
        direction: DrawerPullDirection,
        obstacles: List<MotionBox>,
        clearanceMm: Int = 2,
        stepMm: Int = 50
    ): MotionCheckResult {
        if (extensionMm < 0 || clearanceMm < 0 || stepMm <= 0 || closed.minY == closed.maxY) {
            return MotionCheckResult(listOf(MotionIssue(MotionIssueCode.INVALID_MOTION, drawerId, null,
                "Ход ящика, зазор и шаг проверки должны быть корректными")), 0)
        }
        val steps = maxOf(1, (extensionMm + stepMm - 1) / stepMm)
        val issues = linkedMapOf<String, MotionIssue>()
        var tested = 0
        for (step in 0..steps) {
            val travel = extensionMm * step / steps * if (direction == DrawerPullDirection.FRONT) -1 else 1
            val moving = closed.copy(minY = closed.minY + travel, maxY = closed.maxY + travel)
            tested++
            obstacles.filter { it.id != drawerId && overlaps(moving, it, clearanceMm) }.forEach { obstacle ->
                issues.putIfAbsent(obstacle.id, MotionIssue(MotionIssueCode.COLLISION, drawerId, obstacle.id,
                    "Ход ящика пересекается с объектом ${obstacle.id}"))
            }
        }
        return MotionCheckResult(issues.values.toList(), tested)
    }

    private fun overlaps(a: MotionBox, b: MotionBox, gap: Int): Boolean =
        a.minX < b.maxX + gap && a.maxX + gap > b.minX &&
            a.minY < b.maxY + gap && a.maxY + gap > b.minY &&
            a.minZ < b.maxZ + gap && a.maxZ + gap > b.minZ
}
