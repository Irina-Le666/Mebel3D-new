package ru.mebel3d.app.core

/** Stable key conventions shared by project persistence and its JVM tests. */
object ProjectStorageKeys {
    private fun hex(value: String): String = value.toByteArray(Charsets.UTF_8)
        .joinToString("") { byte -> "%02x".format(byte) }

    fun project(id: String): String {
        require(id.isNotBlank())
        return "project_$id"
    }

    fun variantsMetadata(projectId: String): String {
        require(projectId.isNotBlank())
        return "design_variants_$projectId"
    }

    /** Logical ID passed to save()/loadProject(); project() adds the physical key prefix. */
    fun variantSnapshotPrefix(projectId: String): String {
        require(projectId.isNotBlank())
        return "variant-${hex(projectId)}-"
    }

    fun variantSnapshot(projectId: String, variantId: String): String {
        require(variantId.isNotBlank())
        return variantSnapshotPrefix(projectId) + hex(variantId)
    }

    /** Actual SharedPreferences key produced when save() persists a snapshot ID. */
    fun variantSnapshotStoragePrefix(projectId: String): String =
        project(variantSnapshotPrefix(projectId))

    /** Physical prefix used by the first snapshot implementation (kept for cleanup/migration). */
    fun legacyVariantSnapshotStoragePrefix(projectId: String): String =
        "project_project_variant-${hex(projectId)}-"

    fun isUserProjectKey(key: String): Boolean {
        if (!key.startsWith("project_")) return false
        val suffix = key.removePrefix("project_")
        return !suffix.startsWith("variant-") && !suffix.startsWith("project_variant-")
    }

    /** All persistence entries owned by a project, including its hidden variant snapshots. */
    fun deletionKeys(projectId: String, existingKeys: Set<String>): Set<String> = buildSet {
        add(project(projectId))
        add(variantsMetadata(projectId))
        val prefixes = listOf(
            variantSnapshotStoragePrefix(projectId),
            legacyVariantSnapshotStoragePrefix(projectId)
        )
        existingKeys.filterTo(this) { key -> prefixes.any(key::startsWith) }
    }
}
