<div align="center">

<img src="../images/jiumi-icon.png" alt="Jiumi 아이콘" width="80" />

# Jiumi 揪咪

Android 앱의 활동을 모니터링하고 앱을 관리하는 가벼운 도구입니다.
<br>루트 권한이나 컴퓨터가 필요하지 않습니다. 기록은 휴대전화에 저장됩니다.

**[APK 다운로드](https://github.com/ybblab/Jiumi/releases/latest)** · [사용 가이드](https://jiumi.ybblab.com/en/download/) · [공식 웹사이트](https://jiumi.ybblab.com/en/)

<sub>Android 11 이상 · APK 약 4 MB · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · **한국어** · [Español](README.es.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md)

</div>

## 앱 둘러보기

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="132" alt="모니터링 상태와 주의가 필요한 활동을 보여 주는 개요" /></a>&nbsp;
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="132" alt="앱 활동과 상태의 타임라인" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="132" alt="유형별 활동과 앱 수 및 기록 수" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="132" alt="설치 출처별로 분류된 앱 목록" /></a>
</p>

## 기능

| 기능 | 할 수 있는 일 |
| --- | --- |
| 활동 기록 | 위치, 클립보드, 기기 식별자 등 **12개 그룹의 56가지 접근 유형**을 확인할 수 있습니다. 시간, 앱 또는 유형별로 기록을 보고 백그라운드 활동만 골라 볼 수 있습니다. |
| 주의가 필요한 활동 | 백그라운드 데이터 전송, 백그라운드에서의 민감한 데이터 접근, 잦은 접근, 반복해서 거부된 접근 시도를 확인할 수 있습니다. |
| 네트워크 활동 | 앱별 연결 주소와 확인 가능한 트래픽 측정값을 볼 수 있습니다. 도메인 기록을 켜면 도메인을 연결과 대응시킬 수 있는 경우 해당 도메인이 표시됩니다. |
| 시스템 기록 | 앱의 상세 화면에서 Android가 보존하는 권한 접근 기록과 누적 횟수를 볼 수 있습니다. 모니터링을 시작하기 전의 일부 활동도 포함됩니다. |
| 기록 내보내기 | **Settings › Export logs**에서 선택한 기간의 기록을 CSV 파일이 들어 있는 ZIP 파일로 저장할 수 있습니다. |
| 알림 | 모니터링 중에는 경과 시간과 중지 버튼이 있는 간단한 알림이 표시됩니다. **Settings › Notifications**에서 끌 수 있습니다. 현재 세션의 앱 수와 기록 수도 표시할 수 있습니다. **Attention alerts**를 켜면 새로 주의가 필요한 항목이 발견됐을 때 알림을 받습니다. 한 세션에서 같은 앱의 같은 항목은 한 번만 알립니다. |

## 앱 관리

앱은 설치 출처별로 분류됩니다. 앱의 상세 화면에서 활동을 확인한 뒤 변경할지 결정할 수 있습니다. 앱 관리 작업은 사용자가 시작합니다. 변경 후 Jiumi는 Android에서 상태를 다시 읽어 적용 여부를 확인합니다. Android가 작업을 거부하면 이유를 표시합니다.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="앱 비활성화 확인 화면을 보여 주는 웹사이트 데모" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="앱 제거 확인과 백업 안내를 보여 주는 웹사이트 데모" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="제거한 앱의 복원 및 백업 삭제 옵션을 보여 주는 웹사이트 데모" /></a>
</p>

| 작업 | 동작 |
| --- | --- |
| 권한 | 앱 상세 화면의 **Permissions**에서 권한을 하나씩 켜거나 끌 수 있습니다. **Manageable permissions** 스위치를 끄면 관리할 수 있는 권한을 한꺼번에 끌 수 있습니다. 스위치를 다시 켜면 Jiumi가 껐던 권한만 복원됩니다. 시스템이 잠근 권한은 읽기 전용으로 표시됩니다. |
| 네트워크 접근 | 지원되는 기기에서는 VPN 슬롯을 사용하지 않고 Android 자체 방화벽으로 개별 앱의 네트워크 접근을 차단하거나 복원할 수 있습니다. 앱이 다른 앱과 UID를 공유하거나, 다른 도구가 규칙을 설정했거나, 해당 앱에만 영향이 있는지 Jiumi가 확인할 수 없으면 스위치가 잠기고 이유가 표시됩니다. |
| 앱 비활성화 또는 활성화 | Android가 현재 사용자에 대해 앱을 비활성화합니다. 앱과 데이터는 보존됩니다. **Apps › Disabled**에서 다시 활성화할 수 있습니다. 비활성화된 앱에는 눈송이 배지가 붙습니다. |
| 다시 활성화됨 | Jiumi가 비활성화한 앱이 다시 활성화된 것을 발견하면 목록과 상세 화면에 **Re-enabled**가 표시됩니다. 타임라인에는 발견 시각이 기록됩니다. **Settings › App management › Disable again when re-enabled**를 켜면 Jiumi가 앱을 다시 비활성화합니다. |
| 직접 설치한 앱 제거 | 확인을 위해 전체 패키지 이름을 입력합니다. Jiumi는 기본 APK와 분할 APK가 있는 경우 이를 함께 백업합니다. **Apps › Uninstalled**에서 재설치를 시도할 수 있습니다. **앱을 제거하면 앱 데이터가 삭제됩니다. 백업에는 설치 파일만 포함됩니다.** |
| 사전 설치 앱 제거 | **Settings › App management**에서 **Allow uninstalling preinstalled apps**를 켜면 지원되는 사전 설치 앱을 현재 사용자에 대해 제거할 수 있습니다. 출고 시 설치본과 앱 데이터는 보존됩니다. **Apps › Uninstalled**에서 복원을 시도할 수 있습니다. 제거한 앱에는 휴지통 배지가 붙습니다. |

**Settings › App management**의 **Runs as**에는 작업을 어떤 사용자 권한으로 실행하는지 표시됩니다. 루트 권한으로 연결하거나 루트 권한으로 시작한 Shizuku를 사용하면 앱 비활성화와 활성화, 사전 설치 앱 제거와 복원을 root 사용자로 실행합니다. 따라서 기기가 shell 사용자에게 변경을 허용하지 않는 시스템 앱도 변경할 수 있습니다. 무선 디버깅을 사용하면 이 작업을 shell 사용자로 실행합니다. 기기가 일부 시스템 앱의 변경을 막으면 Jiumi가 이유를 표시합니다. 직접 설치한 앱을 제거하는 작업은 현재 사용자에게만 적용되므로, 업무 프로필이나 세컨드 스페이스에 설치된 동일한 앱은 그대로 유지됩니다.

시스템의 핵심 구성 요소는 기본적으로 보호됩니다. 기기가 의존하는 구성 요소를 변경하면 시스템 기능에 영향을 줄 수 있습니다. Jiumi는 그런 변경을 실행하기 전에 경고를 표시합니다.

## Jiumi는 루트 권한 없이 어떻게 백그라운드 활동을 확인하나요?

앱이 실행되면 Android는 권한 접근 기록, 연결 테이블, 시스템 트레이스, 이벤트 로그를 제공합니다. Jiumi 모니터는 사용 가능한 시스템 신호를 **shell 사용자**로 읽습니다. 앱은 신호를 규칙에 따라 정리하고 휴대전화의 데이터베이스에 저장합니다. 모니터링 과정에서 다른 앱에 코드를 주입하지 않습니다. 앱 비활성화 또는 제거, 권한 해제, 네트워크 접근 차단 등의 작업은 앱 관리에서 사용자가 직접 시작해야 합니다.

<p align="center"><img src="../images/how-it-works.en.png" alt="앱이 Android 시스템 신호를 생성하면 Jiumi 모니터가 shell 사용자로 신호를 읽습니다. 앱은 기록을 정리해 휴대전화에 저장합니다. 선택 기능인 도메인 기록은 로컬 VPN을 통해 DNS 쿼리를 받습니다." width="900" /></p>

| 신호 출처 | Jiumi가 확인할 수 있는 정보 |
| --- | --- |
| 권한 접근 기록 (AppOps) | 어떤 앱이 언제 권한을 사용했는지와 Android가 보존하는 기록입니다. |
| 연결 테이블 | 앱이 어떤 주소에 연결했는지 보여 줍니다. 기본적으로 5초마다 읽습니다. |
| 시스템 트레이스 (Perfetto) | 식별 가능한 시스템 서비스 호출과 시스템이 지원하는 경우의 연결별 트래픽입니다. |
| 이벤트 로그 | 프로세스가 시작된 시각과 Android가 기록한 시작 이유입니다. |

**무선 디버깅, Shizuku, 루트 권한 중에서 선택할 수 있습니다.** 세 방법 모두 모니터는 shell 사용자로 실행됩니다. 무선 디버깅은 휴대전화에서 바로 페어링할 수 있습니다. Shizuku는 ADB 또는 루트 권한으로 시작할 수 있으며 Jiumi에 사용 권한을 부여해야 합니다. 루트 옵션은 모니터를 시작할 때 shell 사용자로 전환합니다. 앱 비활성화와 활성화, 사전 설치 앱 제거와 복원은 root 사용자로 직접 실행하므로 Android가 shell 사용자에게 변경을 허용하지 않는 앱도 변경할 수 있습니다. 루트 권한으로 시작한 Shizuku에서도 마찬가지입니다.

**선택 기능인 도메인 기록**은 로컬 VPN으로 DNS 쿼리를 받아 현재 네트워크의 DNS 서버로 그대로 전달합니다. 다른 트래픽은 목적지로 직접 이동합니다. 도메인 기록은 시스템의 VPN 슬롯을 사용합니다. 앱 자체의 암호화 DNS 또는 호스트 이름으로 지정한 비공개 DNS 제공업체를 쓰는 쿼리는 기록되지 않을 수 있습니다.

## 다운로드 및 모니터링 시작

1. Android 11 이상을 사용하는 휴대전화에서 [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest)의 APK를 다운로드해 설치합니다.
2. Jiumi를 처음 열 때 시작 방법을 선택합니다. 권장 방법인 무선 디버깅을 쓰려면 Wi-Fi에 연결하고 설정 안내에 따라 알림을 허용하고 개발자 옵션을 켭니다. Android가 표시하는 6자리 페어링 코드를 Jiumi 알림에 입력합니다.
3. **Monitor** 화면에서 **Start monitoring**을 누릅니다. 모니터링 중에는 상시 알림이 표시됩니다. 알림이나 앱에서 모니터링을 중지할 수 있습니다.

휴대전화가 재시작되거나 Jiumi가 업데이트되거나 디버깅 서비스가 중단되면, 선택한 연결을 다시 사용할 수 있을 때 Jiumi가 같은 세션을 자동으로 이어갑니다. 카드에 **Monitoring interrupted**가 표시되면 선택한 연결을 복구한 뒤 **Resume**을 누릅니다. 중단된 시간은 모니터링 시간에 포함되지 않습니다.

무선 디버깅과 함께 USB 디버깅도 켜 두면 Wi-Fi 연결이 끊겨 Android가 무선 디버깅을 꺼도 실행 중인 모니터를 유지하는 데 도움이 됩니다. ADB 연결 유지 기능은 기본적으로 꺼져 있습니다. 이 기능을 켜도 기기에 검증된 루프백 리스너가 이미 있을 때만 해당 포트를 재사용합니다. 자세한 내용은 [설정 가이드](https://jiumi.ybblab.com/en/download/)를 참고하세요.

## 개인정보 보호와 한계

- **기록은 휴대전화에 저장됩니다.** Jiumi는 계정을 사용하지 않으며 모니터링 기록을 업로드하지 않습니다. 앱은 GitHub에서 업데이트를 확인할 수 있습니다. 업데이트를 선택하면 APK를 다운로드하고 파일 크기, SHA-256 해시, 서명을 확인합니다. 업데이트 확인 기능은 끌 수 있습니다.
- **기록에는 활동과 연결 정보가 담깁니다.** Jiumi는 권한이 사용되었거나 접근이 거부된 사실을 보여 줄 수 있습니다. 메시지, 비밀번호, 네트워크 전송 내용은 볼 수 없습니다.
- **확인할 수 있는 범위는 시스템에 따라 달라집니다.** 짧은 연결은 샘플링 사이에 끝날 수 있으며, 파일을 직접 읽는 등의 활동은 사용할 수 있는 신호를 남기지 않을 수 있습니다. 연결별 트래픽을 보려면 Android 14 이상과 시스템 지원이 필요합니다. Jiumi는 오탐을 줄이기 위해 확인할 수 없는 일부 활동을 제외합니다.
- **모니터링은 배터리를 더 사용합니다.** 소모량은 앱 활동에 따라 달라집니다. 모니터링이 필요하지 않을 때는 알림에서 중지하세요.

## 소스에서 빌드

JDK 17과 Android SDK가 필요합니다.

```sh
cd code
./gradlew :app:assembleDebug
```

모니터링 분류, 주의 규칙, 보호 대상 앱은 [규칙 디렉터리](../../code/app/src/main/assets/rules/)에서 설정합니다. 코드 구조와 기여 방법은 [CONTRIBUTING.md](../../CONTRIBUTING.md)를 참고하세요.

## 기여 및 라이선스

이슈와 풀 리퀘스트를 환영합니다. 보안 문제는 [SECURITY.md](../../SECURITY.md)의 안내에 따라 비공개로 신고해 주세요.

Jiumi에는 [Apache License 2.0](../../LICENSE)이 적용됩니다. 타사 구성 요소와 라이선스는 [NOTICE](../../NOTICE)에 나와 있습니다. 본인이 소유하거나 사용 권한을 받은 기기에서 Jiumi를 사용하세요.

## 스타 추이

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Jiumi의 GitHub 스타 추이 그래프" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Jiumi가 앱 활동을 파악하는 데 도움이 되었다면 GitHub에서 ⭐를 눌러 주세요.**

</div>
