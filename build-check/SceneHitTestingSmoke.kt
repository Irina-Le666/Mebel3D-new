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

    val diamond = listOf(50f to 0f, 100f to 50f, 50f to 100f, 0f to 50f)
    val diamondCenter = SceneHitTesting.scorePolygon("diamond", 50f, 50f, diamond)!!
    check(diamondCenter.inside)
    val emptyCorner = SceneHitTesting.scorePolygon("diamond", 5f, 5f, diamond)!!
    check(!emptyCorner.inside && emptyCorner.distanceSquared > 100f)
    val projectedBox = listOf(20f to 40f, 80f to 60f, 70f to 100f, 30f to 80f,
        40f to 10f, 100f to 30f, 90f to 70f, 50f to 50f)
    check(SceneHitTesting.scorePolygon("box", 60f, 50f, projectedBox)!!.inside)
    check(SceneHitTesting.scorePolygon("bad", 0f, 0f, listOf(1f to 1f, Float.NaN to 2f, 3f to 3f)) == null)
    println("SceneHitTestingSmoke: polygon selection checks passed")
}
