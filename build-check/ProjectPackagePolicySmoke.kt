import ru.mebel3d.app.core.ProjectPackagePolicy

fun main() {
    ProjectPackagePolicy.validateEntryName("project.json")
    ProjectPackagePolicy.validateEntryName("photos/wall-1.bin")
    val roomOnePhoto = ProjectPackagePolicy.photoEntryName(0, 0, "photo-1")
    val roomTwoPhoto = ProjectPackagePolicy.photoEntryName(1, 0, "photo-1")
    check(roomOnePhoto != roomTwoPhoto) // IDs may legitimately be scoped to individual rooms.
    ProjectPackagePolicy.validateEntryName(roomOnePhoto)
    ProjectPackagePolicy.validateEntryName(roomTwoPhoto)
    ProjectPackagePolicy.validateEntryName(ProjectPackagePolicy.photoEntryName(2, 4, "фото № 1"))
    check(runCatching { ProjectPackagePolicy.photoEntryName(-1, 0, "photo") }.isFailure)
    check(runCatching { ProjectPackagePolicy.photoEntryName(0, 0, "") }.isFailure)
    ProjectPackagePolicy.validateEntrySize("project.json", 128)
    ProjectPackagePolicy.validateEntrySize("photos/wall-1.bin", 1024)
    ProjectPackagePolicy.validatePackage(1152, 1, true)
    ProjectPackagePolicy.validateAssetReferences(setOf("photos/wall-1.bin"), setOf("photos/wall-1.bin"))
    check(ProjectPackagePolicy.referencedBundleEntries(listOf("bundle:photos/wall-1.bin", "file:///private/photo.bin")) == setOf("photos/wall-1.bin"))
    check(ProjectPackagePolicy.referencedBundleEntries(listOf("file:///private/photo.bin")).isEmpty())

    check(runCatching { ProjectPackagePolicy.validateEntryName("../escape.bin") }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateEntryName("photos/../../escape.bin") }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateEntryName("unknown.txt") }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateEntrySize("photos/huge.bin", ProjectPackagePolicy.MAX_PHOTO_BYTES + 1) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateEntrySize("project.json", ProjectPackagePolicy.MAX_PROJECT_BYTES + 1) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validatePackage(ProjectPackagePolicy.MAX_PACKAGE_BYTES + 1, 0, true) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validatePackage(100, 0, false) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validatePackage(100, ProjectPackagePolicy.MAX_PHOTO_COUNT + 1, true) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateAssetReferences(setOf("photos/missing.bin"), emptySet()) }.isFailure)
    check(runCatching { ProjectPackagePolicy.validateAssetReferences(emptySet(), setOf("photos/orphan.bin")) }.isFailure)
    println("ProjectPackagePolicySmoke: OK")
}
