package com.deepseek.harness

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

object RootfsExtractor {

    fun extract(context: Context): Boolean {
        return try {
            val destRoot = File(context.filesDir, "rootfs")
            destRoot.mkdirs()
            context.assets.open("rootfs.tar.gz").use { input ->
                GZIPInputStream(input).use { gz ->
                    extractTar(gz, destRoot)
                }
            }
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    private fun extractTar(input: java.io.InputStream, destRoot: File) {
        val buf = ByteArray(512)
        var pendingName: String? = null
        var totalBytes = 0L

        while (true) {
            val read = readFully(input, buf)
            if (read == 0) break
            if (read < 512) throw java.io.IOException("truncated tar")
            if (buf.all { it == 0.toByte() }) continue

            var name = String(buf, 0, 100, Charsets.UTF_8).trimEnd('\u0000'.code.toChar(), ' ')
            val sizeStr = String(buf, 124, 12, Charsets.US_ASCII).trim()
            val size = if (sizeStr.isEmpty()) 0L else sizeStr.toLong(8)
            val type = buf[156].toInt().toChar()

            val dataBlocks = ((size + 511) / 512).toInt()
            val isFileType = type == '0' || type == '\u0000' || type == '7'
            val isDir = type == '5'
            val isLink = type == '2'

            when {
                type == 'L' -> {
                    pendingName = readString(input, size)
                    skipPadding(input, size)
                    continue
                }
                type == 'x' || type == 'g' -> {
                    skipData(input, size)
                    continue
                }
                isDir -> {
                    File(destRoot, sanitize(name)).mkdirs()
                }
                isLink -> {
                    val target = String(buf, 157, 100, Charsets.UTF_8).trimEnd('\u0000'.code.toChar(), ' ')
                    val link = File(destRoot, sanitize(pendingName ?: name))
                    link.parentFile?.mkdirs()
                    try {
                        android.system.Os.symlink(target, link.absolutePath)
                    } catch (_: Throwable) {
                        // symlink not supported on this device/fs; skip
                    }
                }
                isFileType -> {
                    val outName = sanitize(pendingName ?: name)
                    pendingName = null
                    if (outName.isEmpty()) {
                        skipData(input, size)
                        continue
                    }
                    val outFile = File(destRoot, outName)
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        copyN(input, out, size)
                    }
                    totalBytes += size
                }
                else -> skipData(input, size)
            }
            skipPadding(input, size)
        }
    }

    private fun sanitize(name: String): String {
        var n = name.removePrefix("./")
        while (n.startsWith("../")) n = n.removePrefix("../")
        return n
    }

    private fun readString(input: java.io.InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000'.code.toChar(), ' ')
    }

    private fun copyN(input: java.io.InputStream, out: java.io.OutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(65536)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) throw java.io.IOException("unexpected EOF")
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun skipData(input: java.io.InputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(65536)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) throw java.io.IOException("unexpected EOF")
            remaining -= n
        }
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val pad = (512 - (size % 512)) % 512
        if (pad > 0) skipData(input, pad)
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}
