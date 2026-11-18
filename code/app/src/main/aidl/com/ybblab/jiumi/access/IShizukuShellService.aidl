// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0
package com.ybblab.jiumi.access;

/** Runs in a short-lived process Shizuku starts as the shell or root; starts the monitor and changes packages. */
interface IShizukuShellService {
    // Shizuku's reserved transaction for ending a user service.
    void destroy() = 16777114;
    int getUid() = 1;
    /** Output of `sh -c command`, or a line starting "[SHIZUKU_" when it could not run to the end. */
    String execute(String command, long timeoutMs) = 2;
}
