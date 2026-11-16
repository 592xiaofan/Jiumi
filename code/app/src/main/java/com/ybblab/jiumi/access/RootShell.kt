// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.access

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Root, used only to step down: every command runs as the Android shell (uid 2000, with the shell's
 * groups and no capabilities), so the monitor behaves exactly as when started over Wireless
 * debugging, and root itself is never held. Adapted from Droidance (ybblab, Apache-2.0).
 */
class RootShell(context: Context) {
    private val prefs = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    private val readers = Executors.newCachedThreadPool { Thread(it, "root-shell").apply { isDaemon = true } }
    @Volatile private var identity = SHELL_IDENTITY
    private val _granted = MutableStateFlow(prefs.getBoolean(KEY_GRANTED, false))
    /** Root was granted and stepping down to the shell worked, the last time it was asked. */
    val granted: StateFlow<Boolean> = _granted.asStateFlow()
    @Volatile private var checked = false

    /**
     * Asks the root manager (its own dialog, the first time) and checks that a command can be run as
     * the shell. Called on an explicit action of the user, or to start an open session again.
     */
    @Synchronized fun authorize(): Boolean {
        val help = run("su --help", AUTH_TIMEOUT_MS)
        // Magisk drops capabilities on request; without it, the shell keeps none anyway.
        val chosen = SHELL_IDENTITY + if (help.contains("--drop-cap")) " -d" else ""
        val probe = "id -u; id -g; id -G; awk '/^Cap(Prm|Eff):/ {print \$2}' /proc/\$\$/status"
        val lines = run("id -u; $chosen 2000 -c ${quote(probe)}", AUTH_TIMEOUT_MS)
            .lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val ok = lines.size == 6 && lines[0] == "0" && lines[1] == "2000" && lines[2] == "2000" &&
            lines[3].split(' ').any { it == "3003" } && lines[4].toLongOrNull(16) == 0L && lines[5].toLongOrNull(16) == 0L
        if (ok) identity = chosen
        checked = ok
        prefs.edit().putBoolean(KEY_GRANTED, ok).apply()
        _granted.value = ok
        return ok
    }

    /** Runs [command] as the shell; output, or a line starting "[ROOT_" when it could not. */
    fun shell(command: String, timeoutMs: Long): String {
        if (!checked && !authorize()) return "[ROOT_UNAVAILABLE]"
        val marker = "__JIUMI_ROOT_${System.nanoTime()}__"
        val script = "$command\nprintf '\\n$marker\\n'\nexit 0"
        val output = run("$identity 2000 -c ${quote(script)}", timeoutMs)
        val at = output.lastIndexOf(marker)
        if (output.startsWith("[ROOT_") || at < 0) {
            checked = false
            return if (output.startsWith("[ROOT_")) output else "[ROOT_ERROR] shell did not complete"
        }
        return output.substring(0, at).trimEnd('\n', '\r')
    }

    private fun run(command: String, timeoutMs: Long): String {
        val process = try {
            ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (error: Exception) {
            return "[ROOT_ERROR] ${error.javaClass.simpleName}"
        }
        val output = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText().take(MAX_OUTPUT_CHARS) } }
        return try {
            if (!process.waitFor(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly(); output.cancel(true)
                "[ROOT_TIMEOUT]"
            } else {
                val text = output.get(2, TimeUnit.SECONDS)
                if (process.exitValue() == 0) text else "[ROOT_ERROR] ${text.take(300)}"
            }
        } catch (error: Exception) {
            process.destroyForcibly(); output.cancel(true)
            if (error is InterruptedException) Thread.currentThread().interrupt()
            "[ROOT_ERROR] ${error.javaClass.simpleName}"
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val KEY_GRANTED = "root_granted"
        // The Android shell's supplementary groups, inet among them for the network.
        const val SHELL_IDENTITY = "su -g 2000 -G 1004 -G 1007 -G 1011 -G 1015 -G 1028 " +
            "-G 1078 -G 1079 -G 3001 -G 3002 -G 3003 -G 3006 -G 3009 -G 3011 -G 3012"
        const val AUTH_TIMEOUT_MS = 30_000L
        const val MAX_OUTPUT_CHARS = 128_000
    }
}
