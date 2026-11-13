# AGENTS.md

갈래(GALRAE) 팀 공통 규칙. 사람과 AI 에이전트 모두 이 파일을 따른다.

- 앱: 갈래 — Pleos Connect(차량 인포테인먼트)용 그룹 주행 앱. 팀: 갈래말래
- 기능 스펙·SDK 제약·DB 구조: [`docs/핵심기능_개발정리.md`](docs/핵심기능_개발정리.md)
- UI 문구·화면 흐름은 **Figma가 최신**. 문서와 다르면 Figma를 따른다

## 일반 Android와 다른 Pleos 조건

- 차량 전용 앱: `android.hardware.type.automotive` 필수. SDK는 Pleos Maven(`ai.pleos.playground:*`)에서 받는다
- Google Play 서비스가 없다 → FCM 등 GMS 의존 기능은 동작하지 않는다
- 화면은 분할 창의 한 칸(portrait)으로 뜬다
- 다른 앱이 우리 창을 차지하면 백그라운드로 가서 얼어버린다 (Home 버튼으로는 안 감). 계속 수신하려면 포그라운드 서비스가 필요
- `pleos.car.permission.PRIVATE_*` 권한은 앱마켓 승인이 필요하다
- 에뮬레이터: Pleos Connect Emulator v2.1.0, 2560×1440. 앱은 **user 10**에서 실행된다 (`adb shell pm grant --user 10 …`)
- 에뮬레이터와 실차는 다를 수 있다: `Build.DEVICE`(CONNECT_S 등)는 실차에서만 유효하고, 미디어 코덱 지원도 다르다
- 차량 속도는 GPS로 계산하지 말고 `PERF_VEHICLE_SPEED`를 쓴다 (클러스터 표시와 일치)

자세한 근거와 SDK별 제약은 docs 1장.

## Pleos 개발 가이드 필수 사항

[차량 앱 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro)에서 우리 앱에 해당하는 것만 추림. 어기면 차량 탑재 심사에서 떨어질 수 있다.

- **주행 규제**: Activity에 `distractionOptimized=true`를 선언하고, `CarUxRestrictionsManager`를 구독해 `getActiveRestrictions()`에 맞게 기능을 막는다 (예: `UX_RESTRICTIONS_NO_VIDEO`면 영상 중지). 지역별 제한은 플랫폼이 내려주는 값을 그대로 따른다
- **포그라운드 서비스는 실제로 기능을 제공하는 동안만**: 화면을 점유하지 않고, 재생 중이 아니고, 백그라운드에서 줄 기능이 없으면 `stopForeground` + `stopSelf`로 종료하고 오디오 포커스·WakeLock·Player를 해제한다. 우리 앱은 "여행(방) 진행 중"에만 유지하고 종료·나가기 시 즉시 끈다. 미사용 상태에서 FGS 유지 = 심사 NG
- **오디오 포커스**
  - 재생 전에 `AudioFocusRequest`로 요청하고, 끝나면 반납(abandon)
  - 음악과 같이 들려야 하는 짧은 음성(메시지, 안내)은 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
  - 포커스 변경 이벤트를 반드시 처리: `LOSS`·`LOSS_TRANSIENT` → 즉시 중단, `LOSS_TRANSIENT_CAN_DUCK` → 볼륨 감쇄
  - Suspend 진입 시 재생 중지·포커스 반납·리소스 해제, 새 재생 금지. Resume 때 이전 상태를 자동 복원하지 않는다
- **음성 출력 앞 무음 150~200ms**: TTS·녹음 음성 재생 시 첫 음절이 잘리지 않도록 오디오 데이터 앞에 무음을 붙인다 (재생 지연으로 대신하지 않음)
- **CCP(로터리 컨트롤러)**: 지원하지 않으면 `<application>`에 `com.hmg.rotary.ROTARY_SUPPORTED=false` 메타데이터를 넣고, 모든 필수 조작을 터치만으로 끝낼 수 있게 한다
- **Back 키**: Hide(백그라운드로) 또는 Finish(종료) 중 하나를 정해 일관되게. 한 번의 Back에 둘 다 하지 않는다. Hide라면 프로세스가 죽어도 복원되게 상태를 저장한다
- **키보드(IME)**: 입력창이 있는 화면은 키보드에 밀려 올라가는 게 정상. 입력창이 없는 창(다이얼로그 등)은 키보드 때문에 움직이지 않게 insets를 `statusBars | navigationBars`로 한정한다

## 빌드·실행

`adb`가 PATH에 없으면 `~/Library/Android/sdk/platform-tools`를 PATH에 추가한다.

```bash
./gradlew installDebug                                           # 빌드 + 에뮬레이터 설치
adb shell am start --user 10 -n com.galrae.app/.MainActivity     # 실행 (user 10)
adb logcat --pid=$(adb shell pidof com.galrae.app)                # 우리 앱 로그만
adb shell pm grant --user 10 com.galrae.app <권한>                # 권한 부여 (--user 10 빠지면 적용 안 됨)
adb exec-out screencap -p > screen.png                           # 화면 캡처
```

## Virtual Console (차량 상태 조작)

```bash
adb forward tcp:8088 tcp:8088        # 이후 브라우저에서 http://127.0.0.1:8088
```
- 기어·속도·배터리·경고등을 조작해 주행 중 상태를 재현한다
- 스크립트로도 조작 가능. 브라우저 페이지가 닫혀 있으면 `curl -X POST http://127.0.0.1:8088/browser/session`을 호출한 뒤 **3초 안에** 보내야 한다 (넘기면 503)
  ```bash
  curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Drive"                   # Park|Reverse|Neutral|Drive
  curl -X PUT "http://127.0.0.1:8088/vehicle/controls?speed=60&accelerator=20&brake=0&steering=0"
  curl -X PUT "http://127.0.0.1:8088/vehicle/ev-battery?field=soc&value=15"
  ```
- 비행기 모드는 켜지 말 것: 끄고 나도 에뮬 네트워크가 복구되지 않아 재부팅해야 한다

## 파일 위치

어디에 둘지 헷갈릴 때의 기본 방향. 세부 구조는 개발하면서 정한다.

| 무엇 | 어디 |
|---|---|
| Kotlin 코드 | `app/src/main/java/com/galrae/app/` 아래, 관련 있는 것끼리 하위 패키지로 묶기 |
| 화면 문구 | `app/src/main/res/values/strings.xml` |
| 이미지·아이콘 | `app/src/main/res/drawable/` |
| 앱에 넣는 데이터(JSON 등) | `app/src/main/assets/` |
| 권한·서비스 선언 | `app/src/main/AndroidManifest.xml` |
| 라이브러리 추가 | `gradle/libs.versions.toml`에 버전·좌표를 적고 `app/build.gradle.kts`에서 `libs.*`로 참조 |
| 키·URL 등 설정값 | `local.properties` (이름은 `local.properties.example`에도 추가) → `BuildConfig`로 읽기 |
| 기획·스펙 문서 | `docs/` |
| 가상 차량 봇·시연 스크립트 | `tools/` |
| Firebase 보안 규칙 | `firebase/` |
| 제출용 release APK | `apk/` |

## Git 규칙

### 브랜치
- `main` 하나 + 작업 브랜치. 머지 제한 없음, 셀프 머지 가능
- 브랜치 이름: `타입/요약` — 예: `feat/destination-proposal`, `fix/member-card-eta`

### 커밋 메시지
```
타입: 요약
```
- 타입: `feat` 기능 · `fix` 버그 · `refactor` 구조 변경 · `ui` 화면·스타일 · `docs` 문서 · `chore` 빌드·설정·의존성 · `test` 테스트
- 요약은 한국어 가능, 50자 안쪽, 마침표 없이
- 예: `feat: 목적지 변경 제안 시트 추가` · `docs: DB 구조에 meetups 추가`

### PR
- `main`으로 PR을 열고 [템플릿](.github/pull_request_template.md)을 채운다. 리뷰는 필수 아님
- 머지는 **Squash and merge**. PR 제목을 커밋 메시지 규칙대로 쓴다
- 둘이 같이 쓰는 코드(Manifest, Gradle, 공통 모델 등)를 고쳤으면 상대에게 알린다
- 기능 동작·SDK 제약·DB 구조가 바뀌면 `docs/`도 같이 고친다

## 민감값

- 커밋 금지: `local.properties`, `*.jks`/`*.keystore`, `.env`, `google-services.json`
- 키 이름은 `local.properties.example`에 추가하고, 실제 값은 팀원끼리 직접 공유한다
