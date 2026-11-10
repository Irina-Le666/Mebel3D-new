package ru.mebel3d.app.core

/** Pure planning for replacing one project's persisted design-variant set. */
data class ProjectVariantStorageEntry(
    val variantId: String,
    val logicalSnapshotId: String,
    val storageKey: String
)

data class ProjectVariantStoragePlan(
    val entries: List<ProjectVariantStorageEntry>,
    val obsoleteStorageKeys: Set<String>
)

object ProjectVariantStoragePlanner {
    fun plan(projectId: String, variantIds: List<String>, previousLogicalSnapshotIds: List<String>): ProjectVariantStoragePlan {
        require(projectId.isNotBlank() && projectId.length <= 128)
        require(projectId.matches(Regex("[A-Za-z0-9._-]+")))
        require(variantIds.all { it.isNotBlank() && it.length <= 128 })
        require(variantIds.distinct().size == variantIds.size) { "Повторяется идентификатор варианта" }

        val entries = variantIds.map { variantId ->
            val logicalId = ProjectStorageKeys.variantSnapshot(projectId, variantId)
            ProjectVariantStorageEntry(
                variantId = variantId,
                logicalSnapshotId = logicalId,
                storageKey = ProjectStorageKeys.project(logicalId)
            )
        }
        val retained = entries.mapTo(mutableSetOf()) { it.logicalSnapshotId }
        val ownedPrefix = ProjectStorageKeys.variantSnapshotPrefix(projectId)
        val obsolete = previousLogicalSnapshotIds.asSequence()
            .filter { it.startsWith(ownedPrefix) && it !in retained }
            .map(ProjectStorageKeys::project)
            .toSet()
        return ProjectVariantStoragePlan(entries, obsolete)
    }
}
