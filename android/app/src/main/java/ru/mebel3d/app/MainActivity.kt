package ru.mebel3d.app

import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import ru.mebel3d.app.core.FurnitureResizeMath
import ru.mebel3d.app.core.*

private val AppBg = Color(0xFFF3F6FB)
private val Panel = Color(0xFFFFFFFF)
private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF69758A)
private val Blue = Color(0xFF1677F8)
private val BlueTint = Color(0xFFE6F0FF)
private val Border = Color(0xFFDCE2ED)
private const val MAX_ROOM_PHOTO_BYTES = 25L * 1024L * 1024L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = AppBg)) { FurnitureHome() } }
    }
}

private data class CatalogItem(val name: String, val glyph: String, val category: String)
private val catalog = InteriorCatalogEngine.entries.map { entry ->
    CatalogItem(entry.name, entry.glyph, when (entry.category) {
        FurnitureRoomCategory.KITCHEN -> "Кухня"
        FurnitureRoomCategory.BEDROOM -> "Спальня"
        FurnitureRoomCategory.LIVING_ROOM -> "Гостиная"
        FurnitureRoomCategory.BATHROOM -> "Ванная"
        FurnitureRoomCategory.UTILITY -> "Хозяйственная"
    })
}

private data class FreePartTool(val name: String, val glyph: String, val size: Size3Mm, val zMm: Int = 0, val materialId: String = "wood-oak")
private val freePartTools = listOf(
    FreePartTool("Панель вертикальная", "▯", Size3Mm(600, 18, 2400)),
    FreePartTool("Панель горизонтальная", "▱", Size3Mm(600, 550, 18)),
    FreePartTool("Полка", "▬", Size3Mm(600, 500, 18), 900),
    FreePartTool("Перегородка", "▥", Size3Mm(18, 550, 900)),
    FreePartTool("Задняя стенка", "▤", Size3Mm(600, 6, 900)),
    FreePartTool("Фасад", "▭", Size3Mm(600, 18, 700), 0, "paint-matte"),
    FreePartTool("Выдвижной ящик", "▣", Size3Mm(500, 450, 160), 120),
    FreePartTool("Штанга", "━", Size3Mm(800, 25, 25), 1700, "metal-chrome"),
    FreePartTool("Цоколь", "▬", Size3Mm(600, 80, 100)),
    FreePartTool("Брусок / опора", "▰", Size3Mm(50, 50, 900))
)

@Composable
private fun FurnitureHome() {
    var activeSection by remember { mutableStateOf("Помещения") }
    var mode by remember { mutableStateOf("3D") }
    var cameraYaw by remember { mutableFloatStateOf(45f) }
    var cameraPitch by remember { mutableFloatStateOf(32f) }
    var cameraZoom by remember { mutableFloatStateOf(1f) }
    var wallDisplay by remember { mutableStateOf("Срез") }
    var showCeiling by remember { mutableStateOf(false) }
    var finishWallSide by remember { mutableStateOf(WallSide.NORTH) }
    var drawingWallSide by remember { mutableStateOf(WallSide.NORTH) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var selected by remember { mutableStateOf("Диван") }
    var status by remember { mutableStateOf("Проект: Кухня-гостиная") }
    var width by remember { mutableStateOf("2400") }
    var depth by remember { mutableStateOf("900") }
    var height by remember { mutableStateOf("850") }
    var posX by remember { mutableStateOf("1800") }
    var posY by remember { mutableStateOf("800") }
    var posZ by remember { mutableStateOf("0") }
    var angle by remember { mutableStateOf("0") }
    var showRoomSettings by remember { mutableStateOf(false) }
    var roomWidth by remember { mutableStateOf("5000") }
    var roomDepth by remember { mutableStateOf("3500") }
    var roomHeight by remember { mutableStateOf("2700") }
    var wallThickness by remember { mutableStateOf("120") }
    var openingOffset by remember { mutableStateOf("800") }
    var openingWidth by remember { mutableStateOf("900") }
    var openingHeight by remember { mutableStateOf("2100") }
    var openingSill by remember { mutableStateOf("900") }
    var openingSide by remember { mutableStateOf(WallSide.NORTH) }
    var showOpeningEditor by remember { mutableStateOf<OpeningKind?>(null) }
    var showEngineeringEditor by remember { mutableStateOf(false) }
    var engineeringKind by remember { mutableStateOf(EngineeringKind.OUTLET) }
    var engineeringName by remember { mutableStateOf("Розетка") }
    var engineeringX by remember { mutableStateOf("2500") }
    var engineeringY by remember { mutableStateOf("100") }
    var engineeringZ by remember { mutableStateOf("300") }
    var engineeringDiameter by remember { mutableStateOf("20") }
    var engineeringWall by remember { mutableStateOf<WallSide?>(WallSide.NORTH) }
    var selectedEngineeringId by remember { mutableStateOf<String?>(null) }
    var selectedOpeningId by remember { mutableStateOf<String?>(null) }
    var editingEngineeringId by remember { mutableStateOf<String?>(null) }
    var editingOpeningId by remember { mutableStateOf<String?>(null) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    var showPhotoCalibration by remember { mutableStateOf(false) }
    var photoKnownLength by remember { mutableStateOf("2000") }
    var photoMeasuredPixels by remember { mutableStateOf("1000") }
    var photoWallOriginPixels by remember { mutableStateOf("") }
    var photoFloorLinePixels by remember { mutableStateOf("") }
    var showPhotoAnnotationEditor by remember { mutableStateOf(false) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoOriginalWidth by remember { mutableIntStateOf(0) }
    var photoOriginalHeight by remember { mutableIntStateOf(0) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val compactLayout = LocalConfiguration.current.screenWidthDp < 1100
    val storage = remember { ProjectStorage(context) }
    val designVariants = remember { mutableStateListOf<RoomDesignVariant>() }
    var showVariantsDialog by remember { mutableStateOf(false) }
    var showProjectManager by remember { mutableStateOf(false) }
    var projectSummaries by remember { mutableStateOf(storage.listProjects()) }
    var newProjectName by remember { mutableStateOf("Новый проект") }
    var variantTitle by remember { mutableStateOf("Вариант ${designVariants.size + 1}") }
    var variantNote by remember { mutableStateOf("") }
    var comparisonText by remember { mutableStateOf<String?>(null) }
    var arrangementPreview by remember { mutableStateOf<RoomFurnitureArrangementPlan?>(null) }
    val restored = remember { storage.loadLast() }
    val rooms = remember { mutableStateListOf<RoomModel>().apply {
        addAll(restored?.rooms.orEmpty().ifEmpty { listOf(RoomModel(widthMm = 5000, depthMm = 3500, heightMm = 2700)) })
    } }
    var activeRoomId by remember { mutableStateOf(restored?.activeRoomId ?: rooms.first().id) }
    val room = rooms.firstOrNull { it.id == activeRoomId } ?: rooms.first()
    val placed = remember { mutableStateListOf<FurnitureItem>().apply { addAll(restored?.furniture.orEmpty()) } }
    val session = remember {
        ProjectSession(ProjectModel(restored?.id ?: "local-project", restored?.name ?: "Новый проект", rooms.toList(), placed.toList(), restored?.assemblies.orEmpty(), activeRoomId))
    }
    fun publish(next: ProjectModel) {
        runCatching {
            session.edit { next }
            rooms.clear(); rooms.addAll(session.project.rooms)
            placed.clear(); placed.addAll(session.project.furniture)
            activeRoomId = session.project.activeRoomId
            status = "Изменение записано в историю"
        }.onFailure { status = "Изменение отклонено: ${it.message ?: "проверьте проект"}" }
    }
    fun publishGesture(next: ProjectModel) {
        runCatching {
            session.editGesture { next }
            rooms.clear(); rooms.addAll(session.project.rooms)
            placed.clear(); placed.addAll(session.project.furniture)
            activeRoomId = session.project.activeRoomId
            status = "Перемещение обновлено"
        }.onFailure { status = "Перемещение отклонено: ${it.message ?: "проверьте положение"}" }
    }
    fun restoreFromHistory(project: ProjectModel) {
        rooms.clear(); rooms.addAll(project.rooms)
        placed.clear(); placed.addAll(project.furniture)
        activeRoomId = project.activeRoomId
        status = "История изменений обновлена"
    }
    LaunchedEffect(session.project.id) {
        designVariants.clear()
        designVariants.addAll(storage.loadVariants(session.project.id))
    }
    LaunchedEffect(session.project) {
        // Debounced autosave is also the authoritative saved-state transition. Mark the
        // session clean only after SharedPreferences confirms the write; failed writes
        // must remain dirty so the user can retry from the Save action.
        val snapshot = session.project
        kotlinx.coroutines.delay(500)
        runCatching {
            storage.save(snapshot)
            if (session.project == snapshot) session.markSaved()
        }.onFailure { failure ->
            status = "Автосохранение не выполнено: ${failure.message ?: "ошибка записи"}"
        }
    }
    val visibleFurniture = placed.filter { it.roomId == room.id }
    var selectedId by remember { mutableStateOf(restored?.furniture?.firstOrNull()?.id) }
    var selectedAssemblyId by remember { mutableStateOf<String?>(null) }
    var selectedAssemblyPartId by remember { mutableStateOf<String?>(null) }
    var nextObjectId by remember { mutableIntStateOf(1) }
    val selectedItem = placed.firstOrNull { it.id == selectedId && it.roomId == room.id }

    // Activity-result callbacks are declared only after the project session and editing helpers
    // exist. Import replaces the complete active snapshot; it is not an ordinary same-ID edit.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            var installedPhoto: File? = null
            runCatching {
                // Copy the selected image into app-owned storage immediately. Persistable
                // document grants are not uniformly supported by every gallery/provider;
                // an app-owned copy also makes ZIP export and offline reopening reliable.
                val photoId = "gallery-${java.util.UUID.randomUUID()}"
                val directory = File(context.filesDir, "photo-assets/${session.project.id}/gallery")
                check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку фотографий" }
                val destination = File(directory, "$photoId.img")
                check(destination.createNewFile()) { "Не удалось создать файл фотографии" }
                installedPhoto = destination
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Галерея не предоставила доступ к выбранному файлу")
                val copiedBytes = input.use { source -> destination.outputStream().buffered().use { output ->
                    BoundedStreamCopy.copy(source, output, MAX_ROOM_PHOTO_BYTES)
                } }
                require(copiedBytes > 0L && destination.isFile && destination.length() == copiedBytes) {
                    "Выбранный файл пуст или записан не полностью"
                }
                val photo = RoomPhotoReference(photoId, Uri.fromFile(destination).toString())
                publish(ProjectEditor.addRoomPhoto(session.project, room.id, photo))
                check(session.project.rooms.any { it.id == room.id && it.photos.any { saved -> saved.id == photo.id } }) {
                    "Проект отклонил фотографию; проверьте параметры помещения"
                }
                selectedPhotoId = photo.id
                status = "Фотография скопирована в проект"
            }.onFailure { failure ->
                installedPhoto?.delete()
                status = "Не удалось добавить фотографию: ${failure.message}"
            }
        }
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val source = pendingCameraFile
        pendingCameraFile = null
        if (captured && source != null && source.isFile && source.length() > 0L) {
            var installedPhoto: File? = null
            runCatching {
                require(source.isFile && source.length() > 0L) { "Камера вернула пустой снимок" }
                val photoId = "camera-${java.util.UUID.randomUUID()}"
                val directory = File(context.filesDir, "photo-assets/${session.project.id}/captured")
                check(directory.exists() || directory.mkdirs()) { "Не удалось создать папку фотографий" }
                val destination = File(directory, "$photoId.jpg")
                check(destination.createNewFile()) { "Не удалось создать файл снимка" }
                installedPhoto = destination
                val copiedBytes = source.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output ->
                        BoundedStreamCopy.copy(input, output, MAX_ROOM_PHOTO_BYTES)
                    }
                }
                require(copiedBytes > 0L && destination.length() == copiedBytes) {
                    "Снимок камеры пуст или записан не полностью"
                }
                installedPhoto = destination
                val photo = RoomPhotoReference(photoId, Uri.fromFile(destination).toString())
                publish(ProjectEditor.addRoomPhoto(session.project, room.id, photo))
                check(session.project.rooms.any { it.id == room.id && it.photos.any { saved -> saved.id == photo.id } }) {
                    "Проект отклонил снимок; проверьте параметры помещения"
                }
                selectedPhotoId = photo.id
                status = "Снимок камеры добавлен в проект"
            }.onFailure { failure ->
                installedPhoto?.delete()
                status = "Не удалось сохранить снимок: ${failure.message}"
            }
            source.delete()
        } else {
            source?.delete()
            if (captured) status = "Камера не вернула файл фотографии"
        }
    }
    fun startCameraCapture() {
        runCatching {
            val file = File(context.cacheDir, "mebel3d-capture-${System.currentTimeMillis()}.jpg")
            pendingCameraFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraCapture.launch(uri)
        }.onFailure {
            pendingCameraFile?.delete()
            pendingCameraFile = null
            status = "Не удалось открыть камеру: ${it.message}"
        }
    }
    val exportProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching {
            storage.exportProject(session.project, uri)
            status = "Файл проекта экспортирован"
        }.onFailure { status = "Ошибка экспорта: ${it.message}" }
    }
    val importProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            // Preserve pending edits before import. importProject validates and stores the incoming
            // project; ProjectSession.open then atomically replaces the in-memory editing session.
            storage.save(session.project)
            val imported = storage.importProject(uri)
            val opened = storage.openProject(imported.id) ?: error("Импортированный проект не найден")
            session.open(opened)
            rooms.clear(); rooms.addAll(opened.rooms)
            placed.clear(); placed.addAll(opened.furniture)
            activeRoomId = opened.activeRoomId.takeIf { id -> opened.rooms.any { it.id == id } }
                ?: opened.rooms.first().id
            selectedId = null
            selectedAssemblyId = null
            selectedAssemblyPartId = null
            selectedPhotoId = null
            selectedEngineeringId = null
            selectedOpeningId = null
            session.markSaved()
            projectSummaries = storage.listProjects()
            status = "Импортирован проект: ${opened.name}"
        }.onFailure { status = "Не удалось импортировать проект: ${it.message}" }
    }
    val sections = listOf("Помещения", "Планировка", "Стены и проёмы", "Двери и окна", "Мебель", "Конструктор", "Материалы", "Инженерия", "Размеры и чертежи", "Фото помещения", "Варианты дизайна", "Сохранение")
    val shown = catalog.filter { (category == "Все" || it.category == category) && it.name.contains(query, true) }

    arrangementPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { arrangementPreview = null },
            title = { Text("Предложение по расстановке") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (preview.isComplete) "Найдено размещение для всех предметов." else "Для части предметов не найдено свободного места.", fontSize = 12.sp, color = Ink)
                    Text("Перемещений: ${preview.movedCount} · Без изменений: ${preview.suggestions.size - preview.movedCount}", fontSize = 10.sp, color = Muted)
                    preview.suggestions.forEach { suggestion ->
                        val itemName = session.project.furniture.firstOrNull { it.id == suggestion.furnitureId }?.name ?: suggestion.furnitureId
                        Text("${if (suggestion.moved) "↔" else "•"} $itemName: ${suggestion.to.x}, ${suggestion.to.y} мм", fontSize = 10.sp, color = Ink)
                    }
                    preview.unresolvedFurnitureIds.forEach { id ->
                        val name = session.project.furniture.firstOrNull { it.id == id }?.name ?: id
                        Text("Не удалось разместить: $name", fontSize = 10.sp, color = Color(0xFFB3261E))
                    }
                    preview.accessWarnings.forEach { warning -> Text("Проверить: ${warning.message}", fontSize = 9.sp, color = Color(0xFF9A6418)) }
                    Text("Это предварительная схема. Размеры и повороты мебели не меняются.", fontSize = 9.sp, color = Muted)
                }
            },
            confirmButton = {
                TextButton(enabled = preview.isComplete && preview.movedCount > 0, onClick = {
                    runCatching { ProjectEditor.applyFurnitureArrangement(session.project, preview) }
                        .onSuccess { publish(it); arrangementPreview = null; status = "Расстановка применена · размеры мебели сохранены" }
                        .onFailure { status = "Не удалось применить расстановку: ${it.message}" }
                }) { Text("Применить") }
            },
            dismissButton = { TextButton(onClick = { arrangementPreview = null }) { Text("Закрыть") } }
        )
    }

    if (showRoomSettings) {
        AlertDialog(onDismissRequest = { showRoomSettings = false },
            title = { Text("Параметры помещения") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                NumberField("Ширина, мм", roomWidth) { roomWidth = it }
                NumberField("Глубина, мм", roomDepth) { roomDepth = it }
                NumberField("Высота, мм", roomHeight) { roomHeight = it }
                NumberField("Толщина стен, мм", wallThickness) { wallThickness = it }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching {
                    val updated = GeometryEngine.resizeRoom(room, roomWidth.toInt(), roomDepth.toInt(), roomHeight.toInt(), wallThickness.toInt())
                    require(GeometryEngine.validateRoom(updated).isEmpty())
                    publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) updated else it }))
                    status = "Размеры помещения обновлены"
                    showRoomSettings = false
                }.onFailure { status = "Проверьте размеры и существующие проёмы" }
            }) { Text("Применить") } },
            dismissButton = { TextButton(onClick = { showRoomSettings = false }) { Text("Отмена") } })
    }
    showOpeningEditor?.let { kind ->
        AlertDialog(onDismissRequest = { showOpeningEditor = null },
            title = { Text(if (editingOpeningId != null) { if (kind == OpeningKind.DOOR) "Изменить дверь" else "Изменить окно" } else { if (kind == OpeningKind.DOOR) "Добавить дверь" else "Добавить окно" }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Стена", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WallSide.values().forEach { side ->
                        FilterChip(selected = openingSide == side, onClick = { openingSide = side }, label = { Text(when(side) { WallSide.NORTH -> "Север"; WallSide.EAST -> "Восток"; WallSide.SOUTH -> "Юг"; WallSide.WEST -> "Запад" }, fontSize = 9.sp) })
                    }
                }
                NumberField("Отступ от угла, мм", openingOffset) { openingOffset = it }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Сдвиг вдоль стены", fontSize = 10.sp, color = Muted)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { openingOffset = ((openingOffset.toIntOrNull() ?: 0) - 100).coerceAtLeast(0).toString() }) { Text("−100 мм") }
                    TextButton(onClick = { openingOffset = ((openingOffset.toIntOrNull() ?: 0) + 100).toString() }) { Text("+100 мм") }
                }
                NumberField("Ширина, мм", openingWidth) { openingWidth = it }
                if (kind == OpeningKind.WINDOW) NumberField("Высота подоконника", openingSill) { openingSill = it }
                NumberField("Высота проёма, мм", openingHeight) { openingHeight = it }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching {
                    val sill = if (kind == OpeningKind.DOOR) 0 else openingSill.toInt()
                    val opening = WallOpening(editingOpeningId ?: "opening-${System.currentTimeMillis()}", openingSide, openingOffset.toInt(), openingWidth.toInt(), sill, openingHeight.toInt(), kind)
                    val next = if (editingOpeningId != null) ProjectEditor.updateOpening(session.project, room.id, opening)
                        else session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) GeometryEngine.addOpening(it, opening) else it })
                    publish(next)
                    selectedOpeningId = opening.id
                    status = if (editingOpeningId != null) "Параметры проёма обновлены" else if (kind == OpeningKind.DOOR) "Дверь добавлена" else "Окно добавлено"
                    editingOpeningId = null
                    showOpeningEditor = null
                }.onFailure { status = "Проём не добавлен: проверьте размеры, стену и пересечения" }
            }) { Text(if (editingOpeningId == null) "Добавить" else "Сохранить") } },
            dismissButton = { TextButton(onClick = { editingOpeningId = null; showOpeningEditor = null }) { Text("Отмена") } })
    }

    if (showEngineeringEditor) {
        AlertDialog(onDismissRequest = { showEngineeringEditor = false },
            title = { Text(if (editingEngineeringId != null) "Изменить инженерный элемент" else "Инженерный элемент помещения") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    EngineeringKind.values().forEach { kind ->
                        FilterChip(selected = engineeringKind == kind, onClick = {
                            engineeringKind = kind
                            engineeringName = when (kind) { EngineeringKind.OUTLET -> "Розетка"; EngineeringKind.SWITCH -> "Выключатель"; EngineeringKind.WATER -> "Вывод воды"; EngineeringKind.GAS -> "Газовая труба"; EngineeringKind.DRAIN -> "Слив"; EngineeringKind.RADIATOR -> "Радиатор"; EngineeringKind.VENTILATION -> "Вентиляция"; EngineeringKind.PIPE -> "Труба" }
                        }, label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 9.sp) })
                    }
                }
                Text(engineeringName, fontSize = 12.sp, color = Ink)
                NumberField("X, мм", engineeringX) { engineeringX = it }
                NumberField("Y, мм", engineeringY) { engineeringY = it }
                NumberField("Высота Z, мм", engineeringZ) { engineeringZ = it }
                Text("Быстрое позиционирование · шаг 100 мм", fontSize = 9.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("X", fontSize = 10.sp, color = Muted)
                    TextButton(onClick = { engineeringX = ((engineeringX.toIntOrNull() ?: 0) - 100).coerceAtLeast(0).toString() }) { Text("−") }
                    TextButton(onClick = { engineeringX = ((engineeringX.toIntOrNull() ?: 0) + 100).coerceAtMost(room.widthMm).toString() }) { Text("+") }
                    Text("Y", fontSize = 10.sp, color = Muted)
                    TextButton(onClick = { engineeringY = ((engineeringY.toIntOrNull() ?: 0) - 100).coerceAtLeast(0).toString() }) { Text("−") }
                    TextButton(onClick = { engineeringY = ((engineeringY.toIntOrNull() ?: 0) + 100).coerceAtMost(room.depthMm).toString() }) { Text("+") }
                    Text("Z", fontSize = 10.sp, color = Muted)
                    TextButton(onClick = { engineeringZ = ((engineeringZ.toIntOrNull() ?: 0) - 100).coerceAtLeast(0).toString() }) { Text("−") }
                    TextButton(onClick = { engineeringZ = ((engineeringZ.toIntOrNull() ?: 0) + 100).coerceAtMost(room.heightMm).toString() }) { Text("+") }
                }
                NumberField("Диаметр, мм (если нужен)", engineeringDiameter) { engineeringDiameter = it }
                Text("Привязка к стене", fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(null, WallSide.NORTH, WallSide.EAST, WallSide.SOUTH, WallSide.WEST).forEach { side ->
                        FilterChip(selected = engineeringWall == side, onClick = {
                            engineeringWall = side
                            if (side != null) {
                                val along = (if (side == WallSide.NORTH || side == WallSide.SOUTH) engineeringX else engineeringY).toIntOrNull() ?: 0
                                val elevation = engineeringZ.toIntOrNull() ?: 0
                                runCatching {
                                    val point = GeometryEngine.wallPosition(room, side,
                                        along.coerceIn(0, room.wallLength(side)), elevation.coerceIn(0, room.heightMm))
                                    engineeringX = point.x.toString(); engineeringY = point.y.toString()
                                }
                            }
                        }, label = { Text(side?.name ?: "Нет", fontSize = 9.sp) })
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching {
                    val enteredPosition = Vec3Mm(engineeringX.toInt(), engineeringY.toInt(), engineeringZ.toInt())
                    val position = engineeringWall?.let { side ->
                        val along = if (side == WallSide.NORTH || side == WallSide.SOUTH) enteredPosition.x else enteredPosition.y
                        GeometryEngine.wallPosition(room, side, along, enteredPosition.z)
                    } ?: enteredPosition
                    val element = EngineeringElement(editingEngineeringId ?: "engineering-${System.currentTimeMillis()}", engineeringName, engineeringKind,
                        position, engineeringWall, engineeringDiameter.toInt().coerceAtLeast(0))
                    val next = if (editingEngineeringId != null) ProjectEditor.updateEngineeringElement(session.project, room.id, element)
                        else ProjectEditor.putEngineeringElement(session.project, room.id, element)
                    publish(next)
                    selectedEngineeringId = element.id
                    editingEngineeringId = null
                    showEngineeringEditor = false
                    status = "Инженерный элемент сохранён"
                }.onFailure { status = "Не удалось добавить: проверьте координаты и размеры" }
            }) { Text(if (editingEngineeringId == null) "Добавить" else "Сохранить") } },
            dismissButton = { TextButton(onClick = { editingEngineeringId = null; showEngineeringEditor = false }) { Text("Отмена") } })
    }

    if (showPhotoCalibration) {
        AlertDialog(onDismissRequest = { showPhotoCalibration = false },
            title = { Text("Калибровка фотографии") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Укажите реальную длину горизонтального отрезка и его длину в пикселях. Начало стены — точка с координатой 0: слева на фото для северной/восточной стены, справа — для южной/западной. Для обрезанного снимка укажите также линию пола.", fontSize = 11.sp)
                NumberField("Реальный размер отрезка, мм", photoKnownLength) { photoKnownLength = it }
                NumberField("Длина этого отрезка на фото, px", photoMeasuredPixels) { photoMeasuredPixels = it }
                NumberField("Пиксель X начала стены (необязательно)", photoWallOriginPixels) { photoWallOriginPixels = it }
                NumberField("Пиксель Y линии пола (необязательно)", photoFloorLinePixels) { photoFloorLinePixels = it }
                room.photos.firstOrNull { it.id == selectedPhotoId }?.let { photo -> Text("Размер исходного фото: ${photo.imageWidthPx ?: "?"} × ${photo.imageHeightPx ?: "?"} px. Пустые координаты используют стандартное начало стены (слева для северной/восточной, справа для южной/западной) и нижний край как линию пола.", fontSize = 9.sp, color = Muted) }
            } },
            confirmButton = { TextButton(onClick = {
                runCatching {
                    val photoId = requireNotNull(selectedPhotoId)
                    val origin = photoWallOriginPixels.trim().takeIf { it.isNotEmpty() }?.toInt()
                    val floor = photoFloorLinePixels.trim().takeIf { it.isNotEmpty() }?.toInt()
                    val calibrated = ProjectEditor.calibrateRoomPhoto(session.project, room.id, photoId, photoKnownLength.toInt(), photoMeasuredPixels.toInt(), origin, floor)
                    val sync = ProjectEditor.synchronizePhotoAnnotations(calibrated, room.id, photoId)
                    publish(sync.project)
                    status = "Масштаб сохранён; обновлено элементов: ${sync.updatedIds.size}, конфликтов: ${sync.conflicts.size}"
                    showPhotoCalibration = false
                }.onFailure { status = "Проверьте положительные значения калибровки" }
            }) { Text("Рассчитать масштаб") } },
            dismissButton = { TextButton(onClick = { showPhotoCalibration = false }) { Text("Отмена") } })
    }

    if (showVariantsDialog) {
        AlertDialog(
            onDismissRequest = { showVariantsDialog = false },
            title = { Text("Варианты интерьера") },
            text = {
                Column(Modifier.widthIn(min = 360.dp, max = 620.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Сохраняйте разные решения одной комнаты или всего проекта. Каждый вариант хранит собственную копию помещений, мебели, проёмов, коммуникаций и фотографий.", fontSize = 11.sp, color = Muted)
                    OutlinedTextField(variantTitle, { variantTitle = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Название варианта") })
                    OutlinedTextField(variantNote, { variantNote = it }, Modifier.fillMaxWidth(), label = { Text("Комментарий (необязательно)") }, minLines = 2)
                    Button(onClick = {
                        runCatching {
                            val variant = RoomDesignVariantManager.capture(session.project,
                                "design-${System.currentTimeMillis()}", variantTitle, variantNote)
                            designVariants += variant
                            storage.saveVariants(session.project.id, designVariants.toList())
                            variantTitle = "Вариант ${designVariants.size + 1}"; variantNote = ""
                            status = "Вариант сохранён на устройстве"
                        }.onFailure { status = "Не удалось сохранить вариант: ${it.message}" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить текущий интерьер как вариант") }
                    comparisonText?.let { Text(it, color = Blue, fontSize = 10.sp) }
                    if (designVariants.isEmpty()) Text("Пока нет сохранённых вариантов", color = Muted, fontSize = 11.sp)
                    designVariants.forEach { variant ->
                        Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(8.dp)).padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(variant.title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 12.sp)
                            if (variant.note.isNotBlank()) Text(variant.note, color = Muted, fontSize = 9.sp)
                            Text("${variant.snapshot.rooms.size} помещений · ${variant.snapshot.furniture.size} предметов · ${variant.snapshot.assemblies.size} сборок", color = Muted, fontSize = 9.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                TextButton(onClick = {
                                    runCatching {
                                        publish(RoomDesignVariantManager.apply(session.project, variant))
                                        status = "Открыт вариант: ${variant.title}"
                                        showVariantsDialog = false
                                    }.onFailure { status = "Вариант не применён: ${it.message}" }
                                }) { Text("Открыть") }
                                TextButton(onClick = {
                                    val baseline = designVariants.firstOrNull { it.id != variant.id }
                                    comparisonText = if (baseline == null) "Для сравнения сохраните ещё один вариант." else {
                                        val diff = RoomDesignVariantManager.compare(baseline, variant)
                                        "${baseline.title} ↔ ${variant.title}: ${diff.changes.size} изменений · добавлено ${diff.addedCount} · удалено ${diff.removedCount}"
                                    }
                                }) { Text("Сравнить") }
                                TextButton(onClick = {
                                    runCatching {
                                        val copy = RoomDesignVariantManager.clone(variant, "design-${System.currentTimeMillis()}", "${variant.title} — копия")
                                        designVariants += copy; storage.saveVariants(session.project.id, designVariants.toList())
                                        status = "Создана копия варианта"
                                    }.onFailure { status = "Ошибка копирования: ${it.message}" }
                                }) { Text("Копия") }
                                TextButton(onClick = {
                                    designVariants.remove(variant); storage.saveVariants(session.project.id, designVariants.toList())
                                    comparisonText = null; status = "Вариант удалён"
                                }) { Text("Удалить") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVariantsDialog = false }) { Text("Закрыть") } }
        )
    }

    if (showProjectManager) {
        AlertDialog(
            onDismissRequest = { showProjectManager = false },
            title = { Text("Мои проекты") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = newProjectName, onValueChange = { newProjectName = it.take(80) },
                        label = { Text("Название нового проекта") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            runCatching {
                                val cleanName = newProjectName.trim().ifBlank { "Новый проект" }
                                val id = "project-${java.util.UUID.randomUUID()}"
                                val firstRoom = RoomModel(id = "room-${java.util.UUID.randomUUID()}", name = "Комната 1", widthMm = 5000, depthMm = 3500, heightMm = 2700)
                                val created = ProjectModel(id, cleanName, listOf(firstRoom), emptyList(), emptyList(), firstRoom.id)
                                storage.save(created)
                                session.open(created)
                                rooms.clear(); rooms.add(firstRoom)
                                placed.clear()
                                activeRoomId = firstRoom.id
                                selectedId = null; selectedAssemblyId = null; selectedAssemblyPartId = null
                                projectSummaries = storage.listProjects()
                                newProjectName = "Новый проект"
                                status = "Создан проект: $cleanName"
                            }.onFailure { status = "Не удалось создать проект: ${it.message}" }
                        }) { Text("Создать проект") }
                        OutlinedButton(onClick = { projectSummaries = storage.listProjects() }) { Text("Обновить") }
                    }
                    Divider()
                    if (projectSummaries.isEmpty()) {
                        Text("Сохранённых проектов пока нет", color = Muted, fontSize = 12.sp)
                    } else {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            projectSummaries.forEach { summary ->
                                Row(Modifier.fillMaxWidth().background(if (summary.id == session.project.id) BlueTint else AppBg, RoundedCornerShape(8.dp)).padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(summary.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${summary.roomCount} помещений · ${summary.furnitureCount} предметов${if (summary.isLastOpened) " · последний" else ""}", color = Muted, fontSize = 9.sp)
                                    }
                                    if (summary.id != session.project.id) {
                                        TextButton(onClick = {
                                            runCatching {
                                                storage.save(session.project)
                                                val opened = storage.openProject(summary.id) ?: error("Проект не найден или повреждён")
                                                session.open(opened)
                                                rooms.clear(); rooms.addAll(opened.rooms)
                                                placed.clear(); placed.addAll(opened.furniture)
                                                activeRoomId = opened.activeRoomId.takeIf { id -> opened.rooms.any { it.id == id } } ?: opened.rooms.first().id
                                                selectedId = null; selectedAssemblyId = null; selectedAssemblyPartId = null
                                                projectSummaries = storage.listProjects()
                                                status = "Открыт проект: ${opened.name}"
                                                showProjectManager = false
                                            }.onFailure { status = "Не удалось открыть проект: ${it.message}" }
                                        }) { Text("Открыть") }
                                    } else { Text("Открыт", color = Blue, fontSize = 10.sp) }
                                    if (projectSummaries.size > 1 && summary.id != session.project.id) {
                                        TextButton(onClick = {
                                            runCatching {
                                                storage.deleteProject(summary.id)
                                                projectSummaries = storage.listProjects()
                                                status = "Проект удалён: ${summary.name}"
                                            }.onFailure { status = "Не удалось удалить проект: ${it.message}" }
                                        }) { Text("Удалить", color = Color.Red) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showProjectManager = false }) { Text("Готово") } }
        )
    }

    val selectedPhoto = room.photos.firstOrNull { it.id == selectedPhotoId }
    if (showPhotoAnnotationEditor && selectedPhoto != null && photoBitmap != null) {
        PhotoAnnotationEditor(bitmap = requireNotNull(photoBitmap), coordinateWidth = photoOriginalWidth, coordinateHeight = photoOriginalHeight, existing = selectedPhoto.annotations,
            onDismiss = { showPhotoAnnotationEditor = false; photoBitmap = null }, onAdd = { mark ->
                runCatching {
                    publish(ProjectEditor.addPhotoAnnotation(session.project, room.id, selectedPhoto.id, mark))
                    status = "Отметка добавлена на фотографию"
                }.onFailure { status = "Не удалось добавить отметку: ${it.message}" }
            }, onUpdate = { mark ->
                runCatching {
                    val edited = ProjectEditor.updatePhotoAnnotation(session.project, room.id, selectedPhoto.id, mark)
                    val sync = ProjectEditor.synchronizePhotoAnnotations(edited, room.id, selectedPhoto.id)
                    publish(sync.project)
                    status = "Отметка изменена; обновлено элементов: ${sync.updatedIds.size}, конфликтов: ${sync.conflicts.size}"
                }.onFailure { status = "Не удалось изменить отметку: ${it.message}" }
            }, onRemove = { markId ->
                runCatching {
                    val edited = ProjectEditor.removePhotoAnnotation(session.project, room.id, selectedPhoto.id, markId)
                    val sync = ProjectEditor.synchronizePhotoAnnotations(edited, room.id, selectedPhoto.id)
                    publish(sync.project)
                    status = "Отметка удалена; сохранённых элементов отсоединено: ${sync.detachedIds.size}"
                }.onFailure { status = "Не удалось удалить отметку: ${it.message}" }
            })
    }

    Column(Modifier.fillMaxSize().background(AppBg).padding(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp).panel().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.width(175.dp).padding(start = 12.dp)) {
                Text("Мебель 3D", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(status, fontSize = 10.sp, color = Muted, maxLines = 1)
            }
            TopAction("Проекты") { projectSummaries = storage.listProjects(); newProjectName = "Новый проект"; showProjectManager = true }
            TopAction("Сохранить") { runCatching { storage.save(session.project); session.markSaved(); projectSummaries = storage.listProjects(); status = "Проект сохранён на устройстве" }.onFailure { status = "Ошибка сохранения: ${it.message}" } }
            TopAction("Варианты · ${designVariants.size}") { showVariantsDialog = true }
            TopAction("↶ Отменить") { if (session.canUndo) restoreFromHistory(session.undo()) else status = "Нет изменений для отмены" }
            TopAction("↷ Повторить") { if (session.canRedo) restoreFromHistory(session.redo()) else status = "Нет изменений для повтора" }
            Spacer(Modifier.weight(1f))
            listOf("2D", "3D").forEach { m ->
                Button(onClick = { mode = m }, colors = ButtonDefaults.buttonColors(containerColor = if (mode == m) Blue else BlueTint, contentColor = if (mode == m) Color.White else Ink), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)) { Text(m) }
            }
            TopAction("☼ Свет") { status = "Освещение: дневное · визуальный режим" }
            TopAction("Экспорт") { exportProjectPicker.launch("${session.project.name.replace(Regex("[^А-Яа-яA-Za-z0-9 _-]"), "_")}.mebel3d") }
            TopAction("Импорт") { importProjectPicker.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "text/*")) }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.width(if (compactLayout) 88.dp else 142.dp).fillMaxHeight().panel().padding(if (compactLayout) 3.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                sections.forEach { section ->
                    Text(if (compactLayout) when (section) { "Помещения" -> "Комната"; "Планировка" -> "План"; "Стены и проёмы" -> "Стены"; "Двери и окна" -> "Проёмы"; "Конструктор" -> "Детали"; "Размеры и чертежи" -> "Чертежи"; "Фото помещения" -> "Фото"; "Варианты дизайна" -> "Варианты"; "Сохранение" -> "Проект"; else -> section } else section, Modifier.fillMaxWidth().background(if (activeSection == section) BlueTint else Panel, RoundedCornerShape(9.dp)).clickable { activeSection = section; status = "Раздел: $section"; if (section == "Варианты дизайна") showVariantsDialog = true }.padding(horizontal = if (compactLayout) 2.dp else 8.dp, vertical = if (compactLayout) 8.dp else 10.dp), color = if (activeSection == section) Blue else Ink, fontSize = if (compactLayout) 9.sp else 11.sp, fontWeight = if (activeSection == section) FontWeight.Bold else FontWeight.Medium, maxLines = if (compactLayout) 3 else 2)
                }
            }
            Column(Modifier.width(if (compactLayout) 165.dp else 205.dp).fillMaxHeight().panel().padding(if (compactLayout) 5.dp else 8.dp)) {
                if (activeSection == "Конструктор") {
                    Text("Свободный конструктор", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Добавляйте детали по одной. Выберите панель, полку или штангу — затем меняйте её прямо на чертеже пальцами.", fontSize = 9.sp, color = Muted)
                    Divider(color = Border, modifier = Modifier.padding(vertical = 5.dp))
                    Text("ДЕТАЛИ ДЛЯ ПОСТРОЕНИЯ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Muted)
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(freePartTools.filter { query.isBlank() || it.name.contains(query, true) }) { tool ->
                            Column(Modifier.fillMaxWidth().border(1.dp, if (selected == tool.name) Blue else Border, RoundedCornerShape(9.dp))
                                .background(if (selected == tool.name) BlueTint else Color(0xFFFAFBFE), RoundedCornerShape(9.dp))
                                .clickable {
                                    val id = "part-${java.util.UUID.randomUUID()}"
                                    val x = ((room.widthMm - tool.size.width) / 2).coerceAtLeast(0)
                                    val y = ((room.depthMm - tool.size.depth) / 2).coerceAtLeast(0)
                                    val z = tool.zMm.coerceIn(0, (room.heightMm - tool.size.height).coerceAtLeast(0))
                                    val part = FurnitureItem(id, tool.name, tool.size, Vec3Mm(x, y, z), materialId = tool.materialId, roomId = room.id)
                                    publish(session.project.copy(furniture = session.project.furniture + part))
                                    selected = tool.name
                                    selectedId = id
                                    selectedAssemblyId = null
                                    selectedAssemblyPartId = null
                                    width = tool.size.width.toString(); depth = tool.size.depth.toString(); height = tool.size.height.toString()
                                    posX = x.toString(); posY = y.toString(); posZ = z.toString(); angle = "0"
                                    mode = "3D"
                                    status = "Добавлена деталь «${tool.name}». Выделите её на поле и растягивайте маркеры размера."
                                }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(38.dp).background(Color(0xFFF0F2F5), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                    Text(tool.glyph, fontSize = 24.sp, color = Ink)
                                }
                                Text(tool.name, fontSize = 9.sp, color = Ink, maxLines = 2)
                                Text("${tool.size.width}×${tool.size.depth}×${tool.size.height}", fontSize = 8.sp, color = Muted, maxLines = 1)
                            }
                        }
                    }
                    Divider(color = Border, modifier = Modifier.padding(vertical = 5.dp))
                    Text("В проекте: ${session.project.furniture.count { it.roomId == room.id }} отдельных деталей", fontSize = 9.sp, color = Muted)
                    Text("Копирование и удаление — в свойствах выбранной детали.", fontSize = 8.sp, color = Muted)
                }
                if (activeSection == "Планировка") {
                    Text("Умная расстановка", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("Подбор свободных мест с учётом дверей, окон и доступа к инженерным точкам.", fontSize = 9.sp, color = Muted)
                    Button(onClick = {
                        runCatching { RoomFurnitureArrangementPlanner.plan(session.project, room.id) }
                            .onSuccess { arrangementPreview = it; status = "Подготовлено предложение по расстановке" }
                            .onFailure { status = "Не удалось рассчитать расстановку: ${it.message}" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Подобрать расстановку", fontSize = 10.sp) }
                    Divider(color = Border, modifier = Modifier.padding(vertical = 4.dp))
                }
                if (activeSection != "Конструктор") {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().height(52.dp), singleLine = true, placeholder = { Text("Поиск мебели…", fontSize = 11.sp) })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    (listOf("Все") + catalog.map { it.category }.distinct()).forEach { c ->
                        Text(c, Modifier.background(if (category == c) BlueTint else AppBg, RoundedCornerShape(16.dp)).clickable { category = c }.padding(horizontal = 7.dp, vertical = 6.dp), fontSize = 10.sp, color = if (category == c) Blue else Ink)
                    }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(shown) { item ->
                        Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(10.dp)).background(if (selected == item.name) BlueTint else Color(0xFFFAFBFE), RoundedCornerShape(10.dp)).clickable {
                                selected = item.name
                                val id = "item-${nextObjectId++}"
                                val entry = InteriorCatalogEngine.entries.first { it.name == item.name }
                                val placement = InteriorCatalogEngine.place(session.project, entry.id, id, room.id)
                                val obj = placement.item
                                if (obj == null) {
                                    status = placement.issues.firstOrNull() ?: "Не удалось разместить объект"
                                } else {
                                    publish(session.project.copy(furniture = session.project.furniture + obj)); selectedId = id
                                    width = obj.size.width.toString(); depth = obj.size.depth.toString(); height = obj.size.height.toString()
                                    posX = obj.position.x.toString(); posY = obj.position.y.toString(); posZ = "0"; angle = "0"
                                    status = "Добавлен объект: ${item.name} · найдено свободное место"
                                }
                            }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val entry = InteriorCatalogEngine.entries.first { it.name == item.name }
                            Box(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFFF0F2F5), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                Text(item.glyph, fontSize = 27.sp)
                            }
                            Text(item.name, fontSize = 10.sp, color = Ink, maxLines = 2)
                            Text("${entry.size.width} × ${entry.size.depth} × ${entry.size.height} мм", fontSize = 8.sp, color = Muted, maxLines = 1)
                        }
                    }
                }
                }
                if (activeSection == "Инженерия") {
                    Divider(Modifier.padding(vertical = 5.dp))
                    Text("Элементы помещения", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                    room.engineering.forEach { element ->
                        Row(Modifier.fillMaxWidth().background(if (selectedEngineeringId == element.id) BlueTint else AppBg, RoundedCornerShape(6.dp)).clickable { selectedEngineeringId = element.id }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${element.name} · ${element.position.x}, ${element.position.y}, ${element.position.z}", Modifier.weight(1f).clickable { selectedEngineeringId = element.id }, fontSize = 9.sp, color = Ink, maxLines = 2)
                            Text("✎", Modifier.clickable {
                                editingEngineeringId = element.id; engineeringName = element.name; engineeringKind = element.kind
                                engineeringX = element.position.x.toString(); engineeringY = element.position.y.toString(); engineeringZ = element.position.z.toString()
                                engineeringDiameter = element.diameterMm.toString(); engineeringWall = element.wallSide; showEngineeringEditor = true
                            }.padding(4.dp), color = Blue)
                            Text("×", Modifier.clickable { publish(ProjectEditor.removeEngineeringElement(session.project, room.id, element.id)); selectedEngineeringId = null }.padding(4.dp), color = Color.Red)
                        }
                    }
                    if (room.engineering.isEmpty()) Text("Пока нет элементов", fontSize = 10.sp, color = Muted)
                }
                if (activeSection == "Двери и окна") {
                    Divider(Modifier.padding(vertical = 5.dp))
                    Text("Проёмы помещения", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                    room.openings.forEach { opening ->
                        Row(Modifier.fillMaxWidth().background(if (selectedOpeningId == opening.id) BlueTint else AppBg, RoundedCornerShape(6.dp)).clickable { selectedOpeningId = opening.id }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${if (opening.kind == OpeningKind.DOOR) "Дверь" else "Окно"} · ${opening.side} · ${opening.widthMm}×${opening.heightMm}", Modifier.weight(1f), fontSize = 9.sp, color = Ink, maxLines = 2)
                            Text("✎", Modifier.clickable {
                                editingOpeningId = opening.id; openingSide = opening.side; openingOffset = opening.offsetMm.toString()
                                openingWidth = opening.widthMm.toString(); openingHeight = opening.heightMm.toString(); openingSill = opening.sillHeightMm.toString()
                                showOpeningEditor = opening.kind
                            }.padding(4.dp), color = Blue)
                            Text("×", Modifier.clickable { publish(ProjectEditor.removeOpening(session.project, room.id, opening.id)); selectedOpeningId = null }.padding(4.dp), color = Color.Red)
                        }
                    }
                    if (room.openings.isEmpty()) Text("Пока нет проёмов", fontSize = 10.sp, color = Muted)
                }
                if (activeSection == "Фото помещения") {
                    Divider(Modifier.padding(vertical = 5.dp))
                    Text("Фотографии комнаты", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                    room.photos.forEach { photo ->
                        Column(Modifier.fillMaxWidth().background(if (selectedPhotoId == photo.id) BlueTint else AppBg, RoundedCornerShape(7.dp)).clickable { selectedPhotoId = photo.id }.padding(5.dp)) {
                            Text("Фото ${photo.id.takeLast(6)}", fontSize = 10.sp, color = Ink)
                            Text(photo.wallSide?.name ?: "Стена не указана", fontSize = 9.sp, color = Muted)
                            Text(if (photo.knownLengthMm != null && photo.calibrationPixels != null) "${photo.knownLengthMm.toDouble() / photo.calibrationPixels} мм/px" else "Не откалибровано", fontSize = 9.sp, color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                WallSide.values().forEach { side -> Text(side.name.take(1), Modifier.clickable {
                                    runCatching {
                                        val reassigned = ProjectEditor.setPhotoWall(session.project, room.id, photo.id, side)
                                        val sync = ProjectEditor.synchronizePhotoAnnotations(reassigned, room.id, photo.id)
                                        publish(sync.project)
                                        status = "Стена изменена; обновлено элементов: ${sync.updatedIds.size}, конфликтов: ${sync.conflicts.size}"
                                    }.onFailure { status = "Не удалось обновить привязку: ${it.message}" }
                                }.padding(3.dp), fontSize = 9.sp, color = if (photo.wallSide == side) Blue else Ink) }
                                Text("Калибровать", Modifier.clickable {
                                    selectedPhotoId = photo.id
                                    photoKnownLength = photo.knownLengthMm?.toString() ?: "2000"
                                    photoMeasuredPixels = photo.calibrationPixels?.toString() ?: "1000"
                                    photoWallOriginPixels = photo.wallOriginXPx?.toString().orEmpty()
                                    photoFloorLinePixels = photo.floorLineYPx?.toString().orEmpty()
                                    showPhotoCalibration = true
                                }.padding(3.dp), fontSize = 9.sp, color = Blue)
                                Text("Разметить", Modifier.clickable {
                                    selectedPhotoId = photo.id
                                    runCatching {
                                        val photoUri = android.net.Uri.parse(photo.uri)
                                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                                        val plan = PhotoDecodePlan.create(bounds.outWidth, bounds.outHeight)
                                        val options = BitmapFactory.Options().apply { inSampleSize = plan.sampleSize }
                                        val bitmap = context.contentResolver.openInputStream(photoUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                                            ?: error("Не удалось декодировать фотографию")
                                        Triple(bitmap, plan.originalWidth, plan.originalHeight)
                                    }.onSuccess { (bitmap, originalWidth, originalHeight) ->
                                        photoBitmap = bitmap
                                        photoOriginalWidth = originalWidth
                                        photoOriginalHeight = originalHeight
                                        showPhotoAnnotationEditor = true
                                    }.onFailure { failure -> status = "Не удалось открыть фото: ${failure.message}" }
                                }.padding(3.dp), fontSize = 9.sp, color = Blue)
                                Text("×", Modifier.clickable { publish(ProjectEditor.removeRoomPhoto(session.project, room.id, photo.id)); selectedPhotoId = null }.padding(3.dp), fontSize = 10.sp, color = Color.Red)
                            }
                        }
                    }
                    val photoCoverage = RoomPhotoCoverageAnalyzer.analyze(room)
                    Text("Покрытие стен фотографиями", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Ink)
                    photoCoverage.walls.forEach { wall ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${wall.side.name}: ${wall.coveredLengthMm}/${wall.wallLengthMm} мм", fontSize = 9.sp, color = Ink)
                            Text("${wall.coveragePercent}% · ${wall.photoCount} фото", fontSize = 9.sp,
                                color = if (wall.isFullyCovered) Color(0xFF23834A) else Muted)
                        }
                        val wallStateLabel = when (wall.state) {
                            WallCoverageState.NO_PHOTOS -> "Нет фотографий"
                            WallCoverageState.NEEDS_CALIBRATION -> "Фото есть, нужна калибровка"
                            WallCoverageState.PARTIAL -> "Покрытие неполное"
                            WallCoverageState.COMPLETE -> "Откалибровано полностью"
                            WallCoverageState.COMPLETE_WITH_UNCALIBRATED_PHOTOS -> "Покрытие полное; есть фото без калибровки"
                        }
                        Text(wallStateLabel, fontSize = 8.sp, color = when (wall.state) {
                            WallCoverageState.COMPLETE -> Color(0xFF23834A)
                            WallCoverageState.NEEDS_CALIBRATION, WallCoverageState.NO_PHOTOS -> Color(0xFFB36B18)
                            else -> Muted
                        })
                        if (wall.uncoveredGaps.isNotEmpty() && wall.wallLengthMm > 0) {
                            val targets = wall.uncoveredGaps.sortedByDescending { it.lengthMm }
                            Text(
                                "Не снято: ${wall.uncoveredLengthMm} мм · ${targets.size} участков",
                                fontSize = 8.sp, color = Color(0xFFB36B18)
                            )
                            Text(
                                "Снять: ${targets.take(3).joinToString("; ") { "${it.startMm}–${it.endMm} мм" }}" +
                                    if (targets.size > 3) "; ещё ${targets.size - 3}" else "",
                                fontSize = 8.sp, color = Color(0xFF8A5B24)
                            )
                        }
                    }
                    if (photoCoverage.totalUncoveredLengthMm > 0) {
                        Text(
                            "Всего не покрыто: ${photoCoverage.totalUncoveredLengthMm} мм; сначала снимите стену ${photoCoverage.largestUncoveredWall?.side?.name ?: "—"}",
                            fontSize = 8.sp, color = Color(0xFF8A5B24)
                        )
                    } else if (photoCoverage.hasCompleteCalibratedCoverage) {
                        Text("Все стены имеют полное откалиброванное покрытие", fontSize = 8.sp, color = Color(0xFF23834A))
                    }
                    if (photoCoverage.unassignedPhotoIds.isNotEmpty()) {
                        Text("Не привязано к стене: ${photoCoverage.unassignedPhotoIds.size}", fontSize = 9.sp, color = Color(0xFFB36B18))
                    }
                    if (photoCoverage.uncalibratedPhotoIds.isNotEmpty()) {
                        Text("Требуют калибровки: ${photoCoverage.uncalibratedPhotoIds.size}", fontSize = 9.sp, color = Color(0xFFB36B18))
                    }
                    if (photoCoverage.scaleMismatchWallSides.isNotEmpty()) {
                        Text("Проверьте масштаб фото стен: ${photoCoverage.scaleMismatchWallSides.joinToString()}", fontSize = 9.sp, color = Color(0xFFB36B18))
                    }
                    if (room.photos.any { it.annotations.isNotEmpty() }) {
                        OutlinedButton(onClick = {
                            runCatching {
                                val result = PhotoWallTransfer.transferAll(session.project, room.id)
                                publish(result.project)
                                status = "По всем фото перенесено: ${result.createdOpenings.size} проёмов, ${result.createdEngineering.size} инженерных элементов; пропущено отметок: ${result.ignoredAnnotations.size}; без стены: ${result.skippedPhotoReasons.count { it.value == PhotoTransferSkipReason.NO_WALL }}, нужна калибровка: ${result.skippedPhotoReasons.count { it.value == PhotoTransferSkipReason.NEEDS_CALIBRATION }}, ошибки геометрии: ${result.skippedPhotoReasons.count { it.value == PhotoTransferSkipReason.INVALID_TRANSFER }}"
                            }.onFailure { status = "Общий перенос невозможен: ${it.message}" }
                        }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) {
                            Text("Перенести отметки со всех фото комнаты", fontSize = 9.sp)
                        }
                    }
                    selectedPhoto?.let { photo ->
                        Text("Отметок: ${photo.annotations.size}", fontSize = 9.sp, color = Muted)
                        OutlinedButton(onClick = {
                            runCatching {
                                val result = PhotoWallTransfer.transfer(session.project, room.id, photo.id)
                                publish(result.project)
                                status = "Перенесено: ${result.createdOpenings.size} проёмов, ${result.createdEngineering.size} инженерных элементов; пропущено: ${result.ignoredAnnotations.size}"
                            }.onFailure { status = "Перенос невозможен: ${it.message}" }
                        }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(4.dp)) { Text("Перенести отметки на стену", fontSize = 9.sp) }
                    }
                    if (room.photos.isEmpty()) Text("Добавьте фото пустой комнаты", fontSize = 10.sp, color = Muted)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().panel().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Помещения", color = Muted, fontSize = 10.sp)
                    rooms.forEach { r ->
                        Text(r.name, Modifier.background(if (r.id == room.id) BlueTint else AppBg, RoundedCornerShape(8.dp)).clickable { activeRoomId = r.id; selectedId = null; status = "Открыто: ${r.name}" }.padding(horizontal = 8.dp, vertical = 6.dp), color = if (r.id == room.id) Blue else Ink, fontSize = 10.sp, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    TopAction("Размеры") { roomWidth = room.widthMm.toString(); roomDepth = room.depthMm.toString(); roomHeight = room.heightMm.toString(); wallThickness = room.wallThicknessMm.toString(); showRoomSettings = true }
                    TopAction("+ Дверь") { openingOffset = "800"; openingWidth = "900"; openingHeight = "2100"; openingSill = "0"; showOpeningEditor = OpeningKind.DOOR }
                    TopAction("+ Окно") { openingOffset = "1000"; openingWidth = "1200"; openingHeight = "1200"; openingSill = "900"; showOpeningEditor = OpeningKind.WINDOW }
                    TopAction("+ Инженерия") { engineeringX = (room.widthMm / 2).toString(); engineeringY = "100"; engineeringZ = "300"; showEngineeringEditor = true }
                    if (activeSection == "Фото помещения") {
                        TopAction("Снять фото") { startCameraCapture() }
                        TopAction("Из галереи") { photoPicker.launch(arrayOf("image/*")) }
                    }
                    TopAction("+ Комната") {
                        val index = rooms.size + 1
                        val newRoom = RoomModel(id = "room-${System.currentTimeMillis()}", name = "Комната $index", widthMm = 4000, depthMm = 3000, heightMm = 2700)
                        publish(ProjectEditor.addRoom(session.project, newRoom)); selectedId = null; status = "Добавлено помещение ${newRoom.name}"
                    }
                    TopAction("Копия") {
                        val copyId = "room-${System.currentTimeMillis()}"
                        val copied = room.copy(id = copyId, name = "${room.name} (копия)", openings = room.openings.map { it.copy(id = "$copyId-${it.id}") }, engineering = room.engineering.map { it.copy(id = "$copyId-${it.id}") }, photos = room.photos.map { it.copy(id = "$copyId-${it.id}") })
                        val copiedFurniture = visibleFurniture.map { it.copy(id = "$copyId-${it.id}", roomId = copyId) }
                        publish(session.project.copy(rooms = session.project.rooms + copied, furniture = session.project.furniture + copiedFurniture, activeRoomId = copyId))
                        selectedId = null; status = "Помещение и мебель скопированы"
                    }
                    TopAction("Удалить") {
                        if (rooms.size > 1) { publish(ProjectEditor.removeRoom(session.project, room.id)); selectedId = null; status = "Помещение удалено" }
                        else status = "Нельзя удалить последнее помещение"
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mode == "3D") "3D · Обзор комнаты" else "2D · План помещения", color = Muted, fontSize = 11.sp)
                    if (mode == "3D") {
                        TopAction("↶") { cameraYaw = (cameraYaw - 15f + 360f) % 360f }
                        TopAction("↷") { cameraYaw = (cameraYaw + 15f) % 360f }
                        TopAction("−") { cameraZoom = (cameraZoom / 1.15f).coerceAtLeast(0.55f) }
                        TopAction("+") { cameraZoom = (cameraZoom * 1.15f).coerceAtMost(2.4f) }
                        TopAction("Изометрия") { cameraYaw = 45f; cameraPitch = 32f; cameraZoom = 1f }
                        TopAction("Сверху") { cameraPitch = 85f }
                        TopAction("На уровне") { cameraPitch = 12f }
                        TopAction("Стены: $wallDisplay") { wallDisplay = when (wallDisplay) { "Срез" -> "Прозрачные"; "Прозрачные" -> "Ближние"; else -> "Срез" } }
                        TopAction(if (showCeiling) "Потолок: да" else "Потолок: нет") { showCeiling = !showCeiling }
                    }
                    Text("${room.widthMm} × ${room.depthMm} × ${room.heightMm} мм", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    RoomCanvas(room, visibleFurniture, selectedId, selectedEngineeringId, selectedOpeningId, mode == "3D", cameraYaw = cameraYaw, cameraPitch = cameraPitch, cameraZoom = cameraZoom, wallDisplay = wallDisplay, showCeiling = showCeiling, wallColorArgb = room.wallColorArgb, floorColorArgb = room.floorColorArgb, lightingPreset = room.lightingPreset,
                        wallMaterialId = room.wallMaterialId, northWallMaterialId = room.northWallMaterialId, eastWallMaterialId = room.eastWallMaterialId, southWallMaterialId = room.southWallMaterialId, westWallMaterialId = room.westWallMaterialId, floorMaterialId = room.floorMaterialId, ceilingColorArgb = room.ceilingColorArgb, ceilingMaterialId = room.ceilingMaterialId, lightIntensityPercent = room.lightIntensityPercent, lightTemperatureKelvin = room.lightTemperatureKelvin, shadowStrengthPercent = room.shadowStrengthPercent, measurementGuides = selectedId?.let { id -> runCatching { SceneAlignmentEngine.measure(session.project, id) }.getOrDefault(emptyList()) } ?: emptyList(), onSelect = { id ->
                        // A scene selection is exclusive: stale assembly-part selection must not
                        // keep intercepting drags after the user taps furniture/openings/engineering.
                        selectedAssemblyId = null
                        selectedAssemblyPartId = null
                        when {
                            id.startsWith("engineering:") -> { selectedEngineeringId = id.removePrefix("engineering:"); selectedOpeningId = null; selectedId = null; activeSection = "Инженерия" }
                            id.startsWith("opening:") -> { selectedOpeningId = id.removePrefix("opening:"); selectedEngineeringId = null; selectedId = null; activeSection = "Двери и окна" }
                            else -> { selectedId = id; selectedEngineeringId = null; selectedOpeningId = null }
                        }
                        placed.firstOrNull { it.id == id }?.let { obj ->
                            selected = obj.name; width = obj.size.width.toString(); depth = obj.size.depth.toString(); height = obj.size.height.toString()
                            posX = obj.position.x.toString(); posY = obj.position.y.toString(); posZ = obj.position.z.toString(); angle = obj.rotationDeg.toString()
                        }
                    }, onMoveSelected = { dx, dy ->
                        val item = session.project.furniture.firstOrNull { it.id == selectedId }
                        if (item != null && (dx != 0 || dy != 0)) {
                            val targetX = item.position.x + dx
                            val targetY = item.position.y + dy
                            runCatching { publishGesture(ProjectEditor.moveFurniture(session.project, item.id, targetX, targetY, gridMm = 50)) }
                                .onFailure { status = "Перемещение отклонено: ${it.message}" }
                        }
                    }, assemblies = session.project.assemblies, selectedPartKey = selectedAssemblyId?.let { a -> selectedAssemblyPartId?.let { p -> "$a:$p" } }, onSelectPart = { assemblyId, partId ->
                        selectedAssemblyId = assemblyId; selectedAssemblyPartId = partId
                        selectedId = null; selectedEngineeringId = null; selectedOpeningId = null
                        val part = session.project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
                        if (part != null) { selected = part.name; width = part.size.width.toString(); depth = part.size.depth.toString(); height = part.size.height.toString(); posX = part.localPosition.x.toString(); posY = part.localPosition.y.toString(); posZ = part.localPosition.z.toString(); angle = part.rotationDeg.toString(); activeSection = "Конструктор" }
                    }, onMoveSelectedPart = { assemblyId, partId, dx, dy ->
                        val current = session.project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
                        if (current != null && (dx != 0 || dy != 0)) {
                            runCatching { publishGesture(ProjectEditor.moveAssemblyPart(session.project, assemblyId, partId, current.localPosition.x + dx, current.localPosition.y + dy, current.localPosition.z, 10)) }
                                .onFailure { status = "Перемещение детали отклонено: ${it.message}" }
                        }
                    }, onResizeSelectedPart = { assemblyId, partId, dw, dh ->
                        val current = session.project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
                        if (current != null && (dw != 0 || dh != 0)) {
                            runCatching { publishGesture(ProjectEditor.resizeAssemblyPart(session.project, assemblyId, partId, (current.size.width + dw).coerceAtLeast(1), (current.size.depth + dh).coerceAtLeast(1), current.size.height)) }
                                .onFailure { status = "Изменение размера детали отклонено: ${it.message}" }
                        }
                    }, onRotateSelectedPart = { assemblyId, partId, targetAngle ->
                        val current = session.project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
                        if (current != null && current.rotationDeg != targetAngle) {
                            runCatching { publishGesture(ProjectEditor.rotateAssemblyPartTo(session.project, assemblyId, partId, targetAngle)) }
                                .onSuccess { angle = targetAngle.toString() }
                                .onFailure { status = "Поворот детали отклонён: ${it.message}" }
                        }
                    }, onChangeSelectedPartHeight = { assemblyId, partId, targetHeight ->
                        val current = session.project.assemblies.firstOrNull { it.id == assemblyId }?.parts?.firstOrNull { it.id == partId }
                        if (current != null && current.size.height != targetHeight) {
                            runCatching { publishGesture(ProjectEditor.resizeAssemblyPart(session.project, assemblyId, partId,
                                current.size.width, current.size.depth, targetHeight)) }
                                .onSuccess { height = targetHeight.toString() }
                                .onFailure { status = "Изменение высоты детали отклонено: ${it.message}" }
                        }
                    }, onCameraChange = { yaw, pitch, zoom ->
                        cameraYaw = yaw
                        cameraPitch = pitch
                        cameraZoom = zoom
                    }, onDragStart = { session.beginGesture() }, onDragEnd = { session.endGesture() }, onDragCancel = { restoreFromHistory(session.cancelGesture()) }, onResizeSelectedFurniture = { dw, dd ->
                        val current = session.project.furniture.firstOrNull { it.id == selectedId }
                        if (current != null && (dw != 0 || dd != 0)) {
                            val (nextWidth, nextDepth) = FurnitureResizeMath.resizedDimensions(
                                current.size.width, current.size.depth, dw, dd
                            )
                            runCatching { ProjectEditor.resizeFurniture(session.project, current.id,
                                nextWidth, nextDepth, current.size.height) }
                                .onSuccess { publishGesture(it); width = nextWidth.toString(); depth = nextDepth.toString() }
                                .onFailure { status = "Изменение размера отклонено: ${it.message}" }
                        }
                    }, onRotateSelectedFurniture = { targetAngle ->
                        val current = session.project.furniture.firstOrNull { it.id == selectedId }
                        if (current != null && current.rotationDeg != targetAngle) {
                            runCatching { ProjectEditor.rotateFurnitureTo(session.project, current.id, targetAngle) }
                                .onSuccess { publishGesture(it); angle = targetAngle.toString() }
                                .onFailure { status = "Поворот отклонён: ${it.message}" }
                        }
                    }, onChangeSelectedFurnitureHeight = { targetHeight ->
                        val current = session.project.furniture.firstOrNull { it.id == selectedId }
                        if (current != null && current.size.height != targetHeight) {
                            runCatching { ProjectEditor.resizeFurnitureHeight(session.project, current.id, targetHeight) }
                                .onSuccess { publishGesture(it); height = targetHeight.toString() }
                                .onFailure { status = "Изменение высоты отклонено: ${it.message}" }
                        }
                    })
                }
                Text("Ручки выбранного объекта: синяя — размер · зелёная — поворот · янтарная — высота в 3D", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
                val roomReport = RoomReadinessAnalyzer.analyze(room, visibleFurniture)
                val readinessLabel = when {
                    roomReport.errorCount > 0 -> "Есть ошибки геометрии"
                    roomReport.warningCount > 0 -> "Нужна проверка проекта"
                    else -> "Критичных замечаний нет"
                }
                Text("Проверка проекта · $readinessLabel", color = if (roomReport.errorCount > 0) Color(0xFFB3261E) else if (roomReport.warningCount > 0) Color(0xFF9A6418) else Color(0xFF23834A), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                Text("Объектов: ${visibleFurniture.size} · Ошибки: ${roomReport.errorCount} · Предупреждения: ${roomReport.warningCount}", color = Muted, fontSize = 9.sp)
                roomReport.photoTransferPreview?.takeIf { it.annotatedPhotoCount > 0 }?.let { preview ->
                    Text(
                        "Фоторазметка: готово ${preview.readyPhotoCount}/${preview.annotatedPhotoCount} · к созданию ${preview.plannedElementCount} · пропущено отметок ${preview.ignoredAnnotationCount}",
                        color = if (preview.hasUnresolvedMarks) Color(0xFF9A6418) else Color(0xFF23834A),
                        fontSize = 8.sp
                    )
                }
                roomReport.nextActions.forEach { finding ->
                    Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text("• ${finding.message}", color = if (finding.severity == RoomFindingSeverity.ERROR) Color(0xFFB3261E) else Color(0xFF9A6418), fontSize = 9.sp)
                        finding.suggestedAction?.let { Text(it, color = Muted, fontSize = 8.sp) }
                    }
                }
            }
            Column(Modifier.width(if (compactLayout) 188.dp else 210.dp).fillMaxHeight().panel().padding(if (compactLayout) 6.dp else 10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(selectedItem?.name ?: "Объект не выбран", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                if (activeSection == "Размеры и чертежи") {
                    val drawings = remember(room, visibleFurniture) { RoomDrawingProjection.build(room, visibleFurniture) }
                    val plan = drawings.plan
                    val elevation = drawings.elevations[drawingWallSide]!!
                    Text("Схема помещения", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text("План, виды стен и 3D используют одну модель комнаты и общие координаты.", color = Muted, fontSize = 9.sp)
                    Divider(color = Border)
                    Text("План помещения", fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    Text("${plan.widthMm} × ${plan.depthMm} мм · высота ${room.heightMm} мм", fontSize = 10.sp, color = Ink)
                    RoomDrawingPreview(
                        plan = plan,
                        elevation = drawings.elevations.getValue(drawingWallSide),
                        selectedFurnitureId = selectedId,
                        selectedOpeningId = selectedOpeningId,
                        selectedEngineeringId = selectedEngineeringId,
                        onSelectFurniture = { id ->
                            val item = session.project.furniture.firstOrNull { it.id == id && it.roomId == room.id }
                            if (item != null) {
                                selectedId = item.id
                                selectedAssemblyId = null
                                selectedAssemblyPartId = null
                                selectedEngineeringId = null
                                selectedOpeningId = null
                                selected = item.name
                                width = item.size.width.toString()
                                depth = item.size.depth.toString()
                                height = item.size.height.toString()
                                posX = item.position.x.toString()
                                posY = item.position.y.toString()
                                posZ = item.position.z.toString()
                                angle = item.rotationDeg.toString()
                                mode = "3D"
                                status = "Выбран объект: ${item.name} · ${item.size.width} × ${item.size.depth} × ${item.size.height} мм"
                            }
                        },
                        onSelectOpening = { id ->
                            val opening = session.project.rooms.firstOrNull { it.id == room.id }?.openings?.firstOrNull { it.id == id }
                            if (opening != null) {
                                selectedOpeningId = id; selectedId = null; selectedEngineeringId = null
                                selectedAssemblyId = null; selectedAssemblyPartId = null
                                selected = if (opening.kind == OpeningKind.DOOR) "Дверь" else "Окно"
                                openingSide = opening.side; drawingWallSide = opening.side
                                openingOffset = opening.offsetMm.toString(); openingWidth = opening.widthMm.toString()
                                openingHeight = opening.heightMm.toString(); openingSill = opening.sillHeightMm.toString()
                                editingOpeningId = opening.id; showOpeningEditor = opening.kind
                                mode = "3D"; activeSection = "Двери и окна"
                                status = "Выбран проём · ${opening.side} · ${opening.widthMm} × ${opening.heightMm} мм"
                            }
                        },
                        onSelectEngineering = { id ->
                            val element = session.project.rooms.firstOrNull { it.id == room.id }?.engineering?.firstOrNull { it.id == id }
                            if (element != null) {
                                selectedEngineeringId = id; selectedId = null; selectedOpeningId = null
                                selectedAssemblyId = null; selectedAssemblyPartId = null
                                selected = element.name; engineeringName = element.name; engineeringKind = element.kind
                                engineeringX = element.position.x.toString(); engineeringY = element.position.y.toString()
                                engineeringZ = element.position.z.toString(); engineeringDiameter = element.diameterMm.toString()
                                engineeringWall = element.wallSide; editingEngineeringId = element.id
                                showEngineeringEditor = true; mode = "3D"; activeSection = "Инженерия"
                                status = "Выбрана точка: ${element.name} · ${element.position.x}, ${element.position.y}, ${element.position.z} мм"
                            }
                        }
                    )
                    Text("Схема комнаты · только наглядное планирование, без производственных чертежей", fontSize = 8.sp, color = Muted)
                    Text("Мебель: ${plan.furniture.size} · Проёмы: ${plan.openings.size} · Инженерные точки: ${plan.engineering.size}", fontSize = 9.sp, color = Muted)
                    Text("Вид стены", fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(WallSide.NORTH to "С" , WallSide.EAST to "В", WallSide.SOUTH to "Ю", WallSide.WEST to "З").forEach { (side, label) ->
                            FilterChip(selected = drawingWallSide == side, onClick = { drawingWallSide = side }, label = { Text(label, fontSize = 9.sp) })
                        }
                    }
                    Text("${when (drawingWallSide) { WallSide.NORTH -> "Северная"; WallSide.EAST -> "Восточная"; WallSide.SOUTH -> "Южная"; WallSide.WEST -> "Западная" }} · ${elevation.lengthMm} × ${elevation.heightMm} мм", fontSize = 10.sp, color = Ink)
                    Text("Проёмы: ${elevation.openings.size} · Инженерия: ${elevation.engineering.size} · Мебель у стены: ${elevation.furniture.size}", fontSize = 9.sp, color = Muted)
                    Text("Размеры проёмов и высоты показаны схематично; точные параметры редактируются в карточках двери/окна.", fontSize = 8.sp, color = Muted)
                    if (elevation.engineering.isNotEmpty()) {
                        Text("Инженерные отметки", fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
                        elevation.engineering.forEach { marker ->
                            Text("${marker.kind}: ${marker.alongMm} мм от угла · высота ${marker.elevationMm} мм", fontSize = 8.sp, color = Muted)
                        }
                    }
                    if (elevation.furniture.isNotEmpty()) {
                        Text("Предметы у стены", fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
                        elevation.furniture.forEach { item -> Text("${item.name}: ${item.bounds.widthMm.toInt()} × ${item.bounds.heightMm.toInt()} мм", fontSize = 8.sp, color = Muted) }
                    }
                }
                if (activeSection == "Материалы") {
                    Text("Отделка помещения", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text("Отделка каждой стены", color = Muted, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(WallSide.NORTH to "Север", WallSide.EAST to "Восток", WallSide.SOUTH to "Юг", WallSide.WEST to "Запад").forEach { (side, label) ->
                            val chosen = finishWallSide == side
                            Text(label, fontSize = 8.sp, color = if (chosen) Color.White else Ink,
                                modifier = Modifier.weight(1f).background(if (chosen) Blue else Color(0xFFF0F3F7), RoundedCornerShape(5.dp))
                                    .clickable { finishWallSide = side }.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Text("${when (finishWallSide) { WallSide.NORTH -> "Северная"; WallSide.EAST -> "Восточная"; WallSide.SOUTH -> "Южная"; WallSide.WEST -> "Западная" }} стена", color = Muted, fontSize = 9.sp)
                    Text("Наследовать общую отделку", color = Blue, fontSize = 9.sp,
                        modifier = Modifier.clickable {
                            runCatching {
                                publish(ru.mebel3d.app.core.ProjectEditor.inheritWallFinish(session.project, room.id, finishWallSide))
                                status = "Для выбранной стены используется общая отделка"
                            }.onFailure { status = "Не удалось сбросить отделку: ${it.message}" }
                        }.padding(vertical = 3.dp))
                    ru.mebel3d.app.core.InteriorMaterialLibrary.forUse(ru.mebel3d.app.core.SurfaceUse.WALL).forEach { finish ->
                        val selectedWallMaterial = ru.mebel3d.app.core.RoomSurfaceAppearance.wallMaterialId(room, finishWallSide)
                        Row(Modifier.fillMaxWidth().clickable {
                            runCatching {
                                val updated = ru.mebel3d.app.core.ProjectEditor.applyWallFinish(session.project, room.id, finishWallSide, finish.id)
                                publish(updated)
                                status = "${when (finishWallSide) { WallSide.NORTH -> "Северная"; WallSide.EAST -> "Восточная"; WallSide.SOUTH -> "Южная"; WallSide.WEST -> "Западная" }} стена: ${finish.name}"
                            }.onFailure { status = "Не удалось применить отделку: ${it.message}" }
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(17.dp).background(Color(finish.argb.toInt()), RoundedCornerShape(4.dp))
                                .border(if (selectedWallMaterial == finish.id) 2.dp else 1.dp, if (selectedWallMaterial == finish.id) Blue else Border, RoundedCornerShape(4.dp)))
                            Text(finish.name, fontSize = 9.sp, color = Ink)
                            if (finish.pattern != ru.mebel3d.app.core.SurfacePattern.SOLID) Text("· фактура", fontSize = 8.sp, color = Muted)
                        }
                    }
                    Text("Пол · материалы", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    ru.mebel3d.app.core.InteriorMaterialLibrary.forUse(ru.mebel3d.app.core.SurfaceUse.FLOOR).forEach { finish ->
                        Row(Modifier.fillMaxWidth().clickable {
                            runCatching {
                                val updatedRoom = ru.mebel3d.app.core.RoomSurfaceAppearance.applyFloor(room, finish.id)
                                publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) updatedRoom else it }))
                                status = "Отделка пола: ${finish.name}"
                            }.onFailure { status = "Не удалось применить отделку: ${it.message}" }
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(17.dp).background(Color(finish.argb.toInt()), RoundedCornerShape(4.dp))
                                .border(if (room.floorMaterialId == finish.id) 2.dp else 1.dp, if (room.floorMaterialId == finish.id) Blue else Border, RoundedCornerShape(4.dp)))
                            Text(finish.name, fontSize = 9.sp, color = Ink)
                            if (finish.pattern != ru.mebel3d.app.core.SurfacePattern.SOLID) Text("· фактура", fontSize = 8.sp, color = Muted)
                        }
                    }
                    Text("Потолок · материалы", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    InteriorMaterialLibrary.forUse(SurfaceUse.CEILING).forEach { finish ->
                        Row(Modifier.fillMaxWidth().clickable {
                            runCatching {
                                val updatedRoom = RoomSurfaceAppearance.applyCeiling(room, finish.id)
                                publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) updatedRoom else it }))
                                status = "Отделка потолка: ${finish.name}"
                            }.onFailure { status = "Не удалось применить отделку потолка: ${it.message}" }
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(17.dp).background(Color(finish.argb.toInt()), RoundedCornerShape(4.dp))
                                .border(if (room.ceilingMaterialId == finish.id) 2.dp else 1.dp, if (room.ceilingMaterialId == finish.id) Blue else Border, RoundedCornerShape(4.dp)))
                            Text(finish.name, fontSize = 9.sp, color = Ink)
                            if (finish.pattern != SurfacePattern.SOLID) Text("· фактура", fontSize = 8.sp, color = Muted)
                        }
                    }
                    Text("Освещение сцены", color = Muted, fontSize = 9.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf("Дневной" to 5200, "Тёплый" to 3200, "Нейтральный" to 4200).forEach { (preset, kelvin) ->
                            FilterChip(selected = room.lightingPreset == preset, onClick = {
                                publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) it.copy(lightingPreset = preset, lightTemperatureKelvin = kelvin) else it }))
                            }, label = { Text(preset, fontSize = 8.sp) })
                        }
                    }
                    Text("Яркость · ${room.lightIntensityPercent}%", color = Muted, fontSize = 8.sp)
                    Slider(value = room.lightIntensityPercent.toFloat(), onValueChange = { value ->
                        val v = value.toInt(); publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) it.copy(lightIntensityPercent = v) else it }))
                    }, valueRange = 0f..100f)
                    Text("Температура · ${room.lightTemperatureKelvin} K", color = Muted, fontSize = 8.sp)
                    Slider(value = room.lightTemperatureKelvin.toFloat(), onValueChange = { value ->
                        val v = value.toInt(); publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) it.copy(lightTemperatureKelvin = v) else it }))
                    }, valueRange = 2700f..6500f)
                    Text("Контраст теней · ${room.shadowStrengthPercent}%", color = Muted, fontSize = 8.sp)
                    Slider(value = room.shadowStrengthPercent.toFloat(), onValueChange = { value ->
                        val v = value.toInt(); publish(session.project.copy(rooms = session.project.rooms.map { if (it.id == room.id) it.copy(shadowStrengthPercent = v) else it }))
                    }, valueRange = 0f..100f)
                    Text("Стилизованный предпросмотр света: яркость, цветовая температура и контраст граней. Это не физический расчёт освещения.", color = Muted, fontSize = 8.sp)
                }
                Text("Размеры (мм)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                NumberField("Ширина", width) { width = it }
                NumberField("Глубина", depth) { depth = it }
                NumberField("Высота", height) { height = it }
                if (selectedAssemblyId != null && selectedAssemblyPartId != null) {
                    Text("Деталь сборки · координаты локальные", color = Blue, fontSize = 9.sp)
                    Button(onClick = {
                        runCatching {
                            val updated = ProjectEditor.updateAssemblyPart(
                                session.project, selectedAssemblyId!!, selectedAssemblyPartId!!,
                                width.toInt(), depth.toInt(), height.toInt(),
                                posX.toInt(), posY.toInt(), posZ.toInt(), angle.toInt()
                            )
                            publish(updated); status = "Параметры детали применены"
                        }.onFailure { status = "Изменение детали отклонено: ${it.message}" }
                    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Применить к детали", fontSize = 10.sp) }
                    OutlinedButton(onClick = { runCatching { publish(ProjectEditor.rotateAssemblyPart(session.project, selectedAssemblyId!!, selectedAssemblyPartId!!, 90)); angle = ((angle.toInt()+90)%360).toString() }.onFailure { status = it.message ?: "Ошибка поворота" } }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Повернуть деталь 90°", fontSize = 10.sp) }
                    OutlinedButton(onClick = { selectedAssemblyId = null; selectedAssemblyPartId = null; selected = "Объект не выбран" }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Снять выбор детали", fontSize = 10.sp) }
                    Text("Материал выбранной детали", fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    InteriorMaterialLibrary.forUse(SurfaceUse.FURNITURE).filter { it.family in setOf(SurfaceFamily.WOOD, SurfaceFamily.PAINT, SurfaceFamily.METAL) }.forEach { finish ->
                        Row(Modifier.fillMaxWidth().clickable {
                            runCatching { publish(ProjectEditor.applyAssemblyPartMaterial(session.project, selectedAssemblyId!!, selectedAssemblyPartId!!, finish.id)); status = "Материал детали: ${finish.name}" }
                                .onFailure { status = "Не удалось изменить материал: ${it.message}" }
                        }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(15.dp).background(Color(finish.argb.toInt()), RoundedCornerShape(3.dp)).border(1.dp, Border, RoundedCornerShape(3.dp)))
                            Text(finish.name, fontSize = 8.sp, color = Ink)
                        }
                    }
                }
                selectedItem?.let { item ->
                    Divider(Modifier.padding(vertical = 5.dp))
                    Text("Внешний вид мебели", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text("Готовые стили", color = Muted, fontSize = 9.sp)
                    InteriorCatalogEngine.styles.chunked(2).forEach { styleRow ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            styleRow.forEach { style ->
                                OutlinedButton(onClick = {
                                    runCatching {
                                        val updated = InteriorCatalogEngine.applyStyle(item, style.id)
                                        publish(session.project.copy(furniture = session.project.furniture.map { if (it.id == item.id) updated else it }))
                                        status = "Применён стиль: ${style.name}"
                                    }.onFailure { status = "Не удалось применить стиль: ${it.message}" }
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)) {
                                    Text(style.name, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                            if (styleRow.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    val finishes = listOf(
                        Triple("Дуб натуральный", "wood-oak", 0xFFD8C2A5L),
                        Triple("Орех", "wood-walnut", 0xFF8B6548L),
                        Triple("Белый матовый", "solid-white", 0xFFF2F1ECL),
                        Triple("Графит", "solid-graphite", 0xFF555B63L),
                        Triple("Светло-серый", "solid-grey", 0xFFBFC5CAL),
                        Triple("Песочный", "solid-sand", 0xFFD9C6A5L),
                        Triple("Синий", "solid-blue", 0xFF557FA8L),
                        Triple("Зелёный", "solid-green", 0xFF718B75L),
                        Triple("Ясень", "wood-ash", 0xFFE5D8C2L),
                        Triple("Тёмное дерево", "wood-dark", 0xFF594236L),
                        Triple("Мрамор", "stone-marble", 0xFFE5E1DAL),
                        Triple("Бетон", "stone-concrete", 0xFFAAA9A4L),
                        Triple("Лён", "fabric-linen", 0xFFD8D0C2L),
                        Triple("Серая ткань", "fabric-grey", 0xFF888B8CL),
                        Triple("Оливковая ткань", "fabric-olive", 0xFF777B58L),
                        Triple("Чёрный металл", "metal-black", 0xFF292D31L)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        finishes.chunked(2).forEach { rowFinishes ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowFinishes.forEach { finish ->
                                    val active = item.materialId == finish.second
                                    Row(Modifier.weight(1f).background(if (active) BlueTint else AppBg, RoundedCornerShape(7.dp)).border(if (active) 2.dp else 1.dp, if (active) Blue else Border, RoundedCornerShape(7.dp)).clickable {
                                        runCatching {
                                            val updated = item.copy(materialId = finish.second, colorArgb = finish.third)
                                            publish(session.project.copy(furniture = session.project.furniture.map { if (it.id == item.id) updated else it }))
                                            status = "Материал изменён: ${finish.first}"
                                        }
                                    }.padding(5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(17.dp).background(Color(finish.third.toInt()), RoundedCornerShape(4.dp)).border(1.dp, Border, RoundedCornerShape(4.dp)))
                                        Text(finish.first, fontSize = 8.sp, color = Ink, maxLines = 1)
                                    }
                                }
                                if (rowFinishes.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    Text("Материал сохраняется в проекте и применяется к 3D-модели.", color = Muted, fontSize = 8.sp)
                    val measure = runCatching { ProjectEditor.furnitureDimensions(session.project, item.id) }.getOrNull()
                    if (measure != null) {
                        Text("Габариты после поворота: ${measure.widthMm} × ${measure.depthMm} × ${measure.heightMm} мм", fontSize = 9.sp, color = Muted)
                        Text("До стен: З ${measure.westClearanceMm} · В ${measure.eastClearanceMm} · С ${measure.northClearanceMm} · Ю ${measure.southClearanceMm} мм", fontSize = 9.sp, color = Blue)
                        val nearest = runCatching { SceneAlignmentEngine.measure(session.project, item.id).firstOrNull { it.targetId != null } }.getOrNull()
                        if (nearest != null) Text("Ближайшая мебель: ${nearest.label.removePrefix("До ")} · ${nearest.distanceMm} мм", fontSize = 9.sp, color = Blue)
                    }
                }
                Text("Положение (мм)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                NumberField("X", posX) { posX = it }
                NumberField("Y", posY) { posY = it }
                NumberField("Z", posZ) { posZ = it }
                NumberField("Поворот°", angle) { angle = it }
                Button(onClick = {
                    val old = selectedItem
                    if (old != null) runCatching {
                        var next = ProjectEditor.resizeFurniture(session.project, old.id, width.toInt(), depth.toInt(), height.toInt())
                        next = ProjectEditor.moveFurniture(next, old.id, posX.toInt(), posY.toInt(), posZ.toInt(), gridMm = 1)
                        next = ProjectEditor.rotateFurnitureTo(next, old.id, angle.toInt())
                        publish(next)
                        status = "Размеры и положение применены с проверкой границ и столкновений"
                    }.onFailure { status = "Проверьте размеры: нужны положительные целые числа" }
                    else status = "Сначала добавьте или выберите объект"
                }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Применить размеры", fontSize = 10.sp) }
                OutlinedButton(onClick = {
                    selectedItem?.let { item -> runCatching {
                        publish(ProjectEditor.rotateFurnitureTo(session.project, item.id, item.rotationDeg + 90))
                        angle = ((item.rotationDeg + 90) % 360).toString()
                        status = "Объект повёрнут на 90°"
                    }.onFailure { status = "Поворот отклонён: ${it.message}" } }
                }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Повернуть на 90°", fontSize = 10.sp) }
                OutlinedButton(onClick = {
                    val old = selectedItem
                    if (old != null) { val copy = old.copy(id = "item-${nextObjectId++}", name = "${old.name} (копия)", position = old.position.copy(x = (old.position.x + 300).coerceAtMost(room.widthMm - 1))); publish(session.project.copy(furniture = session.project.furniture + copy)); selectedId = copy.id; status = "Объект дублирован" }
                }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Дублировать", fontSize = 10.sp) }
                OutlinedButton(onClick = { publish(session.project.copy(furniture = session.project.furniture.filterNot { it.id == selectedId })); selectedId = null; status = "Объект удалён" }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) { Text("Удалить", fontSize = 10.sp) }
                Text("${GeometryEngine.validateFurniture(room, visibleFurniture).firstOrNull()?.message ?: "Размещение без замечаний"}", color = if (GeometryEngine.validateFurniture(room, visibleFurniture).isEmpty()) Color(0xFF27834A) else Color(0xFFB45309), fontSize = 9.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(43.dp).panel().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("мм", color = Muted, fontSize = 11.sp); Text("Режим: $mode", color = Muted, fontSize = 11.sp); Text("Выбрано: ${selectedItem?.name ?: "нет"}", color = Muted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("Мебель 3D · новый проект", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun Modifier.panel(): Modifier = this.background(Panel, RoundedCornerShape(13.dp)).border(1.dp, Border, RoundedCornerShape(13.dp))
@Composable private fun TopAction(label: String, onClick: () -> Unit) { OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)) { Text(label, fontSize = 10.sp, maxLines = 1) } }
@Composable private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 10.sp)
        OutlinedTextField(value, onValue, Modifier.width(91.dp).height(43.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp))
    }
}
@Composable private fun RoomObject(glyph: String, name: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.background(if (selected) BlueTint else Color(0xFFF5F0E8), RoundedCornerShape(7.dp)).border(if (selected) 2.dp else 1.dp, if (selected) Blue else Color(0xFFD0C0AC), RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, fontSize = 26.sp); Text(name, fontSize = 10.sp, color = Ink)
    }
}
