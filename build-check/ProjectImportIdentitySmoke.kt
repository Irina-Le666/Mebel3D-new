import ru.mebel3d.app.core.ProjectImportIdentity

fun main() {
    check(ProjectImportIdentity.resolve("kitchen", emptySet(), "123") == "kitchen")
    check(ProjectImportIdentity.resolve("kitchen", setOf("kitchen"), "123") == "kitchen-copy-123")
    check(ProjectImportIdentity.resolve("kitchen", setOf("kitchen", "kitchen-copy-123"), "123") == "kitchen-copy-123-2")
    val longId = "a".repeat(128)
    val copied = ProjectImportIdentity.resolve(longId, setOf(longId), "987654")
    check(copied.length <= 128 && copied.startsWith("a") && copied != longId)
    check(runCatching { ProjectImportIdentity.resolve("../bad", emptySet(), "1") }.isFailure)
    check(runCatching { ProjectImportIdentity.resolve("ok", setOf("ok"), "bad/suffix") }.isFailure)
    println("ProjectImportIdentitySmoke: OK")
}
