package ru.mebel3d.app.core

import java.io.InputStream
import java.io.OutputStream

/** Copies a stream while enforcing its byte limit during the read, not from metadata. */
object BoundedStreamCopy {
    fun copy(input: InputStream, output: OutputStream, maxBytes: Long, bufferSize: Int = 16 * 1024): Long {
        require(maxBytes > 0) { "Лимит копирования должен быть положительным" }
        require(bufferSize > 0) { "Размер буфера должен быть положительным" }
        val buffer = ByteArray(bufferSize)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                // InputStream implementations are allowed to make no progress on a bulk read.
                // Fall back to a one-byte read so a broken/atypical stream cannot spin forever.
                val single = input.read()
                if (single < 0) break
                require(total < maxBytes) { "Размер данных превышает допустимый лимит" }
                output.write(single)
                total++
                continue
            }
            // Subtraction avoids overflow if this utility is ever used with Long.MAX_VALUE.
            require(count.toLong() <= maxBytes - total) {
                "Размер данных превышает допустимый лимит"
            }
            output.write(buffer, 0, count)
            total += count.toLong()
        }
        return total
    }
}
