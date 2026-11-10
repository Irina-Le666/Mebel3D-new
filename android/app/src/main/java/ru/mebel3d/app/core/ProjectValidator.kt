package ru.mebel3d.app.core

/** Whole-project consistency checks shared by editing, persistence and export workflows. */
object ProjectValidator {
    fun validate(project: ProjectModel): List<GeometryIssue> {
        val issues = mutableListOf<GeometryIssue>()
        if (project.id.isBlank() || project.name.isBlank())
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "У проекта должны быть ID и название")
        if (project.rooms.isEmpty()) {
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "В проекте должно быть хотя бы одно помещение")
            return issues
        }
        if (project.rooms.map { it.id }.distinct().size != project.rooms.size)
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Идентификаторы помещений повторяются")
        if (project.rooms.none { it.id == project.activeRoomId })
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Активное помещение отсутствует в проекте")

        project.rooms.forEach { room ->
            issues += GeometryEngine.validateRoom(room)
            if (room.lightIntensityPercent !in 0..100 || room.lightTemperatureKelvin !in 2700..6500 || room.shadowStrengthPercent !in 0..100) {
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Параметры освещения вне допустимого диапазона", room.id)
            }
            room.wallMaterialId?.let { id ->
                InteriorMaterialLibrary.validate(id, SurfaceUse.WALL)?.let {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, it, room.id)
                }
            }
            listOfNotNull(room.northWallMaterialId, room.eastWallMaterialId, room.southWallMaterialId, room.westWallMaterialId).forEach { id ->
                InteriorMaterialLibrary.validate(id, SurfaceUse.WALL)?.let {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, it, room.id)
                }
            }
            room.floorMaterialId?.let { id ->
                InteriorMaterialLibrary.validate(id, SurfaceUse.FLOOR)?.let {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, it, room.id)
                }
            }
            room.ceilingMaterialId?.let { id ->
                InteriorMaterialLibrary.validate(id, SurfaceUse.CEILING)?.let {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, it, room.id)
                }
            }
            fun checkIds(ids: List<String>, label: String) {
                if (ids.any { it.isBlank() } || ids.distinct().size != ids.size)
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Пустые или повторяющиеся ID: $label", room.id)
            }
            checkIds(room.openings.map { it.id }, "проёмы")
            checkIds(room.engineering.map { it.id }, "инженерные элементы")
            checkIds(room.photos.map { it.id }, "фотографии")
            room.photos.forEach { photo ->
                val hasLength = photo.knownLengthMm != null
                val hasPixels = photo.calibrationPixels != null
                val hasImageWidth = photo.imageWidthPx != null
                val hasImageHeight = photo.imageHeightPx != null
                if (photo.uri.isBlank() || hasLength != hasPixels ||
                    (hasLength && (photo.knownLengthMm!! <= 0 || photo.calibrationPixels!! <= 0)) ||
                    hasImageWidth != hasImageHeight ||
                    (hasImageWidth && (photo.imageWidthPx!! <= 0 || photo.imageHeightPx!! <= 0))) {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING, "У фотографии некорректная ссылка, размеры изображения или калибровка", photo.id)
                }
                if (photo.annotations.map { it.id }.distinct().size != photo.annotations.size || photo.annotations.any { it.id.isBlank() }) {
                    issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING, "У фоторазметки пустые или повторяющиеся ID", photo.id)
                }
                photo.wallOriginXPx?.let { origin ->
                    val width = photo.imageWidthPx
                    if (width != null && (origin.toLong() < -width.toLong() * 4L || origin.toLong() > width.toLong() * 4L)) {
                        issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING, "Начало стены слишком далеко от границ фотографии", photo.id)
                    }
                }
                photo.floorLineYPx?.let { floor ->
                    val height = photo.imageHeightPx
                    if (floor < 0 || (height != null && floor > height)) {
                        issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING, "Линия пола находится вне фотографии", photo.id)
                    }
                }
                photo.annotations.forEach { mark ->
                    val validRect = mark.xPx >= 0 && mark.yPx >= 0 && mark.widthPx > 0 && mark.heightPx > 0 &&
                        (photo.imageWidthPx == null || mark.xPx.toLong() + mark.widthPx <= photo.imageWidthPx.toLong()) &&
                        (photo.imageHeightPx == null || mark.yPx.toLong() + mark.heightPx <= photo.imageHeightPx.toLong())
                    if (!validRect) {
                        issues += GeometryIssue(GeometryIssueCode.INVALID_ENGINEERING, "Отметка выходит за границы фотографии или имеет неверный размер", mark.id.ifBlank { photo.id })
                    }
                }
            }
        }
        if (project.furniture.map { it.id }.distinct().size != project.furniture.size || project.furniture.any { it.id.isBlank() })
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "ID мебели пусты или повторяются")
        project.furniture.forEach { item ->
            if (project.rooms.none { it.id == item.roomId })
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "Мебель привязана к отсутствующему помещению", item.id)
        }
        project.rooms.forEach { room ->
            issues += GeometryEngine.validateFurniture(room, project.furniture.filter { it.roomId == room.id })
        }
        if (project.assemblies.map { it.id }.distinct().size != project.assemblies.size || project.assemblies.any { it.id.isBlank() })
            issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "ID сборок пусты или повторяются")
        project.assemblies.forEach { assembly ->
            issues += GeometryEngine.validateAssembly(assembly)
            if (assembly.name.isBlank()) {
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "У сборки должно быть название", assembly.id)
            }
            if (assembly.parts.any { it.id.isBlank() || it.name.isBlank() }) {
                issues += GeometryIssue(GeometryIssueCode.INVALID_ASSEMBLY, "У деталей сборки должны быть ID и названия", assembly.id)
            }
            // Manufacturing warnings (edge distance, material thickness, isolated parts) remain
            // visible to diagnostics but do not block ordinary project editing or saving.
            AssemblyDiagnosticsEngine.inspect(assembly)
                .filter { it.severity == AssemblyDiagnosticSeverity.ERROR }
                .forEach { diagnostic ->
                    issues += GeometryIssue(
                        GeometryIssueCode.INVALID_ASSEMBLY,
                        diagnostic.message,
                        assembly.id
                    )
                }
        }
        return issues
    }

    fun requireValid(project: ProjectModel): ProjectModel {
        val issues = validate(project)
        require(issues.isEmpty()) { issues.joinToString("; ") { it.message } }
        return project
    }
}
