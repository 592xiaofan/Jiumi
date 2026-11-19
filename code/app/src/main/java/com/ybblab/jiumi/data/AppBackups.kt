// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Installation files of apps deleted from here, kept so they can be installed again. Only the APKs
 * are kept: an app's own data lives where nothing but the app (or root) can read it.
 *
 * Backups sit in this app's external files, backups/<package>/, where the monitor (as the shell)
 * can read them to install; nothing else can. Each holds the APKs, meta.json and icon.png.
 */
class AppBackups(context: Context) {
    data class Backup(val pkg: String, val label: String, val uid: Int, val t: Long, val bytes: Long, val dir: File)

    private val root: File? = context.getExternalFilesDir(null)?.let { File(it, "backups") }

    @Synchronized
    fun list(): List<Backup> {
        val base = root ?: return emptyList()
        val dirs = base.listFiles { f -> f.isDirectory }.orEmpty()
        dirs.map { it.name }.filter { it.startsWith(PREVIOUS) }
            .map { it.removePrefix(PREVIOUS) }.filter(Management::isPackageName).forEach { restorePrevious(base, it) }
        return base.listFiles { f -> f.isDirectory && Management.isPackageName(f.name) }.orEmpty()
            .mapNotNull(::read).sortedByDescending { it.t }
    }

    @Synchronized
    fun get(pkg: String): Backup? = if (Management.isPackageName(pkg)) root?.let { base ->
        restorePrevious(base, pkg)
        read(File(base, pkg))
    } else null

    fun icon(pkg: String): Bitmap? = get(pkg)?.let { b -> File(b.dir, ICON).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) } }

    /** Copies [app]'s APKs before replacing any existing recovery copy. */
    @Synchronized
    fun create(app: ApplicationInfo, label: String, icon: Bitmap?): Backup? {
        val base = root ?: return null
        val pkg = app.packageName
        if (!Management.isPackageName(pkg)) return null
        val source = app.sourceDir ?: return null
        val sources = listOf(source) + app.splitSourceDirs.orEmpty()
        val dir = File(base, pkg)
        val staged = File(base, ".new-$pkg")
        val previous = File(base, "$PREVIOUS$pkg")
        restorePrevious(base, pkg)
        if (previous.exists()) return null
        staged.deleteRecursively()
        if (!staged.mkdirs()) return null
        return try {
            sources.forEachIndexed { index, path ->
                val name = if (index == 0) "base.apk" else File(path).name
                val apk = File(path)
                if (apk.length() <= 0L) return null
                apk.copyTo(File(staged, name))
            }
            icon?.let { b -> runCatching { File(staged, ICON).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
            File(staged, META).writeText(JSONObject().put("label", label).put("uid", app.uid).put("t", System.currentTimeMillis()).toString())
            if (dir.exists() && !move(dir, previous)) return null
            if (!move(staged, dir)) return null
            read(dir)
        } catch (_: java.io.IOException) {
            null
        } finally {
            staged.deleteRecursively()
            restorePrevious(base, pkg)
        }
    }

    @Synchronized
    fun delete(pkg: String) {
        get(pkg)?.dir?.deleteRecursively()
    }

    /** Restores the previous complete backup if the process died between the two renames. */
    private fun restorePrevious(base: File, pkg: String) {
        val dir = File(base, pkg)
        val previous = File(base, "$PREVIOUS$pkg")
        if (!previous.exists()) return
        if (read(dir) != null) {
            previous.deleteRecursively()
        } else if (read(previous) != null && (!dir.exists() || dir.deleteRecursively())) {
            move(previous, dir)
        }
    }

    private fun move(from: File, to: File): Boolean = runCatching {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }.isSuccess

    private fun read(dir: File): Backup? {
        val meta = runCatching { JSONObject(File(dir, META).readText()) }.getOrNull() ?: return null
        val apks = dir.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
        if (apks.none { it.name == "base.apk" } || apks.any { !it.isFile || it.length() == 0L }) return null
        return Backup(dir.name, meta.optString("label", dir.name), meta.optInt("uid", -1), meta.optLong("t"), apks.sumOf { it.length() }, dir)
    }

    private companion object {
        const val PREVIOUS = ".previous-"
        const val META = "meta.json"
        const val ICON = "icon.png"
    }
}
