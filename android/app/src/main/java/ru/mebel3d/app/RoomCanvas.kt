package ru.mebel3d.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.dp
import ru.mebel3d.app.core.FurnitureItem
import ru.mebel3d.app.core.FurnitureAssembly
import ru.mebel3d.app.core.FurniturePart
import ru.mebel3d.app.core.RoomModel
import ru.mebel3d.app.core.WallSide
import ru.mebel3d.app.core.Vec3Mm
import ru.mebel3d.app.core.SceneHitTesting
import ru.mebel3d.app.core.SceneGapGuide
import ru.mebel3d.app.core.OpeningKind
import ru.mebel3d.app.core.RoomVisualizationEngine
import ru.mebel3d.app.core.WallDisplayMode
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@androidx.compose.runtime.Composable
private fun roomSceneScale(
    canvasWidth: Float,
    canvasHeight: Float,
    room: RoomModel,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    is3d: Boolean
): Float {
    val w = room.widthMm.toFloat()
    val d = room.depthMm.toFloat()
    if (!is3d) {
        return max(0.025f, minOf(canvasWidth / (w + d) * 0.72f, canvasHeight / (w + d) * 0.48f)) * zoom
    }
    val yaw = yawDeg * PI.toFloat() / 180f
    val pitch = pitchDeg * PI.toFloat() / 180f
    val projectedWidth = 0.58f * (w * kotlin.math.abs(cos(yaw)) + d * kotlin.math.abs(sin(yaw)))
    val projectedHeight = 0.58f * (
        (w * kotlin.math.abs(sin(yaw)) + d * kotlin.math.abs(cos(yaw))) * kotlin.math.sin(pitch) +
            room.heightMm * kotlin.math.cos(pitch)
        )
    val fitWidth = canvasWidth * 0.88f / projectedWidth.coerceAtLeast(1f)
    val fitHeight = canvasHeight * 0.84f / projectedHeight.coerceAtLeast(1f)
    return minOf(fitWidth, fitHeight).coerceAtLeast(0.001f) * zoom
}

fun RoomCanvas(room: RoomModel, furniture: List<FurnitureItem>, selectedId: String?, selectedEngineeringId: String?, selectedOpeningId: String?, is3d: Boolean, cameraYaw: Float = 45f, cameraPitch: Float = 32f, cameraZoom: Float = 1f, wallDisplay: String = "Срез", showCeiling: Boolean = false, wallColorArgb: Long = 0xFFF0ECE5L, floorColorArgb: Long = 0xFFEADCC8L, lightingPreset: String = "Дневной",
    wallMaterialId: String? = null, northWallMaterialId: String? = null, eastWallMaterialId: String? = null, southWallMaterialId: String? = null, westWallMaterialId: String? = null, floorMaterialId: String? = null, ceilingColorArgb: Long = 0xFFF8F7F3L, ceilingMaterialId: String? = null, lightIntensityPercent: Int = 72, lightTemperatureKelvin: Int = 4200, shadowStrengthPercent: Int = 35, onSelect: (String) -> Unit, onMoveSelected: (Int, Int) -> Unit, assemblies: List<FurnitureAssembly> = emptyList(), selectedPartKey: String? = null, onSelectPart: (String, String) -> Unit = { _, _ -> }, onMoveSelectedPart: (String, String, Int, Int) -> Unit = { _, _, _, _ -> }, onResizeSelectedPart: (String, String, Int, Int) -> Unit = { _, _, _, _ -> }, onCameraChange: (Float, Float, Float) -> Unit = { _, _, _ -> }, onDragStart: () -> Unit = {}, onDragEnd: () -> Unit = {}, onDragCancel: () -> Unit = {}, measurementGuides: List<SceneGapGuide> = emptyList(), onResizeSelectedFurniture: (Int, Int) -> Unit = { _, _ -> }, onRotateSelectedFurniture: (Int) -> Unit = {}, onChangeSelectedFurnitureHeight: (Int) -> Unit = {}, onRotateSelectedPart: (String, String, Int) -> Unit = { _, _, _ -> }, onChangeSelectedPartHeight: (String, String, Int) -> Unit = { _, _, _ -> }) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF8FAFD), RoundedCornerShape(12.dp))) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(selectedId, selectedPartKey, room, is3d, assemblies, cameraYaw, cameraPitch, cameraZoom) {
                var resizeHandleActive = false
                var partRotationHandleActive = false
                var partHeightHandleActive = false
                var partGestureCenter = Offset.Zero
                var partRotationGesture: ru.mebel3d.app.core.AssemblyPartGestureTransform.Gesture? = null
                var partHeightGesture: ru.mebel3d.app.core.AssemblyPartGestureTransform.Gesture? = null
                var furnitureResizeHandleActive = false
                var furnitureRotationHandleActive = false
                var furnitureHeightHandleActive = false
                var dragStartPointer = Offset.Zero
                var dragPointer = Offset.Zero
                var rotationGesture: ru.mebel3d.app.core.SceneGestureTransform.FurnitureGesture? = null
                var heightGesture: ru.mebel3d.app.core.SceneGestureTransform.FurnitureGesture? = null
                var furnitureGestureCenter = Offset.Zero
                fun toRoomVector(screen: Offset, scale: Float): Pair<Float, Float> {
                    if (!is3d) return (screen.x / scale) to (screen.y / scale)
                    val horizontal = screen.x / (0.58f * scale)
                    val vertical = screen.y / (0.58f * scale * kotlin.math.sin(cameraPitch * PI.toFloat() / 180f).coerceAtLeast(0.08f))
                    val yaw = cameraYaw * PI.toFloat() / 180f
                    return (horizontal * cos(yaw) + vertical * sin(yaw)) to (-horizontal * sin(yaw) + vertical * cos(yaw))
                }
                detectDragGestures(onDragStart = { start ->
                    onDragStart()
                    dragStartPointer = start
                    dragPointer = start
                    val selectedFurniture = furniture.firstOrNull { it.id == selectedId }
                    if (selectedFurniture != null && selectedPartKey == null) {
                        val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                        val cx = size.width * 0.5f; val cy = size.height * if (is3d) 0.58f else 0.5f
                        val yaw = cameraYaw * PI.toFloat() / 180f
                        val centerDx = selectedFurniture.position.x - room.widthMm / 2f
                        val centerDy = selectedFurniture.position.y - room.depthMm / 2f
                        val center = if (is3d) Offset(cx + (centerDx*cos(yaw)-centerDy*sin(yaw))*scale*0.58f, cy + (centerDx*sin(yaw)+centerDy*cos(yaw))*scale*0.58f*kotlin.math.sin(cameraPitch * PI.toFloat() / 180f) - (selectedFurniture.position.z + selectedFurniture.size.height/2f)*scale*0.58f*kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
                            else Offset(cx-room.widthMm*scale*0.5f+(selectedFurniture.position.x+selectedFurniture.size.width/2f)*scale, cy-room.depthMm*scale*0.5f+(selectedFurniture.position.y+selectedFurniture.size.depth/2f)*scale)
                        furnitureGestureCenter = center
                        val rotationHandle = center + Offset(38.dp.toPx(), -28.dp.toPx())
                        val heightHandle = Offset(cx + (centerDx*cos(yaw)-centerDy*sin(yaw))*scale*0.58f, cy + (centerDx*sin(yaw)+centerDy*cos(yaw))*scale*0.58f*kotlin.math.sin(cameraPitch * PI.toFloat() / 180f) - (selectedFurniture.position.z + selectedFurniture.size.height)*scale*0.58f*kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
                        furnitureRotationHandleActive = (start-rotationHandle).getDistance() < 25.dp.toPx()
                        furnitureHeightHandleActive = is3d && !furnitureRotationHandleActive && (start-heightHandle).getDistance() < 25.dp.toPx()
                        if (furnitureRotationHandleActive) {
                            val vector = toRoomVector(start-center, scale)
                            rotationGesture = ru.mebel3d.app.core.SceneGestureTransform.beginRotation(selectedFurniture, vector.first, vector.second)
                        }
                        if (furnitureHeightHandleActive) heightGesture = ru.mebel3d.app.core.SceneGestureTransform.beginHeightChange(selectedFurniture)
                        val angle = selectedFurniture.rotationDeg * PI.toFloat() / 180f
                        val localX = selectedFurniture.size.width / 2f
                        val localY = selectedFurniture.size.depth / 2f
                        val cornerX = selectedFurniture.position.x + localX * cos(angle) - localY * sin(angle)
                        val cornerY = selectedFurniture.position.y + localX * sin(angle) + localY * cos(angle)
                        val handle = if (is3d) {
                            val dx = cornerX - room.widthMm / 2f; val dy = cornerY - room.depthMm / 2f
                            Offset(cx + (dx*cos(yaw)-dy*sin(yaw))*scale*0.58f, cy + (dx*sin(yaw)+dy*cos(yaw))*scale*0.58f*kotlin.math.sin(cameraPitch * PI.toFloat() / 180f) - (selectedFurniture.position.z + selectedFurniture.size.height)*scale*0.58f*kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
                        } else Offset(cx - room.widthMm*scale*0.5f + cornerX*scale, cy - room.depthMm*scale*0.5f + cornerY*scale)
                        furnitureResizeHandleActive = (start-handle).getDistance() < 32.dp.toPx()
                    }
                    val key = selectedPartKey
                    if (key != null) {
                        val ids = key.split(":", limit = 2)
                        val part = assemblies.firstOrNull { it.id == ids[0] }?.parts?.firstOrNull { it.id == ids.getOrNull(1) }
                        val assembly = assemblies.firstOrNull { it.id == ids[0] }
                        if (part != null && assembly != null) {
                            val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                            val cx = size.width * 0.5f; val cy = size.height * if (is3d) 0.58f else 0.5f
                            val partCx = assembly.position.x + part.localPosition.x + part.size.width / 2f
                            val partCy = assembly.position.y + part.localPosition.y + part.size.depth / 2f
                            val partAngle = part.rotationDeg * PI.toFloat() / 180f
                            val hx = partCx + part.size.width / 2f * cos(partAngle) - part.size.depth / 2f * sin(partAngle)
                            val hy = partCy + part.size.width / 2f * sin(partAngle) + part.size.depth / 2f * cos(partAngle)
                            val hz = assembly.position.z + part.localPosition.z + part.size.height
                            val partCz = assembly.position.z + part.localPosition.z + part.size.height / 2f
                            val yaw = cameraYaw * PI.toFloat() / 180f
                            fun projectPart(px: Float, py: Float, pz: Float): Offset {
                                if (!is3d) return Offset(cx-room.widthMm*scale*0.5f+px*scale, cy-room.depthMm*scale*0.5f+py*scale)
                                val dx = px-room.widthMm/2f; val dy = py-room.depthMm/2f
                                return Offset(cx+(dx*cos(yaw)-dy*sin(yaw))*scale*0.58f,
                                    cy+(dx*sin(yaw)+dy*cos(yaw))*scale*0.58f*kotlin.math.sin(cameraPitch * PI.toFloat() / 180f)-pz*scale*0.58f*kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
                            }
                            val handle = projectPart(hx, hy, hz.toFloat())
                            val center = projectPart(partCx, partCy, partCz)
                            val rotationHandle = center + Offset(38.dp.toPx(), -28.dp.toPx())
                            val heightHandle = projectPart(partCx, partCy, hz.toFloat())
                            partGestureCenter = center
                            partRotationHandleActive = (start-rotationHandle).getDistance() < 25.dp.toPx()
                            partHeightHandleActive = is3d && !partRotationHandleActive && (start-heightHandle).getDistance() < 25.dp.toPx()
                            if (partRotationHandleActive) {
                                val vector = toRoomVector(start-center, scale)
                                partRotationGesture = ru.mebel3d.app.core.AssemblyPartGestureTransform.begin(assembly, part, vector.first, vector.second)
                            }
                            if (partHeightHandleActive) partHeightGesture = ru.mebel3d.app.core.AssemblyPartGestureTransform.begin(assembly, part, 1f, 0f)
                            resizeHandleActive = !partRotationHandleActive && !partHeightHandleActive && (start-handle).getDistance() < 30.dp.toPx()
                        }
                    }
                }, onDragEnd = { onDragEnd(); furnitureRotationHandleActive = false; furnitureHeightHandleActive = false; partRotationHandleActive = false; partHeightHandleActive = false; rotationGesture = null; heightGesture = null; partRotationGesture = null; partHeightGesture = null }, onDragCancel = { onDragCancel(); furnitureRotationHandleActive = false; furnitureHeightHandleActive = false; partRotationHandleActive = false; partHeightHandleActive = false; rotationGesture = null; heightGesture = null; partRotationGesture = null; partHeightGesture = null }) { change, dragAmount ->
                    change.consume()
                    dragPointer += dragAmount
                    if (furnitureRotationHandleActive && selectedId != null) {
                        val item = furniture.firstOrNull { it.id == selectedId }
                        val gesture = rotationGesture
                        if (item != null && gesture != null) {
                            val vector = toRoomVector(dragPointer-furnitureGestureCenter, roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d))
                            runCatching { onRotateSelectedFurniture(ru.mebel3d.app.core.SceneGestureTransform.rotationAt(gesture, vector.first, vector.second, 1)) }
                        }
                        return@detectDragGestures
                    }
                    if (furnitureHeightHandleActive && selectedId != null) {
                        val item = furniture.firstOrNull { it.id == selectedId }
                        val gesture = heightGesture
                        if (item != null && gesture != null) {
                            val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                            val target = ru.mebel3d.app.core.SceneGestureTransform.heightAt(gesture, dragPointer.y-dragStartPointer.y, scale, is3d, room.heightMm-item.position.z, cameraPitch)
                            onChangeSelectedFurnitureHeight(target)
                        }
                        return@detectDragGestures
                    }
                    if (selectedPartKey != null) {
                        val ids = selectedPartKey.split(":", limit = 2)
                        val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                        if (partRotationHandleActive && ids.size == 2) {
                            val gesture = partRotationGesture
                            if (gesture != null) {
                                val vector = toRoomVector(dragPointer-partGestureCenter, scale)
                                onRotateSelectedPart(ids[0], ids[1], ru.mebel3d.app.core.AssemblyPartGestureTransform.angleAt(gesture, vector.first, vector.second, 1))
                            }
                            return@detectDragGestures
                        }
                        if (partHeightHandleActive && ids.size == 2) {
                            val assembly = assemblies.firstOrNull { it.id == ids[0] }
                            val part = assembly?.parts?.firstOrNull { it.id == ids[1] }
                            if (assembly != null && part != null) {
                                val gesture = partHeightGesture
                                val maxHeight = (room.heightMm-assembly.position.z-part.localPosition.z).coerceAtLeast(1)
                                if (gesture != null) {
                                    val target = ru.mebel3d.app.core.AssemblyPartGestureTransform.heightAt(gesture, dragPointer.y-dragStartPointer.y, scale, true, maxHeightMm = maxHeight, cameraPitchDeg = cameraPitch)
                                    onChangeSelectedPartHeight(ids[0], ids[1], target)
                                }
                            }
                            return@detectDragGestures
                        }
                        val yaw = cameraYaw * PI.toFloat() / 180f
                        val worldDx: Float
                        val worldDy: Float
                        if (is3d) {
                            val horizontal = dragAmount.x / (0.58f * scale)
                            val vertical = dragAmount.y / (0.58f * scale * kotlin.math.sin(cameraPitch * PI.toFloat() / 180f).coerceAtLeast(0.08f))
                            worldDx = horizontal * cos(yaw) + vertical * sin(yaw)
                            worldDy = -horizontal * sin(yaw) + vertical * cos(yaw)
                        } else {
                            worldDx = dragAmount.x / scale
                            worldDy = dragAmount.y / scale
                        }
                        if (resizeHandleActive) {
                            val part = assemblies.firstOrNull { it.id == ids[0] }?.parts?.firstOrNull { it.id == ids[1] }
                            val partAngle = (part?.rotationDeg ?: 0) * PI.toFloat() / 180f
                            val localDx = worldDx * cos(partAngle) + worldDy * sin(partAngle)
                            val localDy = -worldDx * sin(partAngle) + worldDy * cos(partAngle)
                            onResizeSelectedPart(ids[0], ids[1], localDx.toInt(), localDy.toInt())
                        } else onMoveSelectedPart(ids[0], ids[1], worldDx.toInt(), worldDy.toInt())
                    } else if (selectedId != null) {
                        val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                        if (furnitureResizeHandleActive) {
                            val item = furniture.firstOrNull { it.id == selectedId }
                            if (item != null) {
                                val (dw, dd) = ru.mebel3d.app.core.FurnitureResizeMath.fromScreenDelta(
                                    dragAmount.x, dragAmount.y, scale, is3d, cameraYaw, item.rotationDeg, cameraPitch
                                )
                                onResizeSelectedFurniture(dw, dd)
                            }
                            return@detectDragGestures
                        }
                        val dxMm: Float
                        val dyMm: Float
                        if (is3d) {
                            val horizontal = dragAmount.x / (0.58f * scale)
                            val vertical = dragAmount.y / (0.58f * scale * kotlin.math.sin(cameraPitch * PI.toFloat() / 180f).coerceAtLeast(0.08f))
                            val yaw = cameraYaw * PI.toFloat() / 180f
                            dxMm = horizontal * cos(yaw) + vertical * sin(yaw)
                            dyMm = -horizontal * sin(yaw) + vertical * cos(yaw)
                        } else {
                            dxMm = dragAmount.x / scale
                            dyMm = dragAmount.y / scale
                        }
                        onMoveSelected(dxMm.toInt(), dyMm.toInt())
                    }
                }
            }
            .pointerInput(cameraYaw, cameraPitch, cameraZoom, is3d) {
                detectTransformGestures { _, pan, zoomChange, rotationChange ->
                    if (is3d && (zoomChange != 1f || kotlin.math.abs(rotationChange) > 0.01f || kotlin.math.abs(pan.x) > 0.5f || kotlin.math.abs(pan.y) > 0.5f)) {
                        val nextYaw = (cameraYaw + rotationChange + pan.x * 0.12f + 360f) % 360f
                        val nextPitch = (cameraPitch - pan.y * 0.08f).coerceIn(5f, 85f)
                        val nextZoom = (cameraZoom * zoomChange).coerceIn(0.55f, 2.4f)
                        onCameraChange(nextYaw, nextPitch, nextZoom)
                    }
                }
            }
            .pointerInput(room, furniture, is3d, cameraYaw, cameraPitch, cameraZoom) {
            detectTapGestures { tap ->
                val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
                val cx = size.width * 0.5f
                val cy = size.height * if (is3d) 0.58f else 0.5f
                val candidates = mutableListOf<ru.mebel3d.app.core.SceneHit>()
                val yaw = cameraYaw * PI.toFloat() / 180f
                fun projectHit(x: Float, y: Float, z: Float): Offset {
                    if (!is3d) return Offset(cx-room.widthMm*scale*0.5f+x*scale, cy-room.depthMm*scale*0.5f+y*scale)
                    val dx = x-room.widthMm/2f; val dy = y-room.depthMm/2f
                    val rx = dx*cos(yaw)-dy*sin(yaw); val ry = dx*sin(yaw)+dy*cos(yaw)
                    return Offset(cx+rx*scale*0.58f, cy+ry*scale*0.58f*kotlin.math.sin(cameraPitch * PI.toFloat() / 180f)-z*scale*0.58f*kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
                }
                fun addBoxHit(id: String, x: Float, y: Float, z: Float, width: Float, depth: Float, height: Float, rotationDeg: Int = 0) {
                    val angle = rotationDeg * PI.toFloat() / 180f
                    val corners = listOf(-width/2f to -depth/2f, width/2f to -depth/2f,
                        width/2f to depth/2f, -width/2f to depth/2f)
                    val points = corners.flatMap { (lx, ly) ->
                        val rx = lx*cos(angle)-ly*sin(angle); val ry = lx*sin(angle)+ly*cos(angle)
                        listOf(projectHit(x+rx, y+ry, z), projectHit(x+rx, y+ry, z+height))
                    }
                    val rect = SceneHitTesting.bounds(points.map { it.x to it.y }, paddingPx = 7.dp.toPx())
                    if (rect != null) candidates += SceneHitTesting.score(id, tap.x, tap.y, rect)
                }
                furniture.forEach { item ->
                    // FurnitureItem.position is its footprint centre (the renderer and validator use this convention).
                    addBoxHit(item.id, item.position.x.toFloat(), item.position.y.toFloat(),
                        item.position.z.toFloat(), item.size.width.toFloat(), item.size.depth.toFloat(), item.size.height.toFloat(), item.rotationDeg)
                }
                assemblies.forEach { assembly -> assembly.parts.forEach { part ->
                    val px = assembly.position.x + part.localPosition.x + part.size.width / 2f
                    val py = assembly.position.y + part.localPosition.y + part.size.depth / 2f
                    val pz = assembly.position.z + part.localPosition.z
                    addBoxHit("assembly-part:${assembly.id}:${part.id}", px, py, pz.toFloat(),
                        part.size.width.toFloat(), part.size.depth.toFloat(), part.size.height.toFloat(), part.rotationDeg)
                } }
                room.engineering.forEach { item ->
                    val q = projectHit(item.position.x.toFloat(), item.position.y.toFloat(), item.position.z.toFloat())
                    val rect = SceneHitTesting.bounds(listOf(q.x-12.dp.toPx() to q.y-12.dp.toPx(), q.x+12.dp.toPx() to q.y+12.dp.toPx()))!!
                    candidates += SceneHitTesting.score("engineering:${item.id}", tap.x, tap.y, rect)
                }
                room.openings.forEach { item ->
                    val corners = RoomVisualizationEngine.openingCorners(room, item) ?: return@forEach
                    val centre = Vec3Mm(
                        (corners.sumOf { it.x.toLong() } / corners.size).toInt(),
                        (corners.sumOf { it.y.toLong() } / corners.size).toInt(),
                        (corners.sumOf { it.z.toLong() } / corners.size).toInt()
                    )
                    val q = projectHit(centre.x.toFloat(), centre.y.toFloat(), centre.z.toFloat())
                    val rect = SceneHitTesting.bounds(listOf(q.x-18.dp.toPx() to q.y-18.dp.toPx(), q.x+18.dp.toPx() to q.y+18.dp.toPx()))!!
                    candidates += SceneHitTesting.score("opening:${item.id}", tap.x, tap.y, rect)
                }
                SceneHitTesting.choose(candidates, 30.dp.toPx())?.let { hitId ->
                    if (hitId.startsWith("assembly-part:")) {
                        val ids = hitId.removePrefix("assembly-part:").split(":", limit = 2)
                        if (ids.size == 2) onSelectPart(ids[0], ids[1])
                    } else onSelect(hitId)
                }
            }
        }) {
            val scale = roomSceneScale(size.width, size.height, room, cameraYaw, cameraPitch, cameraZoom, is3d)
            val cx = size.width * 0.5f
            val cy = size.height * if (is3d) 0.58f else 0.5f
            val yaw = cameraYaw * PI.toFloat() / 180f
            fun p(x: Float, y: Float, z: Float = 0f): Offset = if (is3d) {
                val dx = x - room.widthMm / 2f; val dy = y - room.depthMm / 2f
                val rx = dx * cos(yaw) - dy * sin(yaw)
                val ry = dx * sin(yaw) + dy * cos(yaw)
                Offset(cx + rx * scale * 0.58f, cy + ry * scale * 0.58f * kotlin.math.sin(cameraPitch * PI.toFloat() / 180f) - z * scale * 0.58f * kotlin.math.cos(cameraPitch * PI.toFloat() / 180f))
            } else Offset(cx - room.widthMm * scale * 0.5f + x * scale, cy - room.depthMm * scale * 0.5f + y * scale)
            fun polygon(points: List<Offset>, color: Color, stroke: Color? = null) {
                val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, color)
                if (stroke != null) drawPath(path, stroke, style = Stroke(width = 2.dp.toPx()))
            }
            val wallPattern = ru.mebel3d.app.core.InteriorMaterialLibrary.find(wallMaterialId ?: "")?.pattern
                ?: ru.mebel3d.app.core.SurfacePattern.SOLID
            val floorPattern = ru.mebel3d.app.core.InteriorMaterialLibrary.find(floorMaterialId ?: "")?.pattern
                ?: ru.mebel3d.app.core.SurfacePattern.SOLID
            val ceilingPattern = ru.mebel3d.app.core.InteriorMaterialLibrary.find(ceilingMaterialId ?: "")?.pattern
                ?: ru.mebel3d.app.core.SurfacePattern.SOLID
            fun mix(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x-a.x)*t, a.y + (b.y-a.y)*t)
            val sceneAmbient = (0.58f + lightIntensityPercent.coerceIn(0, 100) / 100f * 0.42f)
            val temperature = lightTemperatureKelvin.coerceIn(2700, 6500)
            fun illuminate(color: Color): Color {
                val warm = ((4200 - temperature).coerceAtLeast(0) / 1500f * 0.055f)
                val cool = ((temperature - 4200).coerceAtLeast(0) / 2300f * 0.055f)
                return color.copy(
                    red = (color.red * sceneAmbient + warm).coerceIn(0f, 1f),
                    green = (color.green * sceneAmbient).coerceIn(0f, 1f),
                    blue = (color.blue * sceneAmbient + cool).coerceIn(0f, 1f)
                )
            }
            fun surfacePattern(points: List<Offset>, pattern: ru.mebel3d.app.core.SurfacePattern, alpha: Float = 1f) {
                if (points.size != 4 || pattern == ru.mebel3d.app.core.SurfacePattern.SOLID ||
                    pattern == ru.mebel3d.app.core.SurfacePattern.TRANSPARENT) return
                val tint = Color(0xFF58636D).copy(alpha = (0.22f * alpha).coerceIn(0f, 0.22f))
                when (pattern) {
                    ru.mebel3d.app.core.SurfacePattern.TILE -> {
                        for (i in 1..5) {
                            val t = i / 6f
                            drawLine(tint, mix(points[0], points[1], t), mix(points[3], points[2], t), 1.dp.toPx())
                            drawLine(tint, mix(points[0], points[3], t), mix(points[1], points[2], t), 1.dp.toPx())
                        }
                    }
                    ru.mebel3d.app.core.SurfacePattern.WOOD_GRAIN -> {
                        for (i in 1..7) {
                            val t = i / 8f
                            drawLine(tint, mix(points[0], points[3], t), mix(points[1], points[2], t), 0.8.dp.toPx())
                        }
                        for (i in 1..3) {
                            val t = i / 4f
                            val start = mix(points[0], points[1], t)
                            val end = mix(points[3], points[2], t)
                            drawLine(tint, mix(start, end, 0.42f), mix(start, end, 0.52f), 1.dp.toPx())
                        }
                    }
                    ru.mebel3d.app.core.SurfacePattern.STONE_GRAIN -> {
                        for (i in 0..2) {
                            val t = (i + 1) / 4f
                            drawLine(tint, mix(points[0], points[1], t), mix(points[3], points[2], (t + 0.12f).coerceAtMost(1f)), 1.1.dp.toPx())
                        }
                    }
                    ru.mebel3d.app.core.SurfacePattern.BRUSHED -> {
                        for (i in 1..8) {
                            val t = i / 9f
                            drawLine(tint, mix(points[0], points[1], t), mix(points[3], points[2], t), 0.6.dp.toPx())
                        }
                    }
                    else -> Unit
                }
            }
            val w = room.widthMm.toFloat(); val d = room.depthMm.toFloat(); val h = room.heightMm.toFloat()
            if (is3d) {
                val floorQuad = listOf(p(0f,0f), p(w,0f), p(w,d), p(0f,d))
                polygon(floorQuad, illuminate(Color(floorColorArgb.toInt())), Color(0xFF8995A5))
                surfacePattern(floorQuad, floorPattern)
                val wallMode = when (wallDisplay) {
                    "Прозрачные" -> WallDisplayMode.ALL_TRANSPARENT
                    "Ближние" -> WallDisplayMode.SOLID_NEAR
                    else -> WallDisplayMode.CUTAWAY
                }
                val wallVisuals = RoomVisualizationEngine.wallVisuals(
                    RoomVisualizationEngine.preset(ru.mebel3d.app.core.RoomViewPreset.ISOMETRIC).copy(yawDeg = cameraYaw), wallMode
                ).associateBy { it.side }
                fun drawWall(side: WallSide, wallLengthMm: Int, materialId: String?, wallPoint: (Int, Float) -> Offset) {
                    val visual = wallVisuals[side] ?: return
                    if (!visual.visible || visual.opacity <= 0f) return
                    val openings = room.openings.filter { it.side == side }
                    val panels = RoomVisualizationEngine.wallPanels(wallLengthMm, room.heightMm, openings)
                    val wallMaterial = ru.mebel3d.app.core.InteriorMaterialLibrary.find(materialId ?: wallMaterialId ?: "")
                    val wallFill = wallMaterial?.argb ?: wallColorArgb
                    val sidePattern = wallMaterial?.pattern ?: wallPattern
                    val fill = illuminate(Color(wallFill.toInt())).copy(alpha = visual.opacity)
                    val outline = Color(0xFF8995A5).copy(alpha = visual.opacity.coerceAtLeast(0.35f))
                    panels.forEach { panel ->
                        val a = panel.offsetMm
                        val b = panel.offsetMm + panel.widthMm
                        val bottom = panel.bottomMm.toFloat()
                        val top = (panel.bottomMm + panel.heightMm).toFloat()
                        val wallQuad = listOf(wallPoint(a, bottom), wallPoint(b, bottom),
                            wallPoint(b, top), wallPoint(a, top))
                        polygon(wallQuad, fill, outline)
                        surfacePattern(wallQuad, sidePattern, visual.opacity)
                    }
                }
                // Build each wall from solid fragments. Door/window rectangles are true gaps,
                // not opaque tinted patches painted over an uninterrupted wall polygon.
                drawWall(WallSide.NORTH, room.widthMm, northWallMaterialId) { offset, z -> p(offset.toFloat(), 0f, z) }
                drawWall(WallSide.SOUTH, room.widthMm, southWallMaterialId) { offset, z -> p(offset.toFloat(), d, z) }
                drawWall(WallSide.WEST, room.depthMm, westWallMaterialId) { offset, z -> p(0f, offset.toFloat(), z) }
                drawWall(WallSide.EAST, room.depthMm, eastWallMaterialId) { offset, z -> p(w, offset.toFloat(), z) }
                if (showCeiling) {
                    val ceilingQuad = listOf(p(0f,0f,h), p(w,0f,h), p(w,d,h), p(0f,d,h))
                    polygon(ceilingQuad, illuminate(Color(ceilingColorArgb.toInt())).copy(alpha = 0.72f), Color(0xFF8995A5))
                    surfacePattern(ceilingQuad, ceilingPattern, 0.72f)
                }
            } else {
                val planFloor = listOf(p(0f,0f), p(w,0f), p(w,d), p(0f,d))
                polygon(planFloor, illuminate(Color(floorColorArgb.toInt())), Color(0xFF66758A))
                surfacePattern(planFloor, floorPattern)
                drawLine(Color(0xFF1677F8), p(0f,0f), p(w,0f), strokeWidth = 5.dp.toPx())
            }
            // Architectural openings are projected from the same millimetre model as the room.
            room.openings.forEach { opening ->
                val corners = RoomVisualizationEngine.openingCorners(room, opening) ?: return@forEach
                val pts = corners.map { p(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
                val selectedOpening = opening.id == selectedOpeningId
                val tint = if (opening.kind == OpeningKind.DOOR) Color(0xFFB8C8D8) else Color(0xFFB9E1F5)
                val edge = if (selectedOpening) Color(0xFF075FCB) else Color(0xFF55738F)
                // The wall itself is absent here. A very low-opacity leaf/glazing hint must not
                // visually seal the cutout or hide furniture visible through the opening.
                polygon(pts, tint.copy(alpha = RoomVisualizationEngine.openingFillOpacity(opening.kind, selectedOpening)))
                pts.indices.forEach { index ->
                    drawLine(edge, pts[index], pts[(index + 1) % pts.size], strokeWidth = if (selectedOpening) 2.8.dp.toPx() else 1.8.dp.toPx())
                }
                if (opening.kind == OpeningKind.WINDOW) {
                    val leftMid = Offset((pts[0].x + pts[3].x) / 2f, (pts[0].y + pts[3].y) / 2f)
                    val rightMid = Offset((pts[1].x + pts[2].x) / 2f, (pts[1].y + pts[2].y) / 2f)
                    val bottomMid = Offset((pts[0].x + pts[1].x) / 2f, (pts[0].y + pts[1].y) / 2f)
                    val topMid = Offset((pts[3].x + pts[2].x) / 2f, (pts[3].y + pts[2].y) / 2f)
                    drawLine(edge.copy(alpha = 0.8f), leftMid, rightMid, strokeWidth = 1.2.dp.toPx())
                    drawLine(edge.copy(alpha = 0.8f), bottomMid, topMid, strokeWidth = 1.2.dp.toPx())
                } else {
                    // A restrained diagonal indicates a door leaf without filling the doorway.
                    drawLine(edge.copy(alpha = 0.55f), pts[0], pts[2], strokeWidth = 1.1.dp.toPx())
                }
            }
            // Wall-mounted engineering items are rendered as upright plaques on the actual
            // wall plane. Their hit target remains centred on the same canonical room point.
            room.engineering.forEach { element ->
                val center = p(element.position.x.toFloat(), element.position.y.toFloat(), element.position.z.toFloat())
                val marker = when (element.kind.name) {
                    "GAS" -> Color(0xFFD97706)
                    "WATER", "DRAIN" -> Color(0xFF1688C7)
                    "OUTLET", "SWITCH" -> Color(0xFF7C5CC4)
                    else -> Color(0xFF39865B)
                }
                val selected = element.id == selectedEngineeringId
                val corners = if (is3d) RoomVisualizationEngine.engineeringMarkerCorners(room, element) else null
                if (corners != null) {
                    val plaque = corners.map { p(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }
                    polygon(plaque, Color.White.copy(alpha = 0.94f), if (selected) Color(0xFF075FCB) else marker)
                    val tangentStart = Offset((plaque[0].x + plaque[3].x) / 2f, (plaque[0].y + plaque[3].y) / 2f)
                    val tangentEnd = Offset((plaque[1].x + plaque[2].x) / 2f, (plaque[1].y + plaque[2].y) / 2f)
                    val verticalStart = Offset((plaque[0].x + plaque[1].x) / 2f, (plaque[0].y + plaque[1].y) / 2f)
                    val verticalEnd = Offset((plaque[2].x + plaque[3].x) / 2f, (plaque[2].y + plaque[3].y) / 2f)
                    val mid = Offset((center.x + (tangentStart.x + tangentEnd.x) / 2f) / 2f,
                        (center.y + (verticalStart.y + verticalEnd.y) / 2f) / 2f)
                    drawCircle(marker, radius = 3.8.dp.toPx(), center = mid)
                    drawLine(marker.copy(alpha = 0.8f), tangentStart, tangentEnd, strokeWidth = 1.dp.toPx())
                } else {
                    drawCircle(if (selected) Color(0xFF075FCB) else Color.White, radius = if (selected) 10.dp.toPx() else 7.dp.toPx(), center = center)
                    drawCircle(marker, radius = 5.dp.toPx(), center = center)
                }
            }
            val painterCamera = ru.mebel3d.app.core.RoomCameraState(
                yawDeg = cameraYaw, pitchDeg = cameraPitch, zoom = cameraZoom
            )
            val painterFurniture = if (is3d) ru.mebel3d.app.core.RoomVisualizationEngine
                .furnitureBackToFront(room, painterCamera, furniture) else furniture
            painterFurniture.forEach { item ->
                // Rotate every procedural face/detail around the furniture centre, not just its hitbox.
                val x = item.position.x.toFloat(); val y = item.position.y.toFloat()
                val hw = item.size.width / 2f; val hd = item.size.depth / 2f
                fun ffp(px: Float, py: Float, pz: Float = 0f): Offset {
                    // The procedural faces are authored in room coordinates; convert them
                    // to the item's local [0..width] x [0..depth] frame before rotation.
                    val world = ru.mebel3d.app.core.FurnitureSceneTransform.point(
                        item, px - (x - hw), py - (y - hd), pz
                    )
                    return p(world.x, world.y, world.z)
                }
                val z = item.position.z.toFloat(); val top = z + item.size.height
                val selected = item.id == selectedId
                val baseMaterial = Color(item.colorArgb.toInt())
                val warmLight = lightTemperatureKelvin < 3800 || (lightTemperatureKelvin == 4200 && lightingPreset == "Тёплый")
                val coolLight = lightTemperatureKelvin > 5000 || (lightTemperatureKelvin == 4200 && lightingPreset == "Нейтральный")
                val ambient = (0.58f + lightIntensityPercent.coerceIn(0, 100) / 100f * 0.42f)
                val warmTint = if (warmLight) 0.035f else 0f
                val coolTint = if (coolLight) 0.035f else 0f
                val litMaterial = baseMaterial.copy(
                    red = (baseMaterial.red * ambient + warmTint).coerceIn(0f, 1f),
                    green = (baseMaterial.green * ambient).coerceIn(0f, 1f),
                    blue = (baseMaterial.blue * ambient + coolTint).coerceIn(0f, 1f)
                )
                val fill = if (selected) Color(0xFF9CC8FF) else litMaterial
                val sideShade = (0.90f - shadowStrengthPercent.coerceIn(0, 100) / 100f * 0.24f).coerceIn(0.55f, 0.95f)
                if (is3d) {
                    val a=ffp(x-hw,y-hd,z); val b=ffp(x+hw,y-hd,z); val c=ffp(x+hw,y+hd,z); val e=ffp(x-hw,y+hd,z)
                    val at=ffp(x-hw,y-hd,top); val bt=ffp(x+hw,y-hd,top); val ct=ffp(x+hw,y+hd,top); val et=ffp(x-hw,y+hd,top)
                    polygon(listOf(a,b,bt,at), if(selected) Color(0xFF7FB5F5) else litMaterial.copy(red = (litMaterial.red * if (warmLight) 1.02f else 0.94f).coerceIn(0f,1f), green = (litMaterial.green * sideShade).coerceIn(0f,1f), blue = (litMaterial.blue * if (coolLight) 0.98f else sideShade).coerceIn(0f,1f)), if(selected) Color(0xFF1677F8) else Color(0xFF8A6A4C))
                    polygon(listOf(b,c,ct,bt), if(selected) Color(0xFF6FA6E8) else litMaterial.copy(red = (litMaterial.red * sideShade * 0.90f).coerceIn(0f,1f), green = (litMaterial.green * sideShade * 0.92f).coerceIn(0f,1f), blue = (litMaterial.blue * sideShade).coerceIn(0f,1f)), if(selected) Color(0xFF1677F8) else Color(0xFF8A6A4C))
                    polygon(listOf(at,bt,ct,et), fill, if(selected) Color(0xFF075FCB) else Color(0xFF8A6A4C))
                } else {
                    polygon(listOf(ffp(x-hw,y-hd),ffp(x+hw,y-hd),ffp(x+hw,y+hd),ffp(x-hw,y+hd)), fill, if(selected) Color(0xFF075FCB) else Color(0xFF8A6A4C))
                }
                // Category-specific procedural details make catalog objects recognizable in the room,
                // while retaining one editable millimetre footprint and one selectable object.
                if (is3d) {
                    val edge = if (selected) Color(0xFF075FCB) else Color(0xFF806B58)
                    val soft = Color(0xFFE7D8C8)
                    val dark = Color(0xFF8A7765)
                    val n = item.name.lowercase()
                    when {
                        n.contains("диван") || n.contains("кресло") -> {
                            val seatZ = z + item.size.height * 0.38f
                            val backZ = z + item.size.height * 0.88f
                            val backDepth = y - hd + item.size.depth * 0.16f
                            polygon(listOf(ffp(x-hw+item.size.width*.04f, backDepth, seatZ), ffp(x+hw-item.size.width*.04f, backDepth, seatZ), ffp(x+hw-item.size.width*.04f, backDepth, backZ), ffp(x-hw+item.size.width*.04f, backDepth, backZ)), baseMaterial.copy(red=(baseMaterial.red*.88f).coerceIn(0f,1f), green=(baseMaterial.green*.9f).coerceIn(0f,1f), blue=(baseMaterial.blue*.92f).coerceIn(0f,1f)), edge)
                            polygon(listOf(ffp(x-hw+item.size.width*.08f,y-hd+item.size.depth*.18f,seatZ), ffp(x+hw-item.size.width*.08f,y-hd+item.size.depth*.18f,seatZ), ffp(x+hw-item.size.width*.08f,y+hd-item.size.depth*.08f,seatZ), ffp(x-hw+item.size.width*.08f,y+hd-item.size.depth*.08f,seatZ)), soft, edge)
                            val armW = item.size.width*.12f
                            listOf(x-hw+armW/2f, x+hw-armW/2f).forEach { ax ->
                                polygon(listOf(ffp(ax-armW/2,y-hd+item.size.depth*.12f,seatZ),ffp(ax+armW/2,y-hd+item.size.depth*.12f,seatZ),ffp(ax+armW/2,y+hd-item.size.depth*.08f,seatZ),ffp(ax-armW/2,y+hd-item.size.depth*.08f,seatZ)), baseMaterial.copy(red=(baseMaterial.red*.82f).coerceIn(0f,1f),green=(baseMaterial.green*.82f).coerceIn(0f,1f),blue=(baseMaterial.blue*.82f).coerceIn(0f,1f)), edge)
                            }
                        }
                        n.contains("стол") || n.contains("тумба") || n.contains("комод") || n.contains("шкаф") || n.contains("пенал") || n.contains("холодильник") -> {
                            val frontY = y + hd
                            val doorCount = if (item.size.width > 1000) 2 else 1
                            for (i in 0 until doorCount) {
                                val left = x-hw + item.size.width*i/doorCount.toFloat() + item.size.width*.035f
                                val right = x-hw + item.size.width*(i+1)/doorCount.toFloat() - item.size.width*.035f
                                polygon(listOf(ffp(left,frontY,z+item.size.height*.06f),ffp(right,frontY,z+item.size.height*.06f),ffp(right,frontY,top-item.size.height*.05f),ffp(left,frontY,top-item.size.height*.05f)), baseMaterial.copy(red=(baseMaterial.red*.94f).coerceIn(0f,1f),green=(baseMaterial.green*.94f).coerceIn(0f,1f),blue=(baseMaterial.blue*.94f).coerceIn(0f,1f)), edge)
                                val handleX = if (doorCount == 1) right-item.size.width*.07f else (left+right)/2f
                                val handleBottom = ffp(handleX,frontY+2f,z+item.size.height*.48f)
                                val handleTop = ffp(handleX,frontY+2f,z+item.size.height*.62f)
                                drawLine(dark, handleBottom, handleTop, strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                            if (n.contains("стол") || n.contains("тумба")) {
                                val topZ = z + item.size.height*.88f
                                polygon(listOf(ffp(x-hw,y-hd,topZ),ffp(x+hw,y-hd,topZ),ffp(x+hw,y+hd,topZ),ffp(x-hw,y+hd,topZ)), baseMaterial.copy(red=(baseMaterial.red*1.08f).coerceIn(0f,1f),green=(baseMaterial.green*1.08f).coerceIn(0f,1f),blue=(baseMaterial.blue*1.08f).coerceIn(0f,1f)), edge)
                            }
                        }
                        n.contains("кровать") -> {
                            val mattressZ = z + item.size.height*.62f
                            polygon(listOf(ffp(x-hw+item.size.width*.035f,y-hd+item.size.depth*.04f,mattressZ),ffp(x+hw-item.size.width*.035f,y-hd+item.size.depth*.04f,mattressZ),ffp(x+hw-item.size.width*.035f,y+hd-item.size.depth*.04f,mattressZ),ffp(x-hw+item.size.width*.035f,y+hd-item.size.depth*.04f,mattressZ)), Color(0xFFF3F0E9), edge)
                            val pillowW = item.size.width*.32f
                            for (side in 0..1) {
                                val px = x-hw+item.size.width*.12f+side*(item.size.width*.43f)
                                polygon(listOf(ffp(px,y-hd+item.size.depth*.07f,mattressZ+8),ffp(px+pillowW,y-hd+item.size.depth*.07f,mattressZ+8),ffp(px+pillowW,y-hd+item.size.depth*.24f,mattressZ+8),ffp(px,y-hd+item.size.depth*.24f,mattressZ+8)), Color(0xFFFFFFFF), Color(0xFFD0C8BE))
                            }
                            polygon(listOf(ffp(x-hw,y-hd,z+item.size.height*.82f),ffp(x+hw,y-hd,z+item.size.height*.82f),ffp(x+hw,y-hd,top),ffp(x-hw,y-hd,top)), baseMaterial, edge)
                        }
                        n.contains("стул") -> {
                            val legW = item.size.width*.07f
                            listOf(x-hw+legW,x+hw-legW).forEach { lx -> listOf(y-hd+legW,y+hd-legW).forEach { ly ->
                                polygon(listOf(ffp(lx,ly,z),ffp(lx+legW,ly,z),ffp(lx+legW,ly,z+item.size.height*.72f),ffp(lx,ly,z+item.size.height*.72f)), dark, edge)
                            } }
                            val seatZ=z+item.size.height*.72f
                            polygon(listOf(ffp(x-hw,y-hd,seatZ),ffp(x+hw,y-hd,seatZ),ffp(x+hw,y+hd,seatZ),ffp(x-hw,y+hd,seatZ)), baseMaterial, edge)
                            polygon(listOf(ffp(x-hw,y-hd,z+item.size.height*.76f),ffp(x+hw,y-hd,z+item.size.height*.76f),ffp(x+hw,y-hd,top),ffp(x-hw,y-hd,top)), baseMaterial, edge)
                        }
                        n.contains("ковёр") -> {
                            polygon(listOf(ffp(x-hw,y-hd,z+8f),ffp(x+hw,y-hd,z+8f),ffp(x+hw,y+hd,z+8f),ffp(x-hw,y+hd,z+8f)), Color(0xFFD8C5AE), Color(0xFF9C8064))
                            polygon(listOf(ffp(x-hw*.88f,y-hd*.86f,z+10f),ffp(x+hw*.88f,y-hd*.86f,z+10f),ffp(x+hw*.88f,y+hd*.86f,z+10f),ffp(x-hw*.88f,y+hd*.86f,z+10f)), Color(0xFFE9DDCF), Color(0xFFC4AE96))
                        }
                        n.contains("зеркало") -> {
                            polygon(listOf(ffp(x-hw,y+hd,z),ffp(x+hw,y+hd,z),ffp(x+hw,y+hd,top),ffp(x-hw,y+hd,top)), Color(0xFFB9D4E1), Color(0xFF718C9A))
                            drawLine(Color(0xFFEAF6FA), ffp(x-hw*.65f,y+hd+3f,z+item.size.height*.18f), ffp(x-hw*.15f,y+hd+3f,z+item.size.height*.78f), strokeWidth = 2.dp.toPx())
                        }
                        n.contains("торшер") || n.contains("люстра") -> {
                            val stemX=x; val stemY=y
                            polygon(listOf(ffp(stemX-item.size.width*.035f,stemY,z),ffp(stemX+item.size.width*.035f,stemY,z),ffp(stemX+item.size.width*.035f,stemY,z+item.size.height*.82f),ffp(stemX-item.size.width*.035f,stemY,z+item.size.height*.82f)), dark, edge)
                            val shadeZ=z+item.size.height*.78f
                            polygon(listOf(ffp(x-hw*.48f,y-hd*.48f,shadeZ),ffp(x+hw*.48f,y-hd*.48f,shadeZ),ffp(x+hw*.32f,y+hd*.32f,top),ffp(x-hw*.32f,y+hd*.32f,top)), Color(0xFFFFE7B0), Color(0xFFB99A62))
                        }
                        n.contains("ванна") || n.contains("душ") || n.contains("раков") || n.contains("унитаз") -> {
                            val rimZ = z+item.size.height*.82f
                            polygon(listOf(ffp(x-hw*.9f,y-hd*.9f,rimZ),ffp(x+hw*.9f,y-hd*.9f,rimZ),ffp(x+hw*.9f,y+hd*.9f,rimZ),ffp(x-hw*.9f,y+hd*.9f,rimZ)), Color(0xFFF8FBFC), Color(0xFF8FA8B5))
                            polygon(listOf(ffp(x-hw*.72f,y-hd*.72f,rimZ+2),ffp(x+hw*.72f,y-hd*.72f,rimZ+2),ffp(x+hw*.72f,y+hd*.72f,rimZ+2),ffp(x-hw*.72f,y+hd*.72f,rimZ+2)), Color(0xFFBFE3EF), Color(0xFF8FA8B5))
                        }
                    }
                }
            }
            // Live dimension/clearance overlay for the selected furniture. Values stay in room-local mm.
            val measuredItem = furniture.firstOrNull { it.id == selectedId }
            if (measuredItem != null) {
                val angle = measuredItem.rotationDeg * PI.toFloat() / 180f
                val localX = measuredItem.size.width / 2f
                val localY = measuredItem.size.depth / 2f
                val handleX = measuredItem.position.x + localX*cos(angle) - localY*sin(angle)
                val handleY = measuredItem.position.y + localX*sin(angle) + localY*cos(angle)
                val handle = p(handleX, handleY, (measuredItem.position.z + measuredItem.size.height).toFloat())
                drawCircle(Color(0xFF075FCB), radius = 10.dp.toPx(), center = handle)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = handle)
                val center = p(measuredItem.position.x.toFloat(), measuredItem.position.y.toFloat(), (measuredItem.position.z + measuredItem.size.height/2f).toFloat())
                val rotate = center + Offset(38.dp.toPx(), -28.dp.toPx())
                val height = p(measuredItem.position.x.toFloat(), measuredItem.position.y.toFloat(), (measuredItem.position.z + measuredItem.size.height).toFloat())
                drawLine(Color(0xFF16865B), center, rotate, strokeWidth = 1.5.dp.toPx())
                drawCircle(Color(0xFF16865B), radius = 10.dp.toPx(), center = rotate)
                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = rotate)
                if (is3d) {
                    drawLine(Color(0xFFB7791F), center, height, strokeWidth = 1.5.dp.toPx())
                    drawCircle(Color(0xFFB7791F), radius = 9.dp.toPx(), center = height)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = height)
                }
            }
            if (measuredItem != null && measurementGuides.isNotEmpty()) {
                val item = measuredItem
                val (footW, footD) = ru.mebel3d.app.core.GeometryEngine.rotatedFootprint(item.size, item.rotationDeg)
                val centerX = item.position.x.toFloat()
                val centerY = item.position.y.toFloat()
                val overlayZ = if (is3d) (item.position.z + 35).toFloat() else 0f
                val guideColor = Color(0xFF075FCB)
                val textPaint = Paint().apply {
                    color = android.graphics.Color.rgb(7, 95, 203)
                    textSize = 10.dp.toPx()
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                measurementGuides.take(5).forEach { guide ->
                    val start: Offset
                    val end: Offset
                    if (guide.axis == "X") {
                        start = p(guide.fromMm.toFloat(), centerY, overlayZ)
                        end = p(guide.toMm.toFloat(), centerY, overlayZ)
                    } else {
                        start = p(centerX, guide.fromMm.toFloat(), overlayZ)
                        end = p(centerX, guide.toMm.toFloat(), overlayZ)
                    }
                    drawLine(guideColor.copy(alpha = 0.82f), start, end, strokeWidth = 1.5.dp.toPx())
                    val tick = 4.dp.toPx()
                    drawLine(guideColor, Offset(start.x - tick, start.y - tick), Offset(start.x + tick, start.y + tick), strokeWidth = 1.dp.toPx())
                    drawLine(guideColor, Offset(end.x - tick, end.y - tick), Offset(end.x + tick, end.y + tick), strokeWidth = 1.dp.toPx())
                    val label = "${guide.distanceMm} мм"
                    val midX = (start.x + end.x) / 2f
                    val midY = (start.y + end.y) / 2f - 5.dp.toPx()
                    drawIntoCanvas { canvas ->
                        val measured = textPaint.measureText(label)
                        canvas.nativeCanvas.drawText(label, midX - measured / 2f, midY, textPaint)
                    }
                }
                // The selected footprint itself is dimensioned independently from wall clearances.
                val widthA = p(centerX - footW / 2f, centerY + footD / 2f + 100f, overlayZ)
                val widthB = p(centerX + footW / 2f, centerY + footD / 2f + 100f, overlayZ)
                val depthA = p(centerX + footW / 2f + 100f, centerY - footD / 2f, overlayZ)
                val depthB = p(centerX + footW / 2f + 100f, centerY + footD / 2f, overlayZ)
                drawLine(guideColor, widthA, widthB, strokeWidth = 1.5.dp.toPx())
                drawLine(guideColor, depthA, depthB, strokeWidth = 1.5.dp.toPx())
                drawIntoCanvas { canvas ->
                    val wLabel = "${footW} мм"
                    val dLabel = "${footD} мм"
                    canvas.nativeCanvas.drawText(wLabel, (widthA.x + widthB.x) / 2f - textPaint.measureText(wLabel) / 2f, (widthA.y + widthB.y) / 2f - 5.dp.toPx(), textPaint)
                    canvas.nativeCanvas.drawText(dLabel, (depthA.x + depthB.x) / 2f + 4.dp.toPx(), (depthA.y + depthB.y) / 2f, textPaint)
                }
            }
            // Individual cabinet boards are rendered above the room and can be selected independently.
            val painterParts = assemblies.flatMap { assembly -> assembly.parts.map { part -> assembly to part } }
                .let { parts -> if (is3d) parts.sortedWith(
                    compareBy<Pair<FurnitureAssembly, FurniturePart>> { (assembly, part) ->
                        ru.mebel3d.app.core.RoomVisualizationEngine.partDepth(room, painterCamera, assembly, part)
                    }.thenBy { it.first.id }.thenBy { it.second.id }
                ) else parts }
            painterParts.forEach { (assembly, part) ->
                val x = (assembly.position.x + part.localPosition.x).toFloat()
                val y = (assembly.position.y + part.localPosition.y).toFloat()
                val z = (assembly.position.z + part.localPosition.z).toFloat()
                val top = z + part.size.height
                val hw = part.size.width / 2f
                val hd = part.size.depth / 2f
                val centerX = x + hw
                val centerY = y + hd
                val partAngle = part.rotationDeg * PI.toFloat() / 180f
                fun partPoint(px: Float, py: Float, pz: Float): Offset {
                    val lx = px-centerX; val ly = py-centerY
                    val rx = centerX + lx*cos(partAngle) - ly*sin(partAngle)
                    val ry = centerY + lx*sin(partAngle) + ly*cos(partAngle)
                    return p(rx, ry, pz)
                }
                val c00 = partPoint(x, y, z)
                val c10 = partPoint(x + part.size.width, y, z)
                val c11 = partPoint(x + part.size.width, y + part.size.depth, z)
                val c01 = partPoint(x, y + part.size.depth, z)
                val t00 = partPoint(x, y, top)
                val t10 = partPoint(x + part.size.width, y, top)
                val t11 = partPoint(x + part.size.width, y + part.size.depth, top)
                val t01 = partPoint(x, y + part.size.depth, top)
                val key = "${assembly.id}:${part.id}"
                val chosen = key == selectedPartKey
                val fillPart = if (chosen) Color(0xFF8BC5FF) else Color(0xFFD6B995)
                if (is3d) {
                    polygon(listOf(c00, c10, t10, t00), fillPart, if (chosen) Color(0xFF075FCB) else Color(0xFF927A5C))
                    polygon(listOf(c10, c11, t11, t10), if (chosen) Color(0xFF75B2F2) else Color(0xFFC5A982), if (chosen) Color(0xFF075FCB) else Color(0xFF927A5C))
                    polygon(listOf(t00, t10, t11, t01), fillPart, if (chosen) Color(0xFF075FCB) else Color(0xFF927A5C))
                } else {
                    polygon(listOf(c00, c10, c11, c01), fillPart, if (chosen) Color(0xFF075FCB) else Color(0xFF927A5C))
                }
                if (chosen) {
                    val handle = partPoint(x + part.size.width, y + part.size.depth, top)
                    drawCircle(Color(0xFF075FCB), radius = 7.dp.toPx(), center = handle)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = handle)
                    val partCenter = p(centerX, centerY, z + part.size.height / 2f)
                    val rotationHandle = partCenter + Offset(38.dp.toPx(), -28.dp.toPx())
                    drawLine(Color(0xFF16865B), partCenter, rotationHandle, strokeWidth = 2.dp.toPx())
                    drawCircle(Color(0xFF16865B), radius = 7.dp.toPx(), center = rotationHandle)
                    if (is3d) {
                        val heightHandle = p(centerX, centerY, top)
                        drawLine(Color(0xFFB7791F), p(centerX, centerY, z), heightHandle, strokeWidth = 2.dp.toPx())
                        drawCircle(Color(0xFFB7791F), radius = 7.dp.toPx(), center = heightHandle)
                    }
                    val widthA = partPoint(x, y + part.size.depth + 90f, top + 35f)
                    val widthB = partPoint(x + part.size.width, y + part.size.depth + 90f, top + 35f)
                    val depthA = partPoint(x + part.size.width + 90f, y, top + 35f)
                    val depthB = partPoint(x + part.size.width + 90f, y + part.size.depth, top + 35f)
                    val dimColor = Color(0xFF075FCB)
                    drawLine(dimColor, widthA, widthB, strokeWidth = 2.dp.toPx())
                    drawLine(dimColor, depthA, depthB, strokeWidth = 2.dp.toPx())
                    listOf(widthA, widthB, depthA, depthB).forEach { drawCircle(dimColor, 3.dp.toPx(), it) }
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply { color = android.graphics.Color.rgb(7,95,203); textSize = 11.dp.toPx(); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
                        canvas.nativeCanvas.drawText("${part.size.width} мм", (widthA.x + widthB.x) / 2f - 20.dp.toPx(), (widthA.y + widthB.y) / 2f - 5.dp.toPx(), paint)
                        canvas.nativeCanvas.drawText("${part.size.depth} мм", (depthA.x + depthB.x) / 2f + 3.dp.toPx(), (depthA.y + depthB.y) / 2f, paint)
                        canvas.nativeCanvas.drawText("${part.size.height} мм", handle.x + 8.dp.toPx(), handle.y - 8.dp.toPx(), paint)
                    }
                }
            }
        }
    }
}
