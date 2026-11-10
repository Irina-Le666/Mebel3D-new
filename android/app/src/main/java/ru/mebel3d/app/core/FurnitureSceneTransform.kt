package ru.mebel3d.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * World-space transform for local furniture geometry.
 * FurnitureItem.position is the centre of its floor footprint throughout the core;
 * local coordinates use the usual [0,width] x [0,depth] rectangle.
 */
object FurnitureSceneTransform {
    fun point(
        item: FurnitureItem,
        localXmm: Float,
        localYmm: Float,
        zMm: Float = item.position.z.toFloat()
    ): Vec3MmFloat {
        require(localXmm.isFinite() && localYmm.isFinite() && zMm.isFinite()) { "Некорректные координаты детали" }
        val angle = item.rotationDeg * PI.toFloat() / 180f
        val dx = localXmm - item.size.width / 2f
        val dy = localYmm - item.size.depth / 2f
        val x = item.position.x + dx * cos(angle) - dy * sin(angle)
        val y = item.position.y + dx * sin(angle) + dy * cos(angle)
        return Vec3MmFloat(x, y, zMm)
    }
}

data class Vec3MmFloat(val x: Float, val y: Float, val z: Float)
