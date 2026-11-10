package ru.mebel3d.app.core

/** Bounds memory use when opening large room photos while retaining original pixel coordinates. */
data class PhotoDecodePlan(
    val originalWidth: Int,
    val originalHeight: Int,
    val sampleSize: Int,
    val decodedWidthUpperBound: Int,
    val decodedHeightUpperBound: Int
) {
    companion object {
        fun create(width: Int, height: Int, maxDimension: Int = 4096): PhotoDecodePlan {
            require(width > 0 && height > 0) { "Размеры фотографии должны быть положительными" }
            require(maxDimension >= 256) { "Слишком малый размер предпросмотра" }
            var sample = 1
            // Either dimension can dominate a panorama; stop when the larger side fits.
            while ((maxOf(width, height).toLong() + sample - 1) / sample > maxDimension) sample *= 2
            return PhotoDecodePlan(
                originalWidth = width,
                originalHeight = height,
                sampleSize = sample,
                decodedWidthUpperBound = ((width.toLong() + sample - 1) / sample).toInt(),
                decodedHeightUpperBound = ((height.toLong() + sample - 1) / sample).toInt()
            )
        }
    }
}
