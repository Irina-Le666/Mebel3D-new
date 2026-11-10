package ru.mebel3d.app

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import ru.mebel3d.app.core.*
import kotlin.math.min

/** Compact, live-scaled plan and elevation preview for the drawing workspace. */
@Composable
fun RoomDrawingPreview(
    plan: RoomPlanProjection,
    elevation: WallElevationProjection,
    selectedFurnitureId: String? = null,
    selectedOpeningId: String? = null,
    selectedEngineeringId: String? = null,
    onSelectFurniture: (String) -> Unit = {},
    onSelectOpening: (String) -> Unit = {},
    onSelectEngineering: (String) -> Unit = {}
) {
    Canvas(Modifier.fillMaxWidth().height(210.dp).pointerInput(plan, elevation, onSelectFurniture, onSelectOpening, onSelectEngineering) {
        detectTapGestures { tap ->
            val w = size.width
            val h = size.height
            val split = w / 2f
            val pAvailW = split - 22f
            val pAvailH = h - 42f
            val pScale = min(pAvailW / plan.widthMm.coerceAtLeast(1), pAvailH / plan.depthMm.coerceAtLeast(1))
            val pLeft = 10f + (pAvailW - plan.widthMm * pScale) / 2f
            val pTop = 34f + (pAvailH - plan.depthMm * pScale) / 2f
            val planXmm = ((tap.x - pLeft) / pScale).toDouble()
            val planYmm = ((tap.y - pTop) / pScale).toDouble()
            val planHitId = RoomDrawingSelection.selectPlanFurniture(plan, planXmm, planYmm)
            if (planHitId != null) { onSelectFurniture(planHitId); return@detectTapGestures }
            val planTolerance = 14.0 / pScale.coerceAtLeast(0.0001f)
            RoomDrawingSelection.selectPlanEngineering(plan, planXmm, planYmm, planTolerance)?.let { onSelectEngineering(it); return@detectTapGestures }
            RoomDrawingSelection.selectPlanOpening(plan, planXmm, planYmm, planTolerance)?.let { onSelectOpening(it); return@detectTapGestures }

            val eAvailW = split - 22f
            val eAvailH = h - 45f
            val eScale = min(eAvailW / elevation.lengthMm.coerceAtLeast(1), eAvailH / elevation.heightMm.coerceAtLeast(1))
            val eLeft = split + 10f + (eAvailW - elevation.lengthMm * eScale) / 2f
            val eTop = 34f + (eAvailH - elevation.heightMm * eScale) / 2f
            val eBottom = eTop + elevation.heightMm * eScale
            val alongMm = ((tap.x - eLeft) / eScale).toDouble()
            val heightMm = ((eBottom - tap.y) / eScale).toDouble()
            RoomDrawingSelection.selectElevationFurniture(elevation, alongMm, heightMm)?.let { onSelectFurniture(it); return@detectTapGestures }
            val elevationTolerance = 14.0 / eScale.coerceAtLeast(0.0001f)
            RoomDrawingSelection.selectElevationEngineering(elevation, alongMm, heightMm, elevationTolerance)?.let { onSelectEngineering(it); return@detectTapGestures }
            RoomDrawingSelection.selectElevationOpening(elevation, alongMm, heightMm)?.let(onSelectOpening)
        }
    }) {
        val canvas = drawContext.canvas.nativeCanvas
        val w = size.width
        val h = size.height
        val split = w / 2f
        fun paint(color: Int, style: Paint.Style = Paint.Style.STROKE, stroke: Float = 2.2f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; this.style = style; this.strokeWidth = stroke
        }
        val wall = paint(0xFF26364D.toInt(), stroke = 4f)
        val opening = paint(0xFF1677F8.toInt(), stroke = 3f)
        val furniturePaint = paint(0xFF8CB9ED.toInt(), Paint.Style.FILL_AND_STROKE, 2f)
        val selectedFurniturePaint = paint(0xFF1677F8.toInt(), Paint.Style.FILL_AND_STROKE, 3.2f)
        val selectedOpeningPaint = paint(0xFF1677F8.toInt(), stroke = 5f)
        val selectedEngineeringPaint = paint(0xFF1677F8.toInt(), Paint.Style.FILL_AND_STROKE, 2f)
        val markerPaint = paint(0xFFF5A623.toInt(), Paint.Style.FILL_AND_STROKE, 1.5f)
        val text = paint(0xFF34445B.toInt(), Paint.Style.FILL, 1f).apply { textSize = 12f; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
        val mutedText = paint(0xFF69758A.toInt(), Paint.Style.FILL, 1f).apply { textSize = 10f }
        canvas.drawText("ПЛАН", 8f, 13f, text)
        canvas.drawText("${plan.widthMm} × ${plan.depthMm} мм", 8f, 26f, mutedText)
        canvas.drawText("РАЗВЁРТКА", split + 8f, 13f, text)
        canvas.drawText("${elevation.lengthMm} × ${elevation.heightMm} мм", split + 8f, 26f, mutedText)

        val pAvailW = split - 22f
        val pAvailH = h - 42f
        val pScale = min(pAvailW / plan.widthMm.coerceAtLeast(1), pAvailH / plan.depthMm.coerceAtLeast(1))
        val pLeft = 10f + (pAvailW - plan.widthMm * pScale) / 2f
        val pTop = 34f + (pAvailH - plan.depthMm * pScale) / 2f
        canvas.drawRect(pLeft, pTop, pLeft + plan.widthMm * pScale, pTop + plan.depthMm * pScale, wall)
        plan.openings.forEach { item ->
            val coords = when (item.side) {
                WallSide.NORTH -> floatArrayOf(pLeft + item.startMm * pScale, pTop, pLeft + item.endMm * pScale, pTop)
                WallSide.SOUTH -> floatArrayOf(pLeft + item.startMm * pScale, pTop + plan.depthMm * pScale, pLeft + item.endMm * pScale, pTop + plan.depthMm * pScale)
                WallSide.WEST -> floatArrayOf(pLeft, pTop + item.startMm * pScale, pLeft, pTop + item.endMm * pScale)
                WallSide.EAST -> floatArrayOf(pLeft + plan.widthMm * pScale, pTop + item.startMm * pScale, pLeft + plan.widthMm * pScale, pTop + item.endMm * pScale)
            }
            canvas.drawLine(coords[0], coords[1], coords[2], coords[3], paint(android.graphics.Color.WHITE, stroke = 7f))
            canvas.drawLine(coords[0], coords[1], coords[2], coords[3], if (item.id == selectedOpeningId) selectedOpeningPaint else opening)
        }
        plan.furniture.forEach { item ->
            val path = Path()
            item.polygon.forEachIndexed { index, point ->
                val x = pLeft + point.xMm.toFloat() * pScale
                val y = pTop + point.yMm.toFloat() * pScale
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); canvas.drawPath(path, if (item.id == selectedFurnitureId) selectedFurniturePaint else furniturePaint)
        }
        plan.engineering.forEach { marker -> canvas.drawCircle(pLeft + marker.point.xMm.toFloat() * pScale, pTop + marker.point.yMm.toFloat() * pScale, if (marker.id == selectedEngineeringId) 5.5f else 3.2f, if (marker.id == selectedEngineeringId) selectedEngineeringPaint else markerPaint) }

        val eAvailW = split - 22f
        val eAvailH = h - 45f
        val eScale = min(eAvailW / elevation.lengthMm.coerceAtLeast(1), eAvailH / elevation.heightMm.coerceAtLeast(1))
        val eLeft = split + 10f + (eAvailW - elevation.lengthMm * eScale) / 2f
        val eTop = 34f + (eAvailH - elevation.heightMm * eScale) / 2f
        val eBottom = eTop + elevation.heightMm * eScale
        canvas.drawRect(eLeft, eTop, eLeft + elevation.lengthMm * eScale, eBottom, wall)
        elevation.openings.forEach { item ->
            val b = item.bounds
            val l = eLeft + b.leftMm.toFloat() * eScale
            val r = eLeft + b.rightMm.toFloat() * eScale
            val t = eBottom - b.topMm.toFloat() * eScale
            val bot = eBottom - b.bottomMm.toFloat() * eScale
            canvas.drawRect(l, t, r, bot, paint(if (item.id == selectedOpeningId) 0xFFB8D5FF.toInt() else 0xFFEAF3FF.toInt(), Paint.Style.FILL_AND_STROKE, if (item.id == selectedOpeningId) 3f else 1.4f))
        }
        elevation.furniture.forEach { item ->
            val b = item.bounds
            canvas.drawRect(eLeft + b.leftMm.toFloat() * eScale, eBottom - b.topMm.toFloat() * eScale,
                eLeft + b.rightMm.toFloat() * eScale, eBottom - b.bottomMm.toFloat() * eScale, if (item.id == selectedFurnitureId) selectedFurniturePaint else furniturePaint)
        }
        elevation.engineering.forEach { marker ->
            canvas.drawCircle(eLeft + marker.alongMm * eScale, eBottom - marker.elevationMm * eScale, if (marker.id == selectedEngineeringId) 5.5f else 3f, if (marker.id == selectedEngineeringId) selectedEngineeringPaint else markerPaint)
        }
        canvas.drawText("Нажмите на мебель, проём или инженерную точку, чтобы выбрать объект", 8f, h - 4f, mutedText)
    }
}
