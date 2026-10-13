package com.wifichat.mesh.network

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Length-prefixed framing for a byte stream (an RFCOMM socket). Each frame is a
 * 4-byte big-endian length followed by that many bytes. This turns a stream,
 * which has no message boundaries, into discrete packets.
 *
 * Frames larger than [maxFrame] are rejected as corrupt, which also bounds the
 * memory a hostile peer can make us allocate.
 */
object FrameCodec {

    private const val LENGTH_BYTES = 4

    @Throws(IOException::class)
    fun writeFrame(out: OutputStream, bytes: ByteArray, maxFrame: Int) {
        if (bytes.size > maxFrame) throw IOException("frame too large: ${bytes.size}")
        val len = ByteArray(LENGTH_BYTES)
        len[0] = (bytes.size ushr 24).toByte()
        len[1] = (bytes.size ushr 16).toByte()
        len[2] = (bytes.size ushr 8).toByte()
        len[3] = bytes.size.toByte()
        out.write(len)
        out.write(bytes)
        out.flush()
    }

    /**
     * Reads one frame. Returns null on a clean end-of-stream (peer closed).
     * Throws IOException on a corrupt or over-large frame.
     */
    @Throws(IOException::class)
    fun readFrame(inp: InputStream, maxFrame: Int): ByteArray? {
        val len = readFully(inp, LENGTH_BYTES) ?: return null
        val size = ((len[0].toInt() and 0xFF) shl 24) or
            ((len[1].toInt() and 0xFF) shl 16) or
            ((len[2].toInt() and 0xFF) shl 8) or
            (len[3].toInt() and 0xFF)
        if (size < 0 || size > maxFrame) throw IOException("bad frame length: $size")
        return readFully(inp, size) ?: throw EOFException("truncated frame")
    }

    private fun readFully(inp: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = inp.read(buf, read, n - read)
            if (r < 0) {
                if (read == 0) return null
                throw EOFException("stream closed mid-frame")
            }
            read += r
        }
        return buf
    }
}
