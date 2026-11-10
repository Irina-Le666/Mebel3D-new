import ru.mebel3d.app.core.ProjectStorageKeys

fun main() {
    check(ProjectStorageKeys.project("room-a") == "project_room-a")
    check(ProjectStorageKeys.variantsMetadata("room-a") == "design_variants_room-a")
    val logicalPrefix = ProjectStorageKeys.variantSnapshotPrefix("room-a")
    val logicalSnapshot = ProjectStorageKeys.variantSnapshot("room-a", "concept-1")
    check(logicalSnapshot.startsWith(logicalPrefix))
    check(logicalSnapshot != ProjectStorageKeys.variantSnapshot("room-b", "concept-1"))
    // save(snapshot.copy(id = logicalSnapshot)) calls project(logicalSnapshot),
    // so verify the physical key, not merely the logical ID.
    val snapshotKey = ProjectStorageKeys.project(logicalSnapshot)
    val otherSnapshotKey = ProjectStorageKeys.project(ProjectStorageKeys.variantSnapshot("room-b", "concept-1"))
    check(ProjectStorageKeys.isUserProjectKey("project_room-a"))
    check(!ProjectStorageKeys.isUserProjectKey(snapshotKey))
    check(!ProjectStorageKeys.isUserProjectKey("project_project_variant-${"726f6f6d2d61"}-6c6567616379"))
    check(!ProjectStorageKeys.isUserProjectKey("design_variants_room-a"))
    val legacySnapshotKey = ProjectStorageKeys.legacyVariantSnapshotStoragePrefix("room-a") + "6c6567616379"
    val cleanup = ProjectStorageKeys.deletionKeys("room-a", setOf(snapshotKey, legacySnapshotKey, otherSnapshotKey, "project_room-a", "project_room-b"))
    check(cleanup == setOf("project_room-a", "design_variants_room-a", snapshotKey, legacySnapshotKey))
    check(otherSnapshotKey !in cleanup && "project_room-b" !in cleanup)
    check(runCatching { ProjectStorageKeys.project("") }.isFailure)
    println("ProjectStorageKeys smoke: 13 checks passed")
}
