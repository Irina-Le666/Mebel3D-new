import ru.mebel3d.app.core.*

fun main() {
    val part = FurniturePart("shelf", "Полка", Size3Mm(500, 400, 18), Vec3Mm(10, 20, 100), 350)
    val assembly = FurnitureAssembly("cab", "Шкаф", Vec3Mm(0, 0, 0), listOf(part))
    val gesture = AssemblyPartGestureTransform.begin(assembly, part, 100f, 0f)
    check(AssemblyPartGestureTransform.angleAt(gesture, 0f, -100f) == 260)
    check(AssemblyPartGestureTransform.angleAt(gesture, 100f, 0f) == 350)
    check(AssemblyPartGestureTransform.angleAt(gesture, 0f, 100f, 15) == 75)
    check(AssemblyPartGestureTransform.heightAt(gesture, -10f, 0.1f, true, maxHeightMm = 2400) > 18)
    check(AssemblyPartGestureTransform.heightAt(gesture, -100000f, 0.1f, true, maxHeightMm = 2400) == 2400)
    check(AssemblyPartGestureTransform.heightAt(gesture, 100000f, 0.1f, true, maxHeightMm = 2400) == 1)
    check(runCatching { AssemblyPartGestureTransform.heightAt(gesture, 1f, 0f, true, maxHeightMm = 2400) }.isFailure)
    check(runCatching { AssemblyPartGestureTransform.angleAt(gesture, 1f, 0f, 0) }.isFailure)
    println("AssemblyPartGestureTransformSmoke: 8 checks passed")
}
