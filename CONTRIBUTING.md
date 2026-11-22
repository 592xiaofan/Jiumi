# 参与贡献 · Contributing

简体中文 | [English](#english)

感谢你愿意改进揪咪。提交 Issue 或 Pull Request 前，请先读完下面几条。

## 揪咪的原则

- **只观察，不干预。** 监测服务以 shell 身份运行，只读取系统已经产生的信号：不注入应用、不拦截或修改调用、不代理网络流量。会改变这一点的改动不会被接受。
- **宁可漏报，不误报。** 只写测到的事实，不推测动机。没把握判定的记录宁可不提醒。
- **规则写在配置里。** 类别、阈值、提醒和受保护应用都在 `code/app/src/main/assets/rules/*.json`，Kotlin 代码不按具体应用、厂商或包名硬编码判断。
- **数据只在本机。** 不新增任何上报、统计或第三方网络请求。

## 报告问题

- 安全漏洞请**不要**公开提 Issue，按 [SECURITY.md](SECURITY.md) 私下报告。
- 其他问题请写明：手机品牌和型号、系统版本（如 HyperOS 3 / Android 15）、揪咪版本、连接方式（无线调试、网络调试、Shizuku 或 Root），以及复现步骤。
- 误报或漏报请说明涉及的应用包名、类别和你期望的结果。附日志或导出文件前，先删掉你不想公开的内容（其他应用、地址、账号等）。

## 提交代码

### 构建与测试

需要 JDK 17、Android SDK、NDK 28.2.13676358 和 CMake 3.22.1。接受 SDK 许可证后，Gradle 可以自动下载构建工具。

```sh
cd code
./gradlew :app:assembleDebug
```

提交 PR 前运行与 CI 相同的检查：

```sh
cd code
./gradlew :app:lintDebug :app:assembleDebug
```

涉及监测行为的改动请在真机上验证，并在 PR 里写明机型、系统版本和连接方式；构建通过不等于功能可用。

### 代码结构

| 目录 | 内容 |
|---|---|
| `code/app` | 应用：界面、数据库、判定、导出、更新 |
| `code/app/src/main/assets/rules` | 判定规则：类别（`access_categories.json`）、提醒（`attention.json`）、应用管理的保护名单（`manage.json`） |
| `code/core/probe` | 以 shell 身份运行的监测服务及其 AIDL 接口 |
| `code/core/adb` | 无线调试配对与连接 |
| `code/third_party/libadb` | 配对所用原生库的来源与构建记录 |

### 规则与文案

- 新增类别、操作或被跟踪的方法时，同时在 `values/strings.xml`（英文）和 `values-zh/strings.xml`（中文）补齐名称与说明。
- 面向用户的文字一律放进字符串资源，中英文两份都要完整。

### 协议与数据库

- 修改 AIDL 或探针启动参数时，把 `ProbeProtocol.VERSION` 加 1。
- 修改表结构时，把数据库版本加 1，并在 `onUpgrade` 写迁移，保留已有记录。
- 普通改动不修改 `versionName` / `versionCode`，发布时由维护者统一调整。

### 提交与 PR

- 一个 PR 只做一件事；无关改动分开提交。
- 提交标题用简短英文：`type(scope): summary`，如 `fix(probe): read maker operations in AppOps dumps`。常用 type：`feat`、`fix`、`perf`、`refactor`、`build`、`ci`、`docs`、`test`、`chore`。
- 用户能感知的变化，请在 `CHANGELOG.md` 的待发布条目里补一行。
- 新增依赖要说明用途，并在 [NOTICE](NOTICE) 补充许可信息。
- 不要提交截图、录屏、导出的 ZIP、探针日志、数据库副本或任何来自真实设备的个人数据。

## 许可

你提交的贡献按 [Apache License 2.0](LICENSE) 授权，与本项目一致。

---

<a id="english"></a>

## English

Thanks for helping improve Jiumi. Please read the following before opening an issue or pull request.

### Principles

- **Observe, never interfere.** The monitor runs as the shell user and only reads signals the system already produces: no injection into apps, no intercepting or changing calls, no proxying of traffic. Changes that break this will not be accepted.
- **Rather miss than misreport.** Record only what was measured, never guess at motives. When a judgement is uncertain, leave it unraised.
- **Rules live in configuration.** Categories, thresholds, attention items and protected apps are in `code/app/src/main/assets/rules/*.json`; Kotlin code does not hard-code judgements for particular apps, makers or packages.
- **Data stays on the phone.** No reporting, analytics or third-party network requests.

### Reporting problems

- Do **not** file security vulnerabilities as public issues; report them privately as described in [SECURITY.md](SECURITY.md).
- For other issues, include the phone model, system version (such as HyperOS 3 / Android 15), Jiumi version, how the monitor was started (Wireless debugging, network debugging, Shizuku or root) and the steps to reproduce.
- For a false or missed report, give the package name, the category and what you expected. Before attaching logs or exports, remove anything you do not want public (other apps, addresses, accounts).

### Building and testing

Requires JDK 17, the Android SDK, NDK 28.2.13676358, and CMake 3.22.1. Gradle can download the build tools after you accept the SDK licenses.

```sh
cd code
./gradlew :app:assembleDebug
```

Before opening a pull request, run the same checks as CI:

```sh
cd code
./gradlew :app:lintDebug :app:assembleDebug
```

Changes to monitoring behaviour should be tried on a real phone; say which model, system version and connection method in the pull request. A passing build alone does not show that a feature works.

### Layout

| Path | Contents |
|---|---|
| `code/app` | The app: UI, database, judgements, export, updates |
| `code/app/src/main/assets/rules` | Rules: categories (`access_categories.json`), attention items (`attention.json`), apps protected from management (`manage.json`) |
| `code/core/probe` | The monitor that runs as the shell user, and its AIDL interface |
| `code/core/adb` | Wireless debugging pairing and connection |
| `code/third_party/libadb` | Source and build records for the native pairing library |

### Rules and wording

- When adding a category, operation or traced method, add its names and descriptions to both `values/strings.xml` (English) and `values-zh/strings.xml` (Chinese).
- All user-facing text goes in string resources, complete in both languages.

### Protocol and database

- Changing the AIDL or the monitor's launch arguments: increase `ProbeProtocol.VERSION` by one.
- Changing the schema: increase the database version by one and add a migration in `onUpgrade` that keeps existing records.
- Ordinary changes leave `versionName` / `versionCode` alone; maintainers set them when releasing.

### Commits and pull requests

- One pull request, one change; keep unrelated changes apart.
- Commit titles in short English: `type(scope): summary`, such as `fix(probe): read maker operations in AppOps dumps`. Common types: `feat`, `fix`, `perf`, `refactor`, `build`, `ci`, `docs`, `test`, `chore`.
- For changes users will notice, add a line to the unreleased entry in `CHANGELOG.md`.
- Explain any new dependency and add its license to [NOTICE](NOTICE).
- Never commit screenshots, recordings, exported ZIPs, monitor logs, database copies or other personal data from a real device.

### License

Contributions are licensed under the [Apache License 2.0](LICENSE), the same as the project.
