package ru.mebel3d.app.core

/** Prevents importing a package from silently replacing an existing local project. */
object ProjectImportIdentity {
    private val validId = Regex("[A-Za-z0-9._-]+")

    fun resolve(requestedId: String, existingIds: Set<String>, uniqueSuffix: String): String {
        require(requestedId.isNotBlank() && requestedId.length <= 128 && validId.matches(requestedId)) {
            "Идентификатор проекта содержит недопустимые символы"
        }
        if (requestedId !in existingIds) return requestedId
        require(uniqueSuffix.isNotBlank() && uniqueSuffix.all { it.isLetterOrDigit() || it == '-' }) {
            "Некорректный суффикс копии проекта"
        }
        val suffix = "-copy-$uniqueSuffix"
        val base = requestedId.take((128 - suffix.length).coerceAtLeast(1))
        var candidate = base + suffix
        var counter = 2
        while (candidate in existingIds) {
            val numbered = "$suffix-$counter"
            candidate = requestedId.take((128 - numbered.length).coerceAtLeast(1)) + numbered
            counter++
        }
        return candidate
    }
}
