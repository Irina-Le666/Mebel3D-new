package ru.mebel3d.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.mebel3d.app.core.PhotoAnnotation
import ru.mebel3d.app.core.PhotoAnnotationKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Photo annotation editor: add, select, edit and delete marks in source-image coordinates. */
@Composable
internal fun PhotoAnnotationEditor(
    bitmap: Bitmap,
    coordinateWidth: Int = bitmap.width,
    coordinateHeight: Int = bitmap.height,
    existing: List<PhotoAnnotation>,
    onDismiss: () -> Unit,
    onAdd: (PhotoAnnotation) -> Unit,
    onUpdate: (PhotoAnnotation) -> Unit,
    onRemove: (String) -> Unit
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(PhotoAnnotationKind.WINDOW) }
    var label by remember { mutableStateOf("") }
    var frameSize by remember { mutableStateOf(Offset.Zero) }
    val sourceWidth = coordinateWidth.coerceAtLeast(1)
    val sourceHeight = coordinateHeight.coerceAtLeast(1)
    val imageRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
    val selected = existing.firstOrNull { it.id == selectedId }
    LaunchedEffect(selectedId, existing) {
        selected?.let { kind = it.kind; label = it.label }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth(0.96f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Разметка фотографии помещения", style = MaterialTheme.typography.titleMedium)
                Text("Нажми на существующую рамку, чтобы изменить или удалить отметку. Для новой отметки обведи область на фото. Снимок желательно делать прямо, без сильной перспективы.", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 430.dp).aspectRatio(imageRatio).background(Color(0xFFF0F2F5), RoundedCornerShape(8.dp))) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    SideEffect { frameSize = Offset(widthPx, heightPx) }
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        existing.forEach { mark ->
                            val l = mark.xPx.toFloat() / sourceWidth * size.width
                            val t = mark.yPx.toFloat() / sourceHeight * size.height
                            val r = (mark.xPx + mark.widthPx).toFloat() / sourceWidth * size.width
                            val b = (mark.yPx + mark.heightPx).toFloat() / sourceHeight * size.height
                            val color = if (mark.id == selectedId) Color(0xFFFF8A00) else Color(0xFF1677F8)
                            drawRect(color.copy(alpha = .16f), Offset(l,t), androidx.compose.ui.geometry.Size(r-l,b-t))
                            drawRect(color, Offset(l,t), androidx.compose.ui.geometry.Size(r-l,b-t), style = androidx.compose.ui.graphics.drawscope.Stroke(if (mark.id == selectedId) 3.dp.toPx() else 2.dp.toPx()))
                        }
                        val a = start; val b = end
                        if (a != null && b != null) {
                            val l=min(a.x,b.x); val t=min(a.y,b.y)
                            drawRect(Color(0x5530A46C), Offset(l,t), androidx.compose.ui.geometry.Size(abs(a.x-b.x),abs(a.y-b.y)))
                            drawRect(Color(0xFF16865A), Offset(l,t), androidx.compose.ui.geometry.Size(abs(a.x-b.x),abs(a.y-b.y)), style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        }
                    }
                    Box(Modifier.fillMaxSize().pointerInput(existing, frameSize) {
                        detectTapGestures { point ->
                            val px = point.x / size.width * sourceWidth
                            val py = point.y / size.height * sourceHeight
                            val hit = existing.lastOrNull { px >= it.xPx && px <= it.xPx + it.widthPx && py >= it.yPx && py <= it.yPx + it.heightPx }
                            selectedId = hit?.id
                            start = null; end = null
                        }
                    }.pointerInput(bitmap, frameSize, selectedId) {
                        detectDragGestures(onDragStart = { selectedId = null; start = it; end = it }, onDrag = { change, _ -> end = change.position })
                    })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(PhotoAnnotationKind.DOOR, PhotoAnnotationKind.WINDOW, PhotoAnnotationKind.OUTLET, PhotoAnnotationKind.SWITCH, PhotoAnnotationKind.WATER, PhotoAnnotationKind.GAS, PhotoAnnotationKind.PIPE).forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(when(k) { PhotoAnnotationKind.DOOR -> "Дверь"; PhotoAnnotationKind.WINDOW -> "Окно"; PhotoAnnotationKind.OUTLET -> "Розетка"; PhotoAnnotationKind.SWITCH -> "Выключатель"; PhotoAnnotationKind.WATER -> "Вода"; PhotoAnnotationKind.GAS -> "Газ"; else -> "Труба" }, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Подпись (необязательно)") })
                Text("Отметок на фото: ${existing.size}${if (selected != null) " · выбрана отметка" else " · новая отметка"}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) TextButton(onClick = { onRemove(selected.id); selectedId = null; label = "" }) { Text("Удалить") } else Spacer(Modifier.width(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onDismiss) { Text("Закрыть") }
                        Button(enabled = if (selected != null) true else start != null && end != null && frameSize.x > 0 && frameSize.y > 0, onClick = {
                            if (selected != null) onUpdate(selected.copy(kind = kind, label = label))
                            else {
                                val a = requireNotNull(start); val b = requireNotNull(end)
                                val left=min(a.x,b.x).coerceIn(0f,frameSize.x); val top=min(a.y,b.y).coerceIn(0f,frameSize.y)
                                val right=max(a.x,b.x).coerceIn(0f,frameSize.x); val bottom=max(a.y,b.y).coerceIn(0f,frameSize.y)
                                val x=(left/frameSize.x*sourceWidth).toInt().coerceIn(0,sourceWidth-1)
                                val y=(top/frameSize.y*sourceHeight).toInt().coerceIn(0,sourceHeight-1)
                                val w=((right-left)/frameSize.x*sourceWidth).toInt().coerceAtLeast(1).coerceAtMost(sourceWidth-x)
                                val h=((bottom-top)/frameSize.y*sourceHeight).toInt().coerceAtLeast(1).coerceAtMost(sourceHeight-y)
                                onAdd(PhotoAnnotation("mark-${System.currentTimeMillis()}",kind,x,y,w,h,label))
                                start=null; end=null; label=""
                            }
                        }) { Text(if (selected != null) "Сохранить" else "Добавить") }
                    }
                }
            }
        }
    }
}
