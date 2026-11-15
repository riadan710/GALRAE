<div align="center">

# 갈래

### 따로 달려도, 우리의 이동은 하나로.

각자의 차로 이동하는 일행이 서로의 이동 상황에 맞춰<br>
주행 중에도 계획부터 주차까지 조율하도록 돕는 차량용 앱.

**Pleos Connect · Kotlin · Jetpack Compose**

[주요 기능](#주요-기능) · [기술 활용 계획](#주요-기술-및-sdkapi-활용-계획) · [시작하기](#시작하기) · [개발 환경](#개발-환경) · [APK](#apk)

</div>

---

## 주요 기능

### 01 · 함께지도

> “우리 여기 들렀다 갈래?”

일행의 차량 상태와 목적지·경유지를 함께 확인하고, 음성 메시지로 이동 계획을 나눕니다. 목적지 변경을 그룹에 제안하거나 다른 차량의 목적지·경유지에 함께 들르고, 휴게소·음식점·카페를 중간 합류 장소로 정할 수 있습니다.

### 02 · 차량 데이터 기반 제안

> “일행 한 대가 늦어지고 있어요. 운전도 오래했는데 잠시 휴게소에서 쉬어가는거 어때요?”

배터리·연료 잔량, 운전 시간과 차량 간 도착 시간 차이를 바탕으로 충전·주유·휴식이 필요한 상황을 살핍니다. 일행이 비슷한 시간에 모일 수 있는 휴게소나 충전소를 제안하고, 따로 이동한 뒤 다시 합류할 장소도 조율합니다.

### 03 · 주차 조율

> “먼저 도착한 차가 만차를 알려왔어요. 가까운 다른 주차장으로 갈까요?”

공공 주차정보와 먼저 도착한 일행의 현장 제보를 바탕으로 주차 상황을 공유합니다. 목적지 주차장이 부족하면 가까운 대체 주차장으로 차량을 나누어 안내하고, 각 차량이 수락한 주차장으로 경로를 변경합니다.

## 주요 기술 및 SDK/API 활용 계획

| 기술 요소 | 활용 계획 |
|---|---|
| **NaviHelper SDK** | 목적지·경유지 조회, 예상 이동 시간 계산, 사용자 수락 후 내비게이션 경로 반영 |
| **Vehicle SDK** | 배터리·연료·주행 상태 수집 및 이동 조율 필요 상황 감지 |
| **Fused Location SDK** | 차량별 현재 위치 수집 및 이동 상황 파악 |
| **STT/TTS SDK** | 음성 입력의 텍스트 변환 및 메시지·제안 음성 안내 |
| **Firebase Realtime Database** | 그룹별 차량 상태, 경로 정보, 제안·응답 실시간 동기화 |
| **카카오 로컬 API·공공데이터** | 합류 장소, 휴게소·충전소 및 주차장 후보 탐색 |

## 시작하기

### 1. 환경 설정

Android Studio에서 프로젝트를 열고 **Android SDK Platform 37**을 설치합니다. Gradle 실행에는 Android Gradle Plugin과 호환되는 Android Studio 내장 JDK를 사용합니다.

`local.properties.example`을 참고해 `local.properties`에 SDK 경로와 카카오 로컬 REST API 키를 설정합니다.

```properties
sdk.dir=/absolute/path/to/Android/sdk
kakao.restApiKey=YOUR_KAKAO_REST_API_KEY
```

Pleos SDK 다운로드를 위한 Maven 저장소는 `settings.gradle.kts`에 설정되어 있습니다.

### 2. 빌드 및 실행

**Pleos Connect Emulator v2.1.0**을 실행한 뒤, 프로젝트 루트에서 다음 명령을 실행합니다.

```bash
adb devices
./gradlew installDebug
adb shell am start --user 10 -n com.galrae.app/.MainActivity
```

`adb`가 PATH에 없다면 Android SDK의 `platform-tools`를 추가합니다. macOS 기본 경로:

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

앱에서 요청하는 위치·알림 권한을 허용하고, 음성 입력 시 마이크 권한을 허용합니다. 추가 권한을 `adb`로 부여할 때도 **`--user 10`**을 지정합니다.

### 3. 시연 준비

가상 차량의 목적지·경유지·에너지 상태와 제안 데이터를 바꾸는 adb 명령은 **[시나리오별 테스트 데이터 변경 가이드](docs/시나리오별_테스트데이터_변경_가이드.md)**를 참고하세요.

- **경로 정보:** Pleos 내비게이션에서 목적지와 경유지를 설정합니다.
- **음성 입력:** 호스트 OS의 마이크 접근을 허용합니다. 에뮬레이터의 호스트 마이크가 꺼져 있다면 아래 명령으로 활성화합니다.

```bash
adb emu avd hostmicon
```

## 개발 환경

| 항목 | 기준 |
|---|---|
| 플랫폼 | Pleos Connect · Automotive 전용 |
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** |
| 화면 | **2560 × 1440** · 앱은 분할 창의 세로 영역(portrait)에서 실행 |
| 차량 설정 | EV |
| 실행 사용자 | Android **user 10** |
| Android SDK | compileSdk 37 · minSdk 28 · targetSdk 34 |
| 언어·UI | Kotlin · Jetpack Compose |
| Pleos SDK | NaviHelper 3.0.5 · Vehicle 2.3.2 · STT/TTS 3.3.1 |

### 테스트

```bash
# 로컬 단위 테스트
./gradlew testDebugUnitTest

# 에뮬레이터 연결 후 UI·통합 테스트
./gradlew connectedDebugAndroidTest
```

## APK

제출용 APK 및 GitHub Release 링크는 **준비 중**입니다.

직접 Debug APK를 빌드하려면:

```bash
./gradlew assembleDebug
```

빌드 결과: `app/build/outputs/apk/debug/app-debug.apk`
