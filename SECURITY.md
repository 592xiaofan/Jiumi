# 安全政策 · Security Policy

简体中文 | [English](#english)

## 报告漏洞

**请不要在公开的 Issue、讨论或 Pull Request 里报告安全漏洞。**

请通过 GitHub 的私密漏洞报告提交：打开本仓库的 [Security → Report a vulnerability](https://github.com/ybblab/Jiumi/security/advisories/new)，只有维护者能看到报告内容。

报告中请尽量写明：

- 受影响的揪咪版本，手机型号和系统版本；
- 漏洞的类型和影响（谁能利用、能拿到什么、能做什么）；
- 复现步骤或概念验证；
- 你认为可行的修复方式（如有）。

我们会尽量在 7 天内确认收到，并在调查期间告知进展。修复发布前请不要公开细节；修复发布后，如你愿意，我们会在发布说明里致谢。

## 支持的版本

只有最新发布的版本会收到安全修复。请先更新到[最新版本](https://github.com/ybblab/Jiumi/releases/latest)再确认问题是否仍然存在。

## 关注范围

揪咪的监测服务以 shell 身份运行，能读取很多敏感信号，所以下面这些问题我们特别重视：

- 揪咪以外的应用能调用监测服务的接口，或读到它记录的数据、事件缓冲、数据库或导出文件；
- 配对密钥或调试连接被其他应用获取或滥用；
- 监测服务或应用管理功能在用户未确认时修改、停用或删除应用；
- 更新检查或安装绕过大小、SHA-256、包名或签名校验；
- 「记录访问的域名」的本地 VPN 泄露、篡改或转发了 DNS 查询以外的流量；
- 任何数据在用户未主动导出的情况下离开手机。

下列情况一般不视为安全漏洞，请按普通 Issue 提交：

- 漏报或误报某个应用的行为（请见 [CONTRIBUTING.md](CONTRIBUTING.md)）；
- 需要攻击者事先已拿到 Root、shell 权限或解锁后的手机实体才能实现的问题；
- 系统或厂商本身的限制。

---

<a id="english"></a>

## English

### Reporting a vulnerability

**Please do not report security vulnerabilities in public issues, discussions or pull requests.**

Report them privately through GitHub: open [Security → Report a vulnerability](https://github.com/ybblab/Jiumi/security/advisories/new) on this repository. Only the maintainers can see the report.

Please include, where you can:

- the affected Jiumi version, phone model and system version;
- the kind of vulnerability and its impact (who can exploit it, what they can read or do);
- steps to reproduce or a proof of concept;
- a suggested fix, if you have one.

We aim to acknowledge reports within 7 days and to keep you informed while we investigate. Please keep the details private until a fix is released; with your permission, we will credit you in the release notes.

### Supported versions

Only the latest release receives security fixes. Please update to the [latest release](https://github.com/ybblab/Jiumi/releases/latest) and check that the problem remains.

### Scope

Jiumi's monitor runs as the shell user and can read many sensitive signals, so we take these especially seriously:

- an app other than Jiumi calling the monitor's interface, or reading its records, event buffer, database or exports;
- the pairing key or debugging connection being obtained or misused by another app;
- the monitor or app management changing, disabling or removing apps without the user's confirmation;
- update checks or installs bypassing the size, SHA-256, package or signature checks;
- the local VPN behind Record domains leaking, altering or forwarding anything other than DNS queries;
- any data leaving the phone without the user exporting it.

These are usually not security vulnerabilities; please file them as ordinary issues:

- missed or false reports of an app's behaviour (see [CONTRIBUTING.md](CONTRIBUTING.md));
- problems that need an attacker to already have root, shell access or the unlocked phone in hand;
- limits of Android or of a phone maker's system.
