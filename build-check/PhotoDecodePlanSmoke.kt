import ru.mebel3d.app.core.PhotoDecodePlan

fun main() {
    val normal = PhotoDecodePlan.create(1920, 1080)
    check(normal.sampleSize == 1 && normal.originalWidth == 1920)
    val huge = PhotoDecodePlan.create(12000, 9000)
    check(huge.sampleSize == 4)
    check(huge.decodedWidthUpperBound <= 4096 && huge.decodedHeightUpperBound <= 4096)
    check(huge.originalWidth == 12000 && huge.originalHeight == 9000)
    val panorama = PhotoDecodePlan.create(20000, 2000)
    check(panorama.sampleSize == 8 && panorama.decodedWidthUpperBound <= 4096)
    check(runCatching { PhotoDecodePlan.create(0, 100) }.isFailure)
    println("PhotoDecodePlanSmoke: 5 checks passed")
}
