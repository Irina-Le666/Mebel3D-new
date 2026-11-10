package ru.mebel3d.app.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

/** Severity of a construction diagnostic. Warnings do not prevent saving a project. */
enum class AssemblyDiagnosticSeverity { ERROR, WARNING, INFO }

enum class AssemblyDiagnosticCode {
    DUPLICATE_PART_ID, INVALID_DIMENSIONS, NEGATIVE_POSITION,
    PARTS_OVERLAP, DISCONNECTED_PART, EMPTY_ASSEMBLY,
    DUPLICATE_JOINT_ID, JOINT_MISSING_PART, INVALID_JOINT_DIMENSIONS,
    INSUFFICIENT_EDGE_DISTANCE, INSUFFICIENT_MATERIAL_THICKNESS, SELF_JOIN
}

data class AssemblyDiagnostic(
    val code: AssemblyDiagnosticCode,
    val severity: AssemblyDiagnosticSeverity,
    val message: String,
    val partIds: List<String> = emptyList()
)

private data class PartBounds(
    val part: FurniturePart,
    val centerX: Double,
    val centerY: Double,
    val halfWidth: Double,
    val halfDepth: Double,
    val minZ: Long,
    val maxZ: Long,
    val axisXx: Double,
    val axisXy: Double,
    val axisYx: Double,
    val axisYy: Double
)

private data class FootprintRelation(val overlaps: Boolean, val touches: Boolean)


/**
 * Structural checks for a furniture assembly. Parts are axis-aligned in local assembly
 * coordinates; quarter-turn rotations swap the local X/Y extents. Face contact is allowed.
 */
object AssemblyDiagnosticsEngine {
    fun inspect(assembly: FurnitureAssembly, clearanceToleranceMm: Int = 1): List<AssemblyDiagnostic> {
        require(clearanceToleranceMm >= 0)
        val result = mutableListOf<AssemblyDiagnostic>()
        if (assembly.parts.isEmpty()) {
            return listOf(AssemblyDiagnostic(AssemblyDiagnosticCode.EMPTY_ASSEMBLY,
                AssemblyDiagnosticSeverity.ERROR, "Сборка не содержит деталей"))
        }

        assembly.parts.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { id ->
            result += AssemblyDiagnostic(AssemblyDiagnosticCode.DUPLICATE_PART_ID,
                AssemblyDiagnosticSeverity.ERROR, "Повторяется идентификатор детали: $id", listOf(id))
        }

        val partIds = assembly.parts.map { it.id }.toSet()
        assembly.joints.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { id ->
            result += AssemblyDiagnostic(AssemblyDiagnosticCode.DUPLICATE_JOINT_ID,
                AssemblyDiagnosticSeverity.ERROR, "Повторяется идентификатор соединения: $id")
        }
        assembly.joints.forEach { joint ->
            if (joint.firstPartId == joint.secondPartId) {
                result += AssemblyDiagnostic(AssemblyDiagnosticCode.SELF_JOIN,
                    AssemblyDiagnosticSeverity.ERROR, "Соединение «${joint.id}» ссылается на одну и ту же деталь")
            }
            val missing = listOf(joint.firstPartId, joint.secondPartId).filterNot { it in partIds }
            if (missing.isNotEmpty()) result += AssemblyDiagnostic(AssemblyDiagnosticCode.JOINT_MISSING_PART,
                AssemblyDiagnosticSeverity.ERROR, "Соединение «${joint.id}» ссылается на отсутствующую деталь: ${missing.joinToString()}")
            val dimensional = joint.fastenerDiameterMm >= 0 && joint.fastenerLengthMm >= 0 &&
                joint.edgeDistanceMm >= 0 && joint.materialThicknessMm >= 0 && joint.clearanceMm >= 0
            if (!dimensional || (joint.fastenerDiameterMm == 0 && joint.fastenerLengthMm > 0) ||
                (joint.fastenerDiameterMm > 0 && joint.fastenerLengthMm == 0)) {
                result += AssemblyDiagnostic(AssemblyDiagnosticCode.INVALID_JOINT_DIMENSIONS,
                    AssemblyDiagnosticSeverity.ERROR, "У соединения «${joint.id}» некорректны размеры крепежа или зазора")
            }
            if (joint.kind in setOf(JointKind.SCREW, JointKind.DOWEL, JointKind.CONFIRMAT, JointKind.MINIFIX) && joint.fastenerDiameterMm > 0) {
                val requiredEdge = joint.fastenerDiameterMm.toLong() * 2L
                if (joint.edgeDistanceMm.toLong() < requiredEdge) result += AssemblyDiagnostic(
                    AssemblyDiagnosticCode.INSUFFICIENT_EDGE_DISTANCE, AssemblyDiagnosticSeverity.WARNING,
                    "У соединения «${joint.id}» расстояние крепежа до края меньше ориентировочных ${requiredEdge} мм")
                val requiredThickness = (joint.fastenerDiameterMm.toLong() * 3L + 1L) / 2L
                if (joint.materialThicknessMm.toLong() in 1L until requiredThickness) result += AssemblyDiagnostic(
                    AssemblyDiagnosticCode.INSUFFICIENT_MATERIAL_THICKNESS, AssemblyDiagnosticSeverity.WARNING,
                    "У соединения «${joint.id}» толщина материала меньше ориентировочных ${requiredThickness} мм")
            }
        }

        val validParts = assembly.parts.filter { part ->
            val valid = part.size.width > 0 && part.size.depth > 0 && part.size.height > 0
            if (!valid) result += AssemblyDiagnostic(AssemblyDiagnosticCode.INVALID_DIMENSIONS,
                AssemblyDiagnosticSeverity.ERROR, "У детали «${part.name}» некорректные размеры", listOf(part.id))
            // Negative X/Y are valid when a board intentionally overhangs its carcass.
            // Only a negative elevation is physically below the assembly floor.
            if (part.localPosition.z < 0) {
                result += AssemblyDiagnostic(AssemblyDiagnosticCode.NEGATIVE_POSITION,
                    AssemblyDiagnosticSeverity.ERROR, "Деталь «${part.name}» расположена ниже основания сборки", listOf(part.id))
            }
            valid
        }
        val bounds = validParts.map { part ->
            val radians = part.rotationDeg.toDouble() * PI / 180.0
            val ux = cos(radians); val uy = sin(radians)
            val vx = -uy; val vy = ux
            PartBounds(part,
                part.localPosition.x + part.size.width / 2.0,
                part.localPosition.y + part.size.depth / 2.0,
                part.size.width / 2.0, part.size.depth / 2.0,
                part.localPosition.z.toLong(), part.localPosition.z.toLong() + part.size.height,
                ux, uy, vx, vy)
        }

        val connected = bounds.associate { it.part.id to mutableSetOf<String>() }.toMutableMap()
        for (i in bounds.indices) for (j in i + 1 until bounds.size) {
            val a = bounds[i]; val b = bounds[j]
            val zOverlap = min(a.maxZ, b.maxZ) - max(a.minZ, b.minZ)
            val zGap = max(max(a.minZ - b.maxZ, b.minZ - a.maxZ), 0L)
            val footprint = footprintRelation(a, b, clearanceToleranceMm.toDouble())
            if (footprint.overlaps && zOverlap > clearanceToleranceMm.toLong()) {
                result += AssemblyDiagnostic(AssemblyDiagnosticCode.PARTS_OVERLAP,
                    AssemblyDiagnosticSeverity.WARNING,
                    "Детали «${a.part.name}» и «${b.part.name}» пересекаются по объёму",
                    listOf(a.part.id, b.part.id))
            }
            val faceTouch = (footprint.touches && zOverlap > 0L) ||
                (zGap <= clearanceToleranceMm.toLong() && footprint.overlaps)
            if (faceTouch) {
                connected[a.part.id]?.add(b.part.id)
                connected[b.part.id]?.add(a.part.id)
            }
        }

        // Report only truly isolated parts; a multi-part assembly may intentionally contain groups.
        bounds.filter { connected[it.part.id].isNullOrEmpty() }.forEach { isolated ->
            result += AssemblyDiagnostic(AssemblyDiagnosticCode.DISCONNECTED_PART,
                AssemblyDiagnosticSeverity.WARNING,
                "Деталь «${isolated.part.name}» не соприкасается с другими деталями сборки",
                listOf(isolated.part.id))
        }
        return result
    }

    /** Separating-axis test for two rotated rectangular footprints in assembly-local XY. */
    private fun footprintRelation(a: PartBounds, b: PartBounds, tolerance: Double): FootprintRelation {
        val axes = listOf(
            a.axisXx to a.axisXy, a.axisYx to a.axisYy,
            b.axisXx to b.axisXy, b.axisYx to b.axisYy
        )
        var allOverlap = true
        var nearSeparatingAxes = 0
        for ((axisX, axisY) in axes) {
            val centerA = a.centerX * axisX + a.centerY * axisY
            val centerB = b.centerX * axisX + b.centerY * axisY
            val radiusA = a.halfWidth * kotlin.math.abs(a.axisXx * axisX + a.axisXy * axisY) +
                a.halfDepth * kotlin.math.abs(a.axisYx * axisX + a.axisYy * axisY)
            val radiusB = b.halfWidth * kotlin.math.abs(b.axisXx * axisX + b.axisXy * axisY) +
                b.halfDepth * kotlin.math.abs(b.axisYx * axisX + b.axisYy * axisY)
            val overlap = radiusA + radiusB - kotlin.math.abs(centerA - centerB)
            if (overlap <= tolerance) allOverlap = false
            if (overlap >= -tolerance) {
                if (overlap <= tolerance) nearSeparatingAxes++
                continue
            }
            return FootprintRelation(false, false)
        }
        return FootprintRelation(
            overlaps = allOverlap,
            touches = !allOverlap && nearSeparatingAxes > 0 && axes.all { (x, y) ->
                val ca = a.centerX * x + a.centerY * y
                val cb = b.centerX * x + b.centerY * y
                val ra = a.halfWidth * kotlin.math.abs(a.axisXx * x + a.axisXy * y) + a.halfDepth * kotlin.math.abs(a.axisYx * x + a.axisYy * y)
                val rb = b.halfWidth * kotlin.math.abs(b.axisXx * x + b.axisXy * y) + b.halfDepth * kotlin.math.abs(b.axisYx * x + b.axisYy * y)
                ra + rb - kotlin.math.abs(ca - cb) >= -tolerance
            }
        )
    }
}
