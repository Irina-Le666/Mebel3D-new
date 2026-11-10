package ru.mebel3d.app.core

import kotlin.math.abs

fun main() {
    var checks = 0
    fun near(actual: Float, expected: Float, label: String) {
        check(abs(actual - expected) < 0.01f) { "$label: expected $expected, got $actual" }
        checks++
    }
    val base = FurnitureItem("x", "Шкаф", Size3Mm(1200, 600, 2200), Vec3Mm(1000, 800, 0), 0)
    // Position is the footprint centre: local centre must map exactly to it.
    val center = FurnitureSceneTransform.point(base, 600f, 300f)
    near(center.x, 1000f, "centre X")
    near(center.y, 800f, "centre Y")
    val topLeft = FurnitureSceneTransform.point(base, 0f, 0f)
    near(topLeft.x, 400f, "unrotated left X")
    near(topLeft.y, 500f, "unrotated near Y")
    val bottomRight = FurnitureSceneTransform.point(base, 1200f, 600f)
    near(bottomRight.x, 1600f, "unrotated right X")
    near(bottomRight.y, 1100f, "unrotated far Y")

    val rotated = base.copy(rotationDeg = 90)
    val rotatedCenter = FurnitureSceneTransform.point(rotated, 600f, 300f)
    near(rotatedCenter.x, 1000f, "rotated centre X")
    near(rotatedCenter.y, 800f, "rotated centre Y")
    val rotatedCorner = FurnitureSceneTransform.point(rotated, 1200f, 300f)
    near(rotatedCorner.x, 1000f, "rotated right midpoint X")
    near(rotatedCorner.y, 1400f, "rotated right midpoint Y")
    val fullTurn = FurnitureSceneTransform.point(base.copy(rotationDeg = 360), 0f, 0f)
    near(fullTurn.x, topLeft.x, "full turn X")
    near(fullTurn.y, topLeft.y, "full turn Y")
    check(runCatching { FurnitureSceneTransform.point(base, Float.NaN, 0f) }.isFailure)
    checks++
    println("FurnitureSceneTransformSmoke: $checks checks passed")
}
