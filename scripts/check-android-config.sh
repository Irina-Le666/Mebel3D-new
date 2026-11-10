#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$ROOT" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
manifest = root / "android/app/src/main/AndroidManifest.xml"
paths = root / "android/app/src/main/res/xml/file_paths.xml"
main = root / "android/app/src/main/java/ru/mebel3d/app/MainActivity.kt"
photo_editor = root / "android/app/src/main/java/ru/mebel3d/app/PhotoAnnotationEditor.kt"
photo_plan = root / "android/app/src/main/java/ru/mebel3d/app/core/PhotoDecodePlan.kt"
photo_transfer = root / "android/app/src/main/java/ru/mebel3d/app/core/PhotoWallTransfer.kt"
photo_mapper = root / "android/app/src/main/java/ru/mebel3d/app/core/PhotoWallCoordinateMapper.kt"
session = root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectSession.kt"
history = root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectHistory.kt"
parametric_assembly = root / "android/app/src/main/java/ru/mebel3d/app/core/ParametricAssembly.kt"
parametric_furniture = root / "android/app/src/main/java/ru/mebel3d/app/core/ParametricFurniture.kt"
assembly_diagnostics = root / "android/app/src/main/java/ru/mebel3d/app/core/AssemblyDiagnostics.kt"
canvas = root / "android/app/src/main/java/ru/mebel3d/app/RoomCanvas.kt"
geometry = root / "android/app/src/main/java/ru/mebel3d/app/core/Geometry.kt"
alignment = root / "android/app/src/main/java/ru/mebel3d/app/core/SceneAlignment.kt"
room_layout = root / "android/app/src/main/java/ru/mebel3d/app/core/RoomLayoutPlanner.kt"
module_catalog = root / "android/app/src/main/java/ru/mebel3d/app/core/FurnitureModuleCatalog.kt"
room_composition = root / "android/app/src/main/java/ru/mebel3d/app/core/RoomCompositionPlanner.kt"
room_visualization = root / "android/app/src/main/java/ru/mebel3d/app/core/RoomVisualization.kt"
room_drawings = root / "android/app/src/main/java/ru/mebel3d/app/core/RoomDrawingProjection.kt"
storage = root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectStorage.kt"
gradle = root / "android/app/build.gradle.kts"
ET.parse(manifest); ET.parse(paths)
m = manifest.read_text(); p = paths.read_text(); a = main.read_text(); g = gradle.read_text(); pe = photo_editor.read_text(); pp = photo_plan.read_text(); pt = photo_transfer.read_text(); pm = photo_mapper.read_text(); st = storage.read_text(); se = session.read_text(); hi = history.read_text(); cv = canvas.read_text(); pa = parametric_assembly.read_text(); pf = parametric_furniture.read_text(); ad = assembly_diagnostics.read_text(); geo = geometry.read_text(); al = alignment.read_text(); rl = room_layout.read_text(); mc = module_catalog.read_text(); rc = room_composition.read_text(); rv = room_visualization.read_text(); rd = room_drawings.read_text()
checks = {
    "landscape activity": 'android:screenOrientation="landscape"' in m,
    "non-exported FileProvider": 'android:exported="false"' in m and 'androidx.core.content.FileProvider' in m,
    "cache-only provider path": '<cache-path name="camera_captures" path="." />' in p and '<external-path' not in p,
    "AndroidX Core dependency": 'androidx.core:core-ktx:1.15.0' in g,
    "camera capture contract": 'ActivityResultContracts.TakePicture()' in a,
    "persist camera image under project": 'photo-assets/${session.project.id}/captured' in a,
    "gallery picker retained": 'ActivityResultContracts.OpenDocument()' in a and 'photoPicker.launch(arrayOf("image/*"))' in a,
    "gallery photo copied into app-private project storage": 'photo-assets/${session.project.id}/gallery' in a and 'Uri.fromFile(destination)' in a,
    "gallery import uses shared bounded streaming copy": 'BoundedStreamCopy.copy(source, output, MAX_ROOM_PHOTO_BYTES)' in a,
    "gallery import does not rely on persistent external URI grants": 'takePersistableUriPermission(uri' not in a,
    "photo attachment is verified after project edit": 'Проект отклонил фотографию' in a and 'Проект отклонил снимок' in a,
    "gallery photo IDs are collision-resistant": 'gallery-${java.util.UUID.randomUUID()}' in a,
    "camera photo IDs are collision-resistant": 'camera-${java.util.UUID.randomUUID()}' in a,
    "gallery files are created without overwriting existing photos": 'destination.createNewFile()' in a,
    "camera capture validates source and enforces streaming size limit": 'source.isFile && source.length() > 0L' in a and 'BoundedStreamCopy.copy(input, output, MAX_ROOM_PHOTO_BYTES)' in a,
    "camera files use shared bounded streaming copy": 'BoundedStreamCopy.copy(input, output, MAX_ROOM_PHOTO_BYTES)' in a and 'installedPhoto = destination' in a,
    "shared photo byte limit is centralized": 'private const val MAX_ROOM_PHOTO_BYTES = 25L * 1024L * 1024L' in a,
    "photo preview bounds decode dimensions before allocation": 'inJustDecodeBounds = true' in a and 'inSampleSize = plan.sampleSize' in a,
    "photo annotation retains original pixel coordinates": 'coordinateWidth = photoOriginalWidth' in a and 'coordinateHeight = photoOriginalHeight' in a and 'sourceWidth' in pe and 'sourceHeight' in pe,
    "photo calibration supports cropped wall origin and floor-line anchors": 'wallOriginXPx' in geo and 'floorLineYPx' in geo and 'wallOriginXPx' in geo and 'val reverse = side == WallSide.SOUTH || side == WallSide.WEST' in pm and 'floorLineYPx' in pm,
    "photo calibration anchors are editable and persisted": 'Пиксель X начала стены' in a and 'Пиксель Y линии пола' in a and 'put("wallOriginXPx", photo.wallOriginXPx)' in st and 'put("floorLineYPx", photo.floorLineYPx)' in st,
    "legacy photo projects retain null-anchor behavior": 'photo.wallOriginXPx?.toDouble() ?: if (reverse) calibration else 0.0' in pm and 'photo.floorLineYPx?.toDouble() ?: room.heightMm.toDouble() / scale' in pm,
    "photo decode plan caps long edge at 4096px": 'maxDimension: Int = 4096' in pp and 'while ((maxOf(width, height).toLong() + sample - 1) / sample > maxDimension)' in pp,
    "stored project loads validate the payload ID against its key": 'decodeProject(raw)?.takeIf { it.id == projectId }' in st,
    "stored project loads reject unsupported schema versions": 'require(schema in 1..14)' in st,
    "lighting parameters are persisted with legacy-safe defaults": all(x in st for x in ["lightIntensityPercent", "lightTemperatureKelvin", "shadowStrengthPercent"]) and 'optInt("lightIntensityPercent", 72)' in st and 'optInt("lightTemperatureKelvin", 4200)' in st and 'optInt("shadowStrengthPercent", 35)' in st,
    "lighting controls are range-validated": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectValidator.kt").read_text() for x in ["lightIntensityPercent !in 0..100", "lightTemperatureKelvin !in 2700..6500", "shadowStrengthPercent !in 0..100"]),
    "lighting controls affect walls floor ceiling and furniture": all(x in cv for x in ["illuminate(Color(floorColorArgb.toInt()))", "illuminate(Color(wallFill.toInt()))", "illuminate(Color(ceilingColorArgb.toInt()))", "lightIntensityPercent", "lightTemperatureKelvin", "shadowStrengthPercent"]),
    "lighting values reach the scene and have interactive controls": all(x in a for x in ["lightIntensityPercent = room.lightIntensityPercent", "lightTemperatureKelvin = room.lightTemperatureKelvin", "shadowStrengthPercent = room.shadowStrengthPercent", "Яркость ·", "Температура ·", "Контраст теней ·"]),
    "design variant comparison includes lighting changes": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomDesignVariants.kt").read_text() for x in ["x.lightIntensityPercent", "x.lightTemperatureKelvin", "x.shadowStrengthPercent"]),
    "independent wall finish IDs are persisted": all(x in st for x in ['northWallMaterialId', 'eastWallMaterialId', 'southWallMaterialId', 'westWallMaterialId']),
    "per-wall finish controls and rendering are connected": all(x in a for x in ['finishWallSide', 'applyWallFinish', 'Отделка каждой стены']) and all(x in cv for x in ['northWallMaterialId', 'southWallMaterialId', 'sidePattern']),
    "room wall, floor and ceiling material IDs are persisted": 'wallMaterialId' in st and 'floorMaterialId' in st and 'ceilingMaterialId' in st and 'ceilingColorArgb' in st,
    "ceiling finish is available in the material catalog and UI": 'SurfaceUse.CEILING' in (root / "android/app/src/main/java/ru/mebel3d/app/core/InteriorMaterials.kt").read_text() and 'Отделка потолка' in a,
    "ceiling finish reaches 3D renderer with pattern": 'ceilingMaterialId = room.ceilingMaterialId' in a and 'surfacePattern(ceilingQuad, ceilingPattern' in cv,
    "room surface finish selection is wired to the editor": 'ProjectEditor.applyWallFinish' in a and 'RoomSurfaceAppearance.applyFloor' in a,
    "surface material IDs reach the room renderer": 'wallMaterialId = room.wallMaterialId' in a and 'floorMaterialId = room.floorMaterialId' in a and 'surfacePattern(wallQuad, sidePattern' in cv and 'surfacePattern(floorQuad, floorPattern)' in cv,
    "decoded projects pass whole-project validation before use": ').also(ProjectValidator::requireValid)' in st,
    "project manager hides invalid stored records": 'val decoded = decodeProject(value as String) ?: return@runCatching null' in st,
    "pointer cancellation has a separate UI callback": 'onDragCancel: () -> Unit = {}' in cv and 'onDragCancel = { onDragCancel();' in cv,
    "cancelled pointer gesture rolls back the session": 'onDragCancel = { restoreFromHistory(session.cancelGesture()) }' in a,
    "session gesture cancellation restores history transaction": 'fun cancelGesture(): ProjectModel' in se and 'history.cancelTransaction()' in se,
    "history transaction preserves undo and redo branches": 'fun beginTransaction()' in hi and 'fun cancelTransaction()' in hi and 'transactionRedo' in hi,
    "parametric dimensions have a shared upper bound": 'MAX_MM: Int = 100_000' in pf and 'INVALID_DIMENSION_RANGE' in pa and 'INVALID_DIMENSION_RANGE' in pf,
    "carcass generator uses Long for intermediate dimensions": '2L * spec.panelThicknessMm' in pa and 'spec.shelfCount.toLong()' in pa,
    "rear panel is sized inside carcass walls and shelves": 'safeInnerW, spec.backThicknessMm' in pa and 'innerH.toInt()' in pa,
    "included rear panel rejects zero thickness": 'spec.includeBack && spec.backThicknessMm <= 0' in pa,
    "assembly diagnostics use Long for vertical and fastener arithmetic": 'val maxZ: Long' in ad and 'fastenerDiameterMm.toLong() * 2L' in ad,
    "furniture hit testing uses the same centre origin as rendering": 'addBoxHit(item.id, item.position.x.toFloat(), item.position.y.toFloat()' in cv and 'item.position.x + item.size.width/2f' not in cv[cv.index('furniture.forEach { item ->', cv.index('detectTapGestures')):cv.index('room.engineering.forEach', cv.index('detectTapGestures'))],
    "furniture vertical validation avoids Int overflow": 'a.position.z.toLong() < b.position.z.toLong() + b.size.height.toLong()' in geo,
    "room opening validation uses Long arithmetic": 'opening.offsetMm.toLong() + opening.widthMm.toLong()' in geo and 'opening.sillHeightMm.toLong() + opening.heightMm.toLong()' in geo,
    "wall-mounted engineering markers are normalized to their declared wall": 'fun wallPosition(room: RoomModel, side: WallSide, alongMm: Int, elevationMm: Int)' in geo and 'GeometryEngine.wallPosition(room, side, along' in a,
    "engineering marker plaques use bounded wall-local geometry": 'fun engineeringMarkerCorners(' in rv and 'px !in 0L..room.widthMm.toLong()' in rv and 'pz !in 0L..room.heightMm.toLong()' in rv,
    "3D canvas renders engineering plaques on wall planes": 'engineeringMarkerCorners(room, element)' in cv and 'val plaque = corners.map' in cv and 'val selected = element.id == selectedEngineeringId' in cv,
    "wall selection pre-fills the matching boundary coordinate": 'along.coerceIn(0, room.wallLength(side))' in a and 'engineeringY = point.y.toString()' in a,
    "photo transfer and opening projection use the same north-south wall planes": 'WallSide.NORTH -> Vec3Mm(along, 0, elevation)' in pt and 'WallSide.SOUTH -> Vec3Mm(along, room.depthMm, elevation)' in pt and 'WallSide.NORTH -> listOf(Vec3Mm(a, 0, low)' in rv and 'WallSide.SOUTH -> listOf(Vec3Mm(a, room.depthMm, low)' in rv,
    "room photo transfer imports all calibrated annotated photos in one action": 'fun transferAll(project: ProjectModel, roomId: String)' in pt and 'PhotoWallTransfer.transferAll(session.project, room.id)' in a and 'skippedPhotoIds' in pt,
    "wall elevation splitting uses bounded Long intermediate coordinates": 'var cursor = 0L' in geo and 'val topStart = opening.sillHeightMm.toLong() + opening.heightMm.toLong()' in geo,
    "scene alignment uses Long bounds and rejects overflow instead of wrapping": 'data class Bounds(val left: Long' in al and 'fun exactInt(value: Long' in al and 'x in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()' in al,
    "furniture scene transform rotates local geometry around the canonical footprint centre": 'localXmm - item.size.width / 2f' in (root / "android/app/src/main/java/ru/mebel3d/app/core/FurnitureSceneTransform.kt").read_text() and 'item.position.x + dx * cos(angle)' in (root / "android/app/src/main/java/ru/mebel3d/app/core/FurnitureSceneTransform.kt").read_text(),
    "canvas converts room-space furniture vertices to local coordinates before rotation": 'px - (x - hw)' in cv and 'py - (y - hd)' in cv,
    "3D drag handles use the furniture footprint centre": 'selectedFurniture.position.x - room.widthMm / 2f' in cv and 'selectedFurniture.position.y - room.depthMm / 2f' in cv,
    "selected furniture dimension handles use the same canonical centre": 'p(measuredItem.position.x.toFloat(), measuredItem.position.y.toFloat()' in cv,
    "room layout rejects a narrow aisle as an invalid plan": '"NARROW_AISLE"' in rl and '"INVALID_REQUEST", "NARROW_AISLE"' in rl,
    "room layout uses Long for module sums and opening intervals": 'sumOf { it.widthMm.toLong() }' in rl and 'val openingStart = opening.offsetMm.toLong()' in rl and 'openingStart + opening.widthMm.toLong()' in rl,
    "room layout checks aisle and footprint with Long coordinates": 'p.center.x.toLong()' in rl and 'request.minimumAisleMm.toLong()' in rl,
    "assembly validator permits bounded negative XY overhang but not negative elevation": 'part.localPosition.x.toLong() !in -coordinateLimit..coordinateLimit' in geo and 'part.localPosition.y.toLong() !in -coordinateLimit..coordinateLimit' in geo and 'part.localPosition.z !in 0..ParametricDimensionLimits.MAX_MM' in geo,
    "assembly diagnostics distinguish XY overhang from below-floor elevation": 'Negative X/Y are valid' in ad and 'part.localPosition.z < 0' in ad,
    "individual countertop dimensions are bounded before allocation": 'spec.widthMm.toLong() + 2L * spec.countertopOverhangMm' in mc and 'spec.depthMm.toLong() + spec.countertopOverhangMm' in mc,
    "furniture run aggregate width is calculated in Long": 'spec.modules.sumOf { it.widthMm.toLong() }' in mc and 'totalWidthLong > maxDimension' in mc,
    "continuous countertop dimensions are bounded before Int conversion": 'totalWidthLong + 2L * spec.countertopOverhangMm' in mc and 'maxDepthMm' not in mc,
    "wall placements orient rear panels onto each room wall": 'WallSide.NORTH -> 180' in rl and 'WallSide.EAST -> 270' in rl and 'WallSide.SOUTH -> 0' in rl and 'WallSide.WEST -> 90' in rl and 'origin is lower-left-front' in (root / "android/app/src/main/java/ru/mebel3d/app/core/ParametricAssembly.kt").read_text() and '+Y runs toward the rear panel' in (root / "android/app/src/main/java/ru/mebel3d/app/core/ParametricAssembly.kt").read_text(),
    "wall planner stores an exact transformed assembly origin": 'originX = cursor + spec.widthMm.toLong()' in rl and 'originX = room.widthMm.toLong() - gap - depth' in rl and 'Vec3Mm(originX.toInt(), originY.toInt(), 0)' in rl,
    "applying a quarter-turn transforms each part centre and its orientation": '-y - partDepth / 2L - partWidth / 2L' in rl and 'y + partDepth / 2L - partWidth / 2L' in rl and 'rotationDeg = angle' in rl,
    "assembly placement bounds are checked before Int conversion": 'originZ in 0L..ParametricDimensionLimits.MAX_MM.toLong()' in rl and 'nextX in -ParametricDimensionLimits.MAX_MM.toLong()' in rl,
    "furniture run failures are atomic and retain requested footprint": 'if (issues.isNotEmpty()) return FurnitureRunResult(' in mc and 'emptyList(), issues, totalWidthLong.toInt()' in mc and 'Advance by the requested module footprint' in mc,
    "generated module assemblies are validated before inclusion in a run": 'GeometryEngine.validateAssembly(placed)' in mc and 'INVALID_GENERATED_GEOMETRY' in mc,
    "continuous countertop is validated before returning a run": 'GeometryEngine.validateAssembly(withCountertop)' in mc and 'INVALID_COUNTERTOP_GEOMETRY' in mc,
    "manufacturing cut list is built from the final successful assemblies": 'val cuts = assemblies.flatMap { GeometryEngine.cutList(it) }' in mc and 'totalWidthLong.toInt()' in mc,
    "custom rear panel material reaches carcass geometry": 'backMaterialId = spec.backMaterialId' in mc and 'spec.backMaterialId' in pa,
    "countertop overhang is placed on front and both sides": 'Vec3Mm(-spec.countertopOverhangMm, -spec.countertopOverhangMm' in mc and '+Y runs toward the rear panel' in pa,
    "composition material area follows actual cut-list board faces": 'GeometryEngine.cutList(it.assembly)' in rc and 'it.widthMm.toDouble() * it.lengthMm.toDouble()' in rc and 'groupBy { it.materialId }' in rc,
    "3D scene exposes adjustable camera elevation": 'cameraPitch: Float = 32f' in cv and 'cameraPitch by remember { mutableFloatStateOf(32f) }' in a,
    "3D scene projection uses the same pitch for floor depth and object height": 'sin(cameraPitch * PI.toFloat() / 180f)' in cv and 'cos(cameraPitch * PI.toFloat() / 180f)' in cv,
    "camera gesture supports vertical orbit and zoom": 'nextPitch = (cameraPitch - pan.y * 0.08f).coerceIn(5f, 85f)' in cv and 'onCameraChange(nextYaw, nextPitch, nextZoom)' in cv,
    "camera presets are available in the workspace": 'TopAction("Сверху") { cameraPitch = 85f }' in a and 'TopAction("На уровне") { cameraPitch = 12f }' in a,
    "3D furniture resizing accounts for camera elevation": 'cameraPitchDeg: Float = 32f' in (root / "android/app/src/main/java/ru/mebel3d/app/core/FurnitureResizeMath.kt").read_text() and 'item.rotationDeg, cameraPitch' in cv,
    "3D height handles account for camera elevation": 'cameraPitchDeg: Float = 32f' in (root / "android/app/src/main/java/ru/mebel3d/app/core/FurnitureTransformMath.kt").read_text() and 'cameraPitchDeg = cameraPitch' in cv,
    "3D furniture rendering uses camera-space far-to-near painter order": 'furnitureBackToFront(room, painterCamera, furniture)' in cv and 'fun furnitureBackToFront' in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomVisualization.kt").read_text(),
    "parametric parts render in camera-space depth order": 'partDepth(room, painterCamera, assembly, part)' in cv and 'painterParts.forEach' in cv,
    "3D walls are split around actual door and window openings": 'RoomVisualizationEngine.wallPanels(wallLengthMm, room.heightMm, openings)' in cv and 'data class WallPanelRect' in rv,
    "wall panel projection handles all four wall planes": 'drawWall(WallSide.NORTH, room.widthMm, northWallMaterialId)' in cv and 'drawWall(WallSide.SOUTH, room.widthMm, southWallMaterialId)' in cv and 'drawWall(WallSide.WEST, room.depthMm, westWallMaterialId)' in cv and 'drawWall(WallSide.EAST, room.depthMm, eastWallMaterialId)' in cv,
    "wall opening subtraction uses bounded Long coordinates": 'opening.offsetMm.toLong()' in rv and 'opening.sillHeightMm.toLong() + opening.heightMm.toLong()' in rv and 'distinct().sorted()' in rv,
    "opening geometry uses one canonical projection for all four walls": 'fun openingCorners(room: RoomModel, opening: WallOpening): List<Vec3Mm>?' in rv and 'WallSide.NORTH -> listOf(Vec3Mm(a, 0, low)' in rv and 'WallSide.EAST -> listOf(Vec3Mm(room.widthMm, a, low)' in rv,
    "door and glazing overlays remain transparent inside true wall cutouts": 'openingFillOpacity(opening.kind, selectedOpening)' in cv and 'kind == OpeningKind.WINDOW -> 0.10f' in rv and 'else -> 0.045f' in rv,
    "opening hit testing uses the same canonical wall-space corners as rendering": 'RoomVisualizationEngine.openingCorners(room, item)' in cv and 'val centre = Vec3Mm(' in cv,
    "non-blocking room planning diagnostics cover doors windows and engineering access": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomPlanningDiagnostics.kt").read_text() for x in ["BLOCKED_DOOR", "OBSTRUCTED_WINDOW", "BLOCKED_ENGINEERING_ACCESS", "fun inspect("]),
    "room workspace surfaces unified access and geometry warnings without blocking project edits": 'RoomReadinessAnalyzer.analyze(room, visibleFurniture)' in a and 'roomReport.nextActions' in a and 'Доступ к помещению:' not in a,
    "planning diagnostics have dedicated regression coverage": (root / "build-check/RoomPlanningDiagnosticsSmoke.kt").is_file(),
    "unified room readiness analyzer combines geometry, access and photo coverage": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomReadinessReport.kt").read_text() for x in ["RoomReadinessAnalyzer", "GeometryEngine.validateRoom", "GeometryEngine.validateFurniture", "RoomPlanningDiagnostics.inspect", "RoomPhotoCoverageAnalyzer.analyze"]),
    "workspace presents unified room readiness findings and next actions": "RoomReadinessAnalyzer.analyze(room, visibleFurniture)" in a and "roomReport.nextActions" in a and "Ошибки:" in a and "Предупреждения:" in a,
    "photo import preview counts are visible in the room workspace": "roomReport.photoTransferPreview" in a and "Фоторазметка: готово" in a and "plannedElementCount" in a,
    "unified readiness findings are non-blocking and uniquely identified": "never blocks editing, saving or exporting" in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomReadinessReport.kt").read_text() and "distinctBy { it.id }" in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomReadinessReport.kt").read_text(),
    "unified readiness analyzer has dedicated scenario regression coverage": (root / "build-check/RoomReadinessReportSmoke.kt").is_file(),
    "photo import preview uses the same room-wide transfer pipeline without mutating project": all(x in pt for x in ["data class PhotoTransferPreview", "fun preview(project: ProjectModel, roomId: String)", "transferAll(project, roomId)"]),
    "unified readiness report surfaces importable and unresolved photo annotations": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomReadinessReport.kt").read_text() for x in ["PhotoWallTransfer.preview", "PHOTO_TRANSFER", "ignoredAnnotationCount", "plannedElementCount"]),
    "photo transfer preview has regression coverage for prediction, skip reasons and immutability": all(x in (root / "build-check/PhotoWallTransferSmoke.kt").read_text() for x in ["PhotoWallTransfer.preview", "preview never mutates", "preview groups skipped photos"]),
    "photo coverage and annotation transfer share one canonical coordinate mapper": all(x in pm for x in ["object PhotoWallCoordinateMapper", "fun coverageInterval(", "fun annotationBounds(", "WallSide.SOUTH", "WallSide.WEST"]) and "PhotoWallCoordinateMapper.coverageInterval" in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomPhotoCoverage.kt").read_text() and "PhotoWallCoordinateMapper.annotationBounds" in pt,
    "photo coordinate mapper has dedicated north south east west and crop regression coverage": (root / "build-check/PhotoWallCoordinateMapperSmoke.kt").is_file() and all(x in (root / "build-check/PhotoWallCoordinateMapperSmoke.kt").read_text() for x in ["WallSide.NORTH", "WallSide.EAST", "WallSide.SOUTH", "WallSide.WEST", "cropped wall origin", "fractional scale"]),
    "photo-derived geometry records stable source provenance": all(x in geo for x in ["sourcePhotoId", "sourceAnnotationId"]) and all(x in st for x in ["put(\"sourcePhotoId\"", "put(\"sourceAnnotationId\""]),
    "photo annotation edits reconcile imported room geometry": "synchronizePhotoAnnotations" in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectEditor.kt").read_text() and "fun synchronize(project: ProjectModel" in pt,
    "photo annotation, calibration and wall reassignment update imported geometry in UI": all(x in a for x in ["synchronizePhotoAnnotations(calibrated", "synchronizePhotoAnnotations(edited", "synchronizePhotoAnnotations(reassigned"]),
    "photo sync is covered for moved, deleted and type-changed marks": all(x in (root / "build-check/PhotoWallTransferSmoke.kt").read_text() for x in ["follows edited annotation", "detaches rather than deletes", "type-changed annotation", "engineering point follows edited source"]),
    "plan and four wall elevations derive from one room-space projection": all(x in rd for x in ["fun plan(room: RoomModel", "fun elevation(room: RoomModel", "WallSide.values().associateWith", "RoomPlanProjection", "WallElevationProjection"]),
    "dimension chains close to exact wall lengths": "if (start > cursor)" in rd and "if (cursor < length)" in rd and "DrawingDimension(\"Остаток стены\"" in rd,
    "drawing plan uses the same rotated furniture footprint centre as the 3D scene": "item.position.x + x * c - y * s" in rd and "item.position.y + x * s + y * c" in rd,
    "elevations include wall openings and correctly attached engineering points": "validOpening(room, it)" in rd and "it.wallSide == side" in rd and "onWall(room, it, side)" in rd,
    "drawing workspace exposes plan, four schematic wall views and room dimensions": all(x in a for x in ["RoomDrawingProjection.build(room, visibleFurniture)", "drawingWallSide", "Вид стены"]),
    "room drawing projection has dedicated cross-wall regression scenarios": (root / "build-check/RoomDrawingProjectionSmoke.kt").is_file(),
    "drawing workspace renders live plan and elevation preview": (root / "android/app/src/main/java/ru/mebel3d/app/RoomDrawingPreview.kt").is_file() and "RoomDrawingPreview(" in a and "onSelectFurniture = { id ->" in a,
    "schematic drawing preview supports selection from plan and elevation": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/RoomDrawingPreview.kt").read_text() for x in ["pointerInput(plan, elevation, onSelectFurniture, onSelectOpening, onSelectEngineering)", "RoomDrawingSelection.selectPlanFurniture", "RoomDrawingSelection.selectElevationFurniture", "selectedFurnitureId"]),
    "schematic selection updates the shared 3D furniture selection and dimension fields": all(x in a for x in ["onSelectFurniture = { id ->", "selectedId = item.id", "mode = \"3D\"", "width = item.size.width.toString()", "angle = item.rotationDeg.toString()"]),
    "drawing selection geometry has regression coverage": (root / "build-check/RoomDrawingSelectionSmoke.kt").is_file(),
    "schematic opening movement validates wall boundaries and overlap": "fun moveOpening(" in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectEditor.kt").read_text() and "return updateOpening(project, roomId, moved)" in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectEditor.kt").read_text(),
    "engineering marker movement preserves wall attachment and room bounds": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectEditor.kt").read_text() for x in ["fun moveEngineeringElement(", "fun moveEngineeringOnWall(", "WallSide.NORTH -> Vec3Mm(x, 0, z)", "coerceIn(0, room.heightMm)"]),
    "schematic movement has regression coverage for plan and elevation projections": "moveEngineeringOnWall" in (root / "build-check/RoomDrawingSelectionSmoke.kt").read_text() and "moveOpening(project" in (root / "build-check/RoomDrawingSelectionSmoke.kt").read_text(),
    "opening editor provides direct along-wall position nudges": 'Сдвиг вдоль стены' in a and 'openingOffset.toIntOrNull() ?: 0) + 100' in a,
    "engineering editor provides bounded XYZ position nudges": all(x in a for x in ['Быстрое позиционирование · шаг 100 мм', 'engineeringX.toIntOrNull() ?: 0) + 100).coerceAtMost(room.widthMm)', 'engineeringY.toIntOrNull() ?: 0) + 100).coerceAtMost(room.depthMm)', 'engineeringZ.toIntOrNull() ?: 0) + 100).coerceAtMost(room.heightMm)']),
    "formal multi-sheet drawing package is not exposed or compiled": not (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomDrawingPackageExporter.kt").exists() and "Комплект: план + 4 стены · ZIP" not in a,
    "schematic plan selects wall openings and engineering points": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomDrawingSelection.kt").read_text() for x in ["selectPlanOpening", "selectPlanEngineering"]),
    "schematic wall elevation selects openings and engineering points": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomDrawingSelection.kt").read_text() for x in ["selectElevationOpening", "selectElevationEngineering"]),
    "drawing preview visually highlights selected opening and engineering point": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/RoomDrawingPreview.kt").read_text() for x in ["selectedOpeningId", "selectedEngineeringId", "selectedOpeningPaint", "selectedEngineeringPaint"]),
    "schematic selection opens editors for the same room opening and engineering element": all(x in a for x in ["onSelectOpening = { id ->", "editingOpeningId = opening.id", "onSelectEngineering = { id ->", "editingEngineeringId = element.id", "showEngineeringEditor = true"]),
    "interior arrangement planner preserves furniture geometry and produces preview suggestions": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/RoomFurnitureArrangementPlanner.kt").read_text() for x in ["RoomFurnitureArrangementPlanner", "FurniturePlacementSuggestion", "GeometryEngine.validateFurniture", "RoomPlanningDiagnostics.inspect"]),
    "arrangement plan applies atomically and rejects stale or incomplete previews": all(x in (root / "android/app/src/main/java/ru/mebel3d/app/core/ProjectEditor.kt").read_text() for x in ["fun applyFurnitureArrangement", "plan.isComplete", "byId.keys == roomItems.map { it.id }.toSet()"]),
    "arrangement planner is accessible from the planning menu": all(x in a for x in ["Умная расстановка", "Подобрать расстановку", "arrangementPreview", "ProjectEditor.applyFurnitureArrangement"]),
    "arrangement preview reports unresolved items and never silently resizes furniture": all(x in a for x in ["preview.unresolvedFurnitureIds", "Размеры и повороты мебели не меняются"]),
    "arrangement planner has regression coverage for collisions access zones and atomic apply": (root / "build-check/RoomFurnitureArrangementPlannerSmoke.kt").is_file() and all(x in (root / "build-check/RoomFurnitureArrangementPlannerSmoke.kt").read_text() for x in ["collision-free", "door and engineering access", "atomically apply preview", "larger than room"]),
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"ANDROID CONFIG CONTRACT: {len(checks)}/{len(checks)} checks passed")
PY

# Photo-derived geometry keeps provenance so annotation edits can be reconciled safely.
grep -q 'sourcePhotoId' android/app/src/main/java/ru/mebel3d/app/core/Geometry.kt
grep -q 'fun synchronize(project: ProjectModel' android/app/src/main/java/ru/mebel3d/app/core/PhotoWallTransfer.kt
grep -q 'sourceAnnotationId' android/app/src/main/java/ru/mebel3d/app/core/ProjectStorage.kt
