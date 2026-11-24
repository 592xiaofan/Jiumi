// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import java.io.IOException
import javax.net.ssl.SSLException

class AirAdbAuthorizationException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

object AirAdbConnectionFailurePolicy {
    // Android's Conscrypt can expose BoringSSL's reason token instead of JSSE's prose.
    private val tlsAuthorizationAlert = Regex(
        """\b(?:fatal alert:\s*|(?:sslv3|tlsv1|tlsv13)_alert_)""" +
            """(?:bad_certificate|certificate_(?:unknown|required|expired|revoked)|unknown_ca|access_denied)\b""" +
            """|\btlsv1_certificate_required\b""",
        RegexOption.IGNORE_CASE
    )

    fun requiresPairing(error: Throwable): Boolean =
        generateSequence(error as Throwable?) { it.cause }
            .any { it is AirAdbAuthorizationException }

    fun isExplicitTlsAuthorizationRejection(error: SSLException): Boolean =
        generateSequence(error as Throwable?) { it.cause }
            .any { tlsAuthorizationAlert.containsMatchIn(it.message.orEmpty()) }
}
