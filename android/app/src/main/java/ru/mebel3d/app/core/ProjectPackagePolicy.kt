package ru.mebel3d.app.core

/** Pure limits and path rules for portable .mebel3d packages. Kept outside Android storage so
 * archive validation can be regression-tested on the JVM. */
object ProjectPackagePolicy {
    const val MAX_PROJECT_BYTES: Long = 20L * 1024 * 1024
    const val MAX_PHOTO_BYTES: Long = 25L * 1024 * 1024
    const val MAX_PACKAGE_BYTES: Long = 100L * 1024 * 1024
    const val MAX_PHOTO_COUNT: Int = 1000

    fun validateEntryName(name: String) {
        require(name.isNotBlank() && name.length <= 256) { "Недопустимое имя файла в архиве" }
        require(!name.startsWith('/') && !name.contains("..") && !name.contains('\\')) {
            "Архив содержит небезопасный путь"
        }
        require(name == "project.json" || name.matches(Regex("photos/[A-Za-z0-9._-]+\\.bin"))) {
            "Неизвестный файл в архиве: $name"
        }
    }

    fun validateEntrySize(name: String, size: Long) {
        require(size >= 0) { "Некорректный размер файла в архиве" }
        when (name) {
            "project.json" -> require(size in 1..MAX_PROJECT_BYTES) {
                "Описание проекта отсутствует или превышает лимит 20 МБ"
            }
            else -> require(size in 1..MAX_PHOTO_BYTES) {
                "Фотография превышает лимит 25 МБ или пуста"
            }
        }
    }

    fun validatePackage(totalBytes: Long, photoCount: Int, hasProject: Boolean) {
        require(hasProject) { "В архиве отсутствует project.json" }
        require(totalBytes in 1..MAX_PACKAGE_BYTES) { "Архив проекта превышает лимит 100 МБ" }
        require(photoCount in 0..MAX_PHOTO_COUNT) { "Слишком много фотографий в проекте" }
    }

    /** Stable archive entry for one photo occurrence. Photo IDs are room-scoped, so the room
     * and photo indices are included to prevent collisions when separate rooms reuse an ID. */
    fun photoEntryName(roomIndex: Int, photoIndex: Int, photoId: String): String {
        require(roomIndex >= 0 && photoIndex >= 0) { "Некорректный индекс фотографии" }
        require(photoId.isNotBlank()) { "У фотографии отсутствует идентификатор" }
        val safeId = photoId.map { char ->
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "._-") char else '_'
        }.joinToString("").take(80).ifBlank { "photo" }
        return "photos/r${roomIndex + 1}-p${photoIndex + 1}-$safeId.bin"
    }

    /** Collects portable asset paths before import rewrites bundle: URIs to local file URIs. */
    fun referencedBundleEntries(photoUris: Iterable<String>): Set<String> = photoUris
        .filter { it.startsWith("bundle:") }
        .map { it.removePrefix("bundle:") }
        .toSet()

    fun validateAssetReferences(referencedEntries: Set<String>, actualEntries: Set<String>) {
        require(referencedEntries.all { it.startsWith("photos/") }) { "Некорректная ссылка на фотографию" }
        require(referencedEntries == actualEntries) {
            "Архив содержит отсутствующие или неиспользуемые фотографии"
        }
    }
}
