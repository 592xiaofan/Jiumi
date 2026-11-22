// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.os.IBinder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

internal enum class PackageCommandOutcome { FINISHED, UNAVAILABLE, DENIED, UNKNOWN }

internal data class PackageCommand(val outcome: PackageCommandOutcome, val output: String)

internal data class EnabledChange(val failure: PackageChangeFailure?, val path: String, val output: String, val attempts: String)

/** The pre-change state and the readback are for the same installed package and Android user. */
internal fun changeEnabledSetting(
    target: Int,
    read: () -> Int?,
    command: () -> PackageCommand,
    binder: () -> PackageCommand
): EnabledChange {
    val before = read()
    val first = command()
    val after = read()
    val failure = enabledFailure(first, after, target)
    val primary = "pm=${first.outcome}/${failure?.name ?: "VERIFIED"}"
    val canRetry = (first.outcome == PackageCommandOutcome.UNAVAILABLE && (before == null || after == before)) ||
        (first.outcome == PackageCommandOutcome.FINISHED && before != null && after == before)
    if (failure == null || !canRetry || after == null) return EnabledChange(failure, "pm", first.output, primary)

    val second = binder()
    val verified = if (second.outcome == PackageCommandOutcome.UNAVAILABLE) after else read()
    val finalFailure = enabledFailure(second, verified, target)
    return EnabledChange(finalFailure, "pm,binder", "pm: ${first.output}\nbinder: ${second.output}",
        "$primary binder=${second.outcome}/${finalFailure?.name ?: "VERIFIED"}")
}

private fun enabledFailure(command: PackageCommand, state: Int?, target: Int): PackageChangeFailure? = when {
    command.outcome == PackageCommandOutcome.DENIED -> PackageChangeFailure.PERMISSION_DENIED
    state == target -> null
    command.outcome == PackageCommandOutcome.UNKNOWN || state == null -> PackageChangeFailure.UNVERIFIED
    else -> PackageChangeFailure.NOT_APPLIED
}

/** Uses the framework proxy for this OS version. The caller already runs as Android shell. */
internal object PackageEnabledBinder {
    private class Service(val remote: Any, val type: Class<*>) {
        fun method(name: String, vararg parameters: Class<*>): Method = type.getMethod(name, *parameters)
    }

    private fun connect(): Service {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java).invoke(null, "package") as IBinder
        check(binder.isBinderAlive) { "Package service is unavailable" }
        val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
        val remote = checkNotNull(stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder))
        return Service(remote, Class.forName("android.content.pm.IPackageManager"))
    }

    fun read(pkg: String, user: Int): Int? = runCatching {
        val service = connect()
        val name = String::class.java
        val number = Int::class.javaPrimitiveType!!
        val installed = try {
            service.method("getApplicationInfo", name, Long::class.javaPrimitiveType!!, number)
                .invoke(service.remote, pkg, 0L, user)
        } catch (_: NoSuchMethodException) {
            service.method("getApplicationInfo", name, number, number).invoke(service.remote, pkg, 0, user)
        }
        if (installed != null) service.method("getApplicationEnabledSetting", name, number)
            .invoke(service.remote, pkg, user) as Int else null
    }.getOrNull()

    fun write(pkg: String, user: Int, target: Int): PackageCommand {
        val number = Int::class.javaPrimitiveType!!
        val name = String::class.java
        val (service, method) = try {
            val service = connect()
            service to service.method("setApplicationEnabledSetting", name, number, number, number, name)
        } catch (error: Exception) {
            return commandFailure(error, PackageCommandOutcome.UNAVAILABLE)
        }
        return try {
            method.invoke(service.remote, pkg, target, 0, user, "com.android.shell")
            PackageCommand(PackageCommandOutcome.FINISHED, "Package manager returned")
        } catch (error: InvocationTargetException) {
            commandFailure(error.targetException, PackageCommandOutcome.UNKNOWN)
        } catch (error: Exception) {
            commandFailure(error, PackageCommandOutcome.UNAVAILABLE)
        }
    }

    private fun commandFailure(error: Throwable, outcome: PackageCommandOutcome) = PackageCommand(
        if (error is SecurityException) PackageCommandOutcome.DENIED else outcome,
        error.toString()
    )
}
