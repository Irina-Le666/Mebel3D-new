package ru.mebel3d.app.core

fun main() {
    var checks = 0
    fun verify(value: Boolean, label: String) { check(value) { label }; checks++ }
    val empty = RoomModel(widthMm = 5000, depthMm = 3500, heightMm = 2700)
    val emptyReport = RoomReadinessAnalyzer.analyze(empty, emptyList())
    verify(emptyReport.errorCount == 0, "an empty room has no geometry errors")
    verify(emptyReport.warningCount == 4, "each missing wall photo is reported")
    verify(!emptyReport.hasCompletePhotoCoverage, "missing photos mean coverage is incomplete")
    verify(emptyReport.nextActions.size == 4, "capture actions are surfaced")

    val room = empty.copy(
        openings = listOf(WallOpening("door", WallSide.NORTH, 1000, 900, 0, 2100, OpeningKind.DOOR)),
        engineering = listOf(EngineeringElement("water", "Вода", EngineeringKind.WATER, Vec3Mm(2500, 0, 500), WallSide.NORTH, 20)),
        photos = listOf(
            RoomPhotoReference("north", "file:///north.jpg", WallSide.NORTH, 2500, 1000, 1000, 700),
            RoomPhotoReference("unknown", "file:///unknown.jpg", annotations = listOf(PhotoAnnotation("unknown-outlet", PhotoAnnotationKind.OUTLET, 10, 10, 20, 20)))
        )
    )
    val blocked = FurnitureItem("cabinet", "Шкаф", Size3Mm(700, 700, 2200), Vec3Mm(1450, 350))
    val report = RoomReadinessAnalyzer.analyze(room, listOf(blocked))
    verify(report.findings.any { it.category == RoomFindingCategory.ACCESS && it.relatedId == "door" }, "door clearance warning is included")
    verify(report.findings.any { it.category == RoomFindingCategory.PHOTO_CALIBRATION && it.relatedId == "unknown" }, "unassigned photo warning is included")
    verify(report.findings.any { it.category == RoomFindingCategory.PHOTO_COVERAGE && it.relatedId == null }, "missing wall coverage is included")
    verify(report.findings.any { it.category == RoomFindingCategory.PHOTO_COVERAGE && it.message.contains("не снято") }, "partial photo coverage is included")
    verify(report.findings.any { it.category == RoomFindingCategory.PHOTO_TRANSFER && it.message.contains("Не готовы к переносу") }, "unready annotated photos appear in the unified report")
    verify(report.nextActions.size <= 5, "next actions are bounded")
    verify(report.findings.map { it.id }.distinct().size == report.findings.size, "finding identifiers are unique")

    val outside = blocked.copy(id = "outside", position = Vec3Mm(4900, 3400))
    val geometry = RoomReadinessAnalyzer.analyze(empty, listOf(outside))
    verify(geometry.errorCount > 0, "out-of-room furniture is an error")
    verify(geometry.findings.any { it.category == RoomFindingCategory.FURNITURE_GEOMETRY }, "furniture geometry category is retained")

    val goodRoom = empty.copy(photos = WallSide.values().map { side ->
        RoomPhotoReference("photo-$side", "file:///$side.jpg", side, empty.wallLength(side), 1000, 1000, 700)
    })
    val good = RoomReadinessAnalyzer.analyze(goodRoom, emptyList())
    verify(good.hasCompletePhotoCoverage, "four calibrated full-width wall photos complete coverage")
    verify(good.warningCount == 0 && good.errorCount == 0, "complete empty room has no warnings")
    verify(good.findings.single().severity == RoomFindingSeverity.INFO, "clean state has a neutral informational finding")

    val transferRoom = empty.copy(photos = listOf(
        RoomPhotoReference("transfer-photo", "file:///transfer.jpg", WallSide.NORTH, 5000, 5000, 5000, 2700,
            listOf(PhotoAnnotation("outlet", PhotoAnnotationKind.OUTLET, 100, 2200, 80, 80),
                PhotoAnnotation("other", PhotoAnnotationKind.OTHER, 300, 100, 50, 50)))
    ))
    val transferReport = RoomReadinessAnalyzer.analyze(transferRoom, emptyList())
    verify(transferReport.findings.any { it.category == RoomFindingCategory.PHOTO_TRANSFER && it.message.contains("можно создать") }, "unified report previews importable photo elements")
    verify(transferReport.findings.any { it.category == RoomFindingCategory.PHOTO_TRANSFER && it.message.contains("не получится перенести") }, "unified report warns about unsupported photo marks")
    println("RoomReadinessReportSmoke: $checks checks passed")
}
