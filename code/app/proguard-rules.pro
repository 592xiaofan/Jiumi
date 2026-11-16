# SPDX-FileCopyrightText: 2026 ybblab
# SPDX-License-Identifier: Apache-2.0

# Shizuku starts the shell helper by class name in its own process.
-keep class com.ybblab.jiumi.access.ShizukuShellService { *; }
-keep class com.ybblab.jiumi.access.IShizukuShellService$Stub { *; }
-keep class com.ybblab.jiumi.access.IShizukuShellService$Stub$Proxy { *; }
