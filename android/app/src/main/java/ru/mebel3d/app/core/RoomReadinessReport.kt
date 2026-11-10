package ru.mebel3d.app.core

/** Unified, advisory overview of room capture and interior-planning quality. */
enum class RoomFindingSeverity { ERROR, WARNING, INFO }
enum class RoomFindingCategory { ROOM_GEOMETRY, FURNITURE_GEOMETRY, ACCESS, PHOTO_COVERAGE, PHOTO_CALIBRATION, PHOTO_TRANSFER }

data class RoomReadinessFinding(
    val id: String,
    val severity: RoomFindingSeverity,
    val category: RoomFindingCategory,
    val message: String,
    val relatedId: String? = null,
    val suggestedAction: String? = null
)

data class RoomReadinessReport(
    val findings: List<RoomReadinessFinding>,
    val photoTransferPreview: PhotoTransferPreview? = null
) {
    val errorCount: Int get() = findings.count { it.severity == RoomFindingSeverity.ERROR }
    val warningCount: Int get() = findings.count { it.severity == RoomFindingSeverity.WARNING }
    val infoCount: Int get() = findings.count { it.severity == RoomFindingSeverity.INFO }
    val isGeometricallyValid: Boolean get() = errorCount == 0
    val hasCompletePhotoCoverage: Boolean get() = findings.none { it.category == RoomFindingCategory.PHOTO_COVERAGE && it.severity == RoomFindingSeverity.WARNING }
    val nextActions: List<RoomReadinessFinding> get() = findings
        .filter { it.suggestedAction != null }
        .sortedWith(compareBy<RoomReadinessFinding> { it.severity.ordinal }.thenBy { it.category.ordinal })
        .distinctBy { it.id }
        .take(5)
}

/**
 * Combines existing room, furniture, access and photo analyzers without changing the project.
 * Findings are advisory: this report never blocks editing, saving or exporting a design.
 */
object RoomReadinessAnalyzer {
    fun analyze(room: RoomModel, furniture: List<FurnitureItem>): RoomReadinessReport {
        val findings = mutableListOf<RoomReadinessFinding>()

        GeometryEngine.validateRoom(room).forEach { issue ->
            findings += RoomReadinessFinding(
                id = "room:${issue.code}:${issue.objectId.orEmpty()}",
                severity = RoomFindingSeverity.ERROR,
                category = RoomFindingCategory.ROOM_GEOMETRY,
                message = issue.message,
                relatedId = issue.objectId,
                suggestedAction = "Исправьте параметры помещения или расположение элемента"
            )
        }

        GeometryEngine.validateFurniture(room, furniture).forEachIndexed { index, issue ->
            findings += RoomReadinessFinding(
                id = "furniture:${issue.code}:${issue.objectId.orEmpty()}:$index",
                severity = RoomFindingSeverity.ERROR,
                category = RoomFindingCategory.FURNITURE_GEOMETRY,
                message = issue.message,
                relatedId = issue.objectId,
                suggestedAction = "Переместите, поверните или измените размер объекта"
            )
        }

        RoomPlanningDiagnostics.inspect(room, furniture).forEach { advice ->
            findings += RoomReadinessFinding(
                id = "access:${advice.kind}:${advice.objectId}:${advice.relatedId}",
                severity = RoomFindingSeverity.WARNING,
                category = RoomFindingCategory.ACCESS,
                message = advice.message,
                relatedId = advice.relatedId,
                suggestedAction = "Проверьте свободный подход и при необходимости измените расстановку"
            )
        }

        val coverage = RoomPhotoCoverageAnalyzer.analyze(room)
        coverage.walls.forEach { wall ->
            when {
                wall.photoCount == 0 -> findings += RoomReadinessFinding(
                    id = "photo:missing:${wall.side}", severity = RoomFindingSeverity.WARNING,
                    category = RoomFindingCategory.PHOTO_COVERAGE,
                    message = "Для стены ${wall.side} нет фотографий",
                    suggestedAction = "Добавьте фотографию стены ${wall.side}"
                )
                wall.uncoveredLengthMm > 0 -> findings += RoomReadinessFinding(
                    id = "photo:gaps:${wall.side}", severity = RoomFindingSeverity.WARNING,
                    category = RoomFindingCategory.PHOTO_COVERAGE,
                    message = "Стена ${wall.side}: не снято ${wall.uncoveredLengthMm} мм",
                    suggestedAction = wall.largestUncoveredGap?.let { "Снимите участок ${it.startMm}–${it.endMm} мм стены ${wall.side}" }
                )
            }
            if (wall.uncalibratedPhotoCount > 0) findings += RoomReadinessFinding(
                id = "photo:uncalibrated:${wall.side}", severity = RoomFindingSeverity.WARNING,
                category = RoomFindingCategory.PHOTO_CALIBRATION,
                message = "На стене ${wall.side} есть фото без калибровки",
                suggestedAction = "Укажите известный размер и масштаб фотографии стены ${wall.side}"
            )
        }
        coverage.unassignedPhotoIds.forEach { id -> findings += RoomReadinessFinding(
            id = "photo:unassigned:$id", severity = RoomFindingSeverity.WARNING,
            category = RoomFindingCategory.PHOTO_CALIBRATION,
            message = "Фотография $id не привязана к стене", relatedId = id,
            suggestedAction = "Назначьте фотографии одну из четырёх стен"
        ) }
        coverage.scaleMismatchWallSides.forEach { side -> findings += RoomReadinessFinding(
            id = "photo:scale-mismatch:$side", severity = RoomFindingSeverity.WARNING,
            category = RoomFindingCategory.PHOTO_CALIBRATION,
            message = "Масштаб фотографий стены $side заметно различается",
            suggestedAction = "Перепроверьте калибровку фотографий стены $side"
        ) }

        // Preview the same conversion pipeline used by the explicit import action.
        // This surfaces marks that cannot become room geometry before the user imports them.
        val transferPreview = runCatching { PhotoWallTransfer.preview(ProjectModel("readiness", "Проверка", listOf(room)), room.id) }.getOrNull()
        if (transferPreview != null) {
            if (transferPreview.skippedPhotoCount > 0) findings += RoomReadinessFinding(
                id = "photo:transfer:skipped", severity = RoomFindingSeverity.WARNING,
                category = RoomFindingCategory.PHOTO_TRANSFER,
                message = "Не готовы к переносу ${transferPreview.skippedPhotoCount} фото с отметками",
                suggestedAction = "Назначьте фото стенам и завершите калибровку"
            )
            if (transferPreview.ignoredAnnotationCount > 0) findings += RoomReadinessFinding(
                id = "photo:transfer:ignored", severity = RoomFindingSeverity.WARNING,
                category = RoomFindingCategory.PHOTO_TRANSFER,
                message = "Отметок не получится перенести: ${transferPreview.ignoredAnnotationCount}",
                suggestedAction = "Проверьте границы отметок, привязку к полу и типы элементов на фото"
            )
            if (transferPreview.plannedElementCount > 0) findings += RoomReadinessFinding(
                id = "photo:transfer:ready", severity = RoomFindingSeverity.INFO,
                category = RoomFindingCategory.PHOTO_TRANSFER,
                message = "По откалиброванным фото можно создать элементов: ${transferPreview.plannedElementCount}"
            )
        }

        if (findings.isEmpty()) findings += RoomReadinessFinding(
            id = "room:looks-good", severity = RoomFindingSeverity.INFO,
            category = RoomFindingCategory.ROOM_GEOMETRY,
            message = "Ошибок геометрии и замечаний по доступу не обнаружено"
        )
        return RoomReadinessReport(findings.distinctBy { it.id }, transferPreview)
    }
}
