import ru.mebel3d.app.core.BoundedStreamCopy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

fun main() {
    val payload = ByteArray(40_000) { (it % 251).toByte() }
    val output = ByteArrayOutputStream()
    check(BoundedStreamCopy.copy(ByteArrayInputStream(payload), output, payload.size.toLong()) == payload.size.toLong())
    check(output.toByteArray().contentEquals(payload))

    val partial = ByteArrayOutputStream()
    check(runCatching { BoundedStreamCopy.copy(ByteArrayInputStream(payload), partial, 100) }.isFailure)
    check(partial.size() <= 100)

    // Some stream wrappers can return 0 from a bulk read despite a non-empty request.
    val zeroThenData = object : InputStream() {
        private var first = true
        private val delegate = ByteArrayInputStream(byteArrayOf(7, 8, 9))
        override fun read(): Int = delegate.read()
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (first) { first = false; return 0 }
            return delegate.read(bytes, offset, length)
        }
    }
    val recovered = ByteArrayOutputStream()
    check(BoundedStreamCopy.copy(zeroThenData, recovered, 3, 8) == 3L)
    check(recovered.toByteArray().contentEquals(byteArrayOf(7, 8, 9)))

    // A stream that repeatedly reports zero for bulk reads must still make progress via read().
    val zeroAlways = object : InputStream() {
        private var next = 1
        override fun read(): Int = if (next <= 3) next++ else -1
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
    }
    val zeroOutput = ByteArrayOutputStream()
    check(BoundedStreamCopy.copy(zeroAlways, zeroOutput, 3, 4) == 3L)
    check(zeroOutput.toByteArray().contentEquals(byteArrayOf(1, 2, 3)))
    check(runCatching { BoundedStreamCopy.copy(zeroAlways, ByteArrayOutputStream(), 3, 0) }.isFailure)
    check(runCatching { BoundedStreamCopy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0) }.isFailure)
    check(BoundedStreamCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 1) == 0L)
    println("BoundedStreamCopySmoke: OK")
}
