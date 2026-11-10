import ru.mebel3d.app.core.*

fun main() {
    val large = SceneHitTesting.bounds(listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f))!!
    val small = SceneHitTesting.bounds(listOf(40f to 40f, 60f to 40f, 60f to 60f, 40f to 60f))!!
    check(SceneHitTesting.score("large", 50f, 50f, large).inside)
    check(SceneHitTesting.score("small", 50f, 50f, small).inside)
    check(SceneHitTesting.choose(listOf(SceneHitTesting.score("large", 50f, 50f, large), SceneHitTesting.score("small", 50f, 50f, small))) == "small")
    check(SceneHitTesting.choose(listOf(SceneHitTesting.score("large", 110f, 50f, large))) == "large")
    check(SceneHitTesting.choose(listOf(SceneHitTesting.score("large", 140f, 50f, large))) == null)
    check(SceneHitTesting.choose(emptyList()) == null)
    check(SceneHitTesting.bounds(emptyList()) == null)
    check(runCatching { ScreenRect(10f, 0f, 0f, 10f) }.isFailure)
    check(runCatching { SceneHitTesting.choose(emptyList(), -1f) }.getOrNull() == null)
    val padded = SceneHitTesting.bounds(listOf(10f to 10f, 20f to 20f), 5f)!!
    check(padded.left == 5f && padded.bottom == 25f)
    println("SceneHitTestingSmoke: 10 checks passed")
}
