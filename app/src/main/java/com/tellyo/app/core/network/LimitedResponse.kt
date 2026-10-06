package com.tellyo.app.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.ResponseBody

/** Enforces the limit while reading, including chunked responses and gzip expansion. */
internal fun InputStream.readLimited(limit: Long): ByteArray {
    require(limit in 0..Int.MAX_VALUE.toLong())
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), limit - total + 1).toInt())
        if (count < 0) break
        total += count
        if (total > limit) throw IOException("Response exceeds $limit bytes")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun ResponseBody.readLimitedBytes(limit: Long): ByteArray {
    if (contentLength() > limit) throw IOException("Response exceeds $limit bytes")
    return byteStream().use { it.readLimited(limit) }
}
