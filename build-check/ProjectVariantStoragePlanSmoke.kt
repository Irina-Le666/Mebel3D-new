import ru.mebel3d.app.core.ProjectStorageKeys
import ru.mebel3d.app.core.ProjectVariantStoragePlanner

fun main() {
    val plan = ProjectVariantStoragePlanner.plan(
        projectId = "kitchen-main",
        variantIds = listOf("white", "oak"),
        previousLogicalSnapshotIds = listOf(
            ProjectStorageKeys.variantSnapshot("kitchen-main", "white"),
            ProjectStorageKeys.variantSnapshot("kitchen-main", "oak"),
            ProjectStorageKeys.variantSnapshot("kitchen-main", "old"),
            ProjectStorageKeys.variantSnapshot("bedroom", "keep")
        )
    )
    check(plan.entries.size == 2)
    check(plan.entries.map { it.variantId } == listOf("white", "oak"))
    check(plan.entries[0].storageKey == ProjectStorageKeys.project(plan.entries[0].logicalSnapshotId))
    check(plan.obsoleteStorageKeys == setOf(ProjectStorageKeys.project(ProjectStorageKeys.variantSnapshot("kitchen-main", "old"))))
    check(plan.obsoleteStorageKeys.none { it.contains("bedroom") })
    check(runCatching { ProjectVariantStoragePlanner.plan("kitchen", listOf("same", "same"), emptyList()) }.isFailure)
    check(runCatching { ProjectVariantStoragePlanner.plan("bad id", listOf("v1"), emptyList()) }.isFailure)
    check(runCatching { ProjectVariantStoragePlanner.plan("kitchen", listOf(""), emptyList()) }.isFailure)
    println("ProjectVariantStoragePlan smoke: 8 checks passed")
}
