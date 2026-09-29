# 하루만 APK 작업 체크포인트

현재 상태: **v3.2 구현·검수 진행 중** · 기준일: **2026-09-29, Asia/Seoul**

사용자의 재개 지시에 따라 **12:04:47–15:04:47 KST, 180분** 개발을 진행하고 있다. 이 문서는 현재 실행 요약과 **그 전에 중지했던 버전 1.0 보존 기록**을 구분한다. 재개 직전 과거 소스 ZIP의 50개 파일 해시 일치를 확인했으며, 그 ZIP을 최신 앱 소스로 표시하지 않는다.

## 현재 v3.2 실행 체크포인트

| 구분 | 확인된 상태 | 아직 완료하지 않은 것 |
| --- | --- | --- |
| 소스 | 첫 통합 `dde656c`를 GitHub `main`에 반영 | 이후 수정과 최종 소스 SHA는 별도 체크포인트 필요 |
| 입력·저장 | 쿠팡 링크 입력/공유 경계, 직접 텍스트 입력, 보관·편집·결정·초안 보존 구조 구현 | 모든 복합 UI 조건을 자동 테스트 통과로 대체하지 않음 |
| API | 계약 초안 2개 수령, adapter·검증·자동 저장/보완·취소·중복 방어 구현 및 Fake 검증 | 실제 endpoint·인증 미제공, production `NotConfigured`, 실제 상품 조회 미검증 |
| 디자인 | `md_plus/mascot-duo` MD 5개·제공 벡터 적용, 모아·토리·도토리·정리 시간·절약 숲·빨강 3모드 | 최종 수정 후 화면·모션·반응형 검수는 todo에 개별 기록 |
| 테스트 | JVM 38개, 격리 저장 공간을 사용하는 Android 13 실기기 36개 통과 | 최종 release APK 자체의 전체 QA와 구분 |
| 데이터 보존 | 최신 실제 폰 테스트 전후 운영 prefs·workspace 해시 동일 | 아래 데이터 손실 사건 이전 데이터 복구 증거는 없음 |
| 내부 APK | 전용 키로 서명한 `1.1.0-rc.1`/code 2, 서명·버전·package·debuggable=false 및 깨끗한 API 36 emulator 설치 확인 | GitHub Release 미발행. 후속 수정 최종 APK의 QA·로컬 불변 보존·공개 배포·다운로드 해시 확인 대기 |

스크린샷/사용자 이미지 입력·인스타그램 신규 서비스·유튜브·앱 내부 페이지 파싱은 제외한다. 기존 인스타그램 데이터 호환은 유지한다. 상품 설명은 API 계약에 없는 필드이므로 직접 입력한다. 자세한 현재 기준은 [GOAL_SPEC.md](./GOAL_SPEC.md), [todo.md](./todo.md), [LINK_API_DESIGN.md](./LINK_API_DESIGN.md), [UI_CHARACTER_DESIGN.md](./UI_CHARACTER_DESIGN.md)를 따른다.

### 기기 데이터 손실 사건과 변경한 검수 절차

재개 후 `connectedDebugAndroidTest` 경로에서 실제 폰 앱이 제거되는 부작용이 발생했다. **사건 이전 기기 내부 데이터 백업은 없었고, 손실된 기존 데이터를 복구했다고 확인할 근거도 없다.** 아래 과거 ZIP은 소스·APK·검증 산출물 보존물이며 휴대전화 데이터 백업이 아니다. 최신 테스트에서 데이터가 유지된 사실이 과거 손실 복구를 뜻하지 않는다.

이후 실제 폰에서는 해당 Gradle 연결 테스트 명령을 금지하고, [run_device_tests.py](./scripts/run_device_tests.py)의 사전 `run-as` 백업 → `install -r` → 수동 instrumentation → 앱 존재·운영 데이터 해시 확인을 사용한다. 백업 대상은 `shared_prefs`와 `files/ui-workspace/<SHA256(UTF-8 haruman_local_v1)>/`이며 테스트 UUID workspace와 `files/` 전체는 제외한다. 실행 명령은 이 문서 마지막 절과 [DEVELOPMENT_RUNBOOK](./DEVELOPMENT_RUNBOOK.md)에 있다. 원시 QA·기기 serial·백업은 Git 제외 경로에만 보존한다.

## 과거 중지 체크포인트 — 버전 1.0, 아래 기록은 현재 구현 상태가 아님

이하 과거 보존 내역에서 “현재”, “미구현”, “중지 이후 실행하지 않았다”는 **재개 전 해당 기록 시점**을 뜻한다. 당시에는 사용자 요청에 따라 앱 수정·빌드·adb를 중지하고 문서·복사·압축·해시 확인과 읽기 전용 쿠팡 조사를 수행했다. 이후 12:04:47 KST부터 다시 개발했으므로 아래 상태를 최신 상태로 해석하지 않는다.

### 과거 v3.1 설계 전용 체크포인트

- 사용자가 이번에는 **spec·todo 반영**으로 범위를 확인했다. 180분 개발 목표/타이머는 미시작이다.
- [ASSET_PLAN.md](./ASSET_PLAN.md): 작업 도중 교체된 최신 img의 내용·알파·크기·해시 재점검, 화면별 배치와 필요한 이미지 생성 계획. Android 리소스 적용·이미지 생성은 하지 않았다.
- HARUMAN_API_RESPONSE_SPEC.md / HARUMAN_PRODUCT_API_PLAN.md가 작업 중 추가되어 문서상 계약을 검토했다. 실제 endpoint·인증·서버·Mac 상태를 확인하거나 서버 작업을 실행한 것은 아니다. API 계약에 description 필드는 없다.
- [DEVELOPMENT_RUNBOOK.md](./DEVELOPMENT_RUNBOOK.md): 재개 후 자율 목표, 연결 폰 UI/모션/반응형 반복 검수, todo 즉시 갱신, 단계별 main 반영.
- [RELEASE_WORKFLOW.md](./RELEASE_WORKFLOW.md): 기존 데이터/서명 충돌 구분, 최종 APK 검증, GitHub Release asset 업로드·다운로드 해시 확인.
- [`releases/`](./releases/README.md)를 마련했다. release APK는 아직 제작·서명·검증·업로드하지 않았다. 기존 debug APK를 release로 이름만 바꾸어 보관하지 않는다.
- 이미지 원본과 명세는 Git에 보존한다. 향후 APK는 GitHub Release asset으로 제공하고 로컬 `releases/<versionName>-<versionCode>/`에 각각 저장한다. 원시 기기 증거·키·빌드 산출물은 소스 commit에서 제외한다.

## 과거 중지 당시 요구와 구현의 차이

- 다음 개발 목표는 쿠팡 중심으로 다시 정리한다. **현재 APK에는 쿠팡과 인스타그램 지원이 모두 남아 있으며, 쿠팡 전용 전환은 아직 구현하지 않았다.**
- 외부 API, 상품 자동 추출, 서버 연결은 구현하지 않았다. Manifest에 인터넷 권한도 없다.
- 앱은 Kotlin/Compose 기반의 오프라인 틀이다. 상품명·가격·링크를 직접 입력하고, 쿨다운 이후 구매 결정 또는 포기를 기록한다.
- 마지막 변경은 결정 화면 내용 열에 `fillMaxWidth()`를 추가한 중앙 정렬 수정이다. 수정 후 APK 빌드와 연결 테스트는 완료했으나 **수정된 화면을 다시 촬영해 시각적으로 확인하지는 않았다.**
- 기존 휴대전화의 앱 데이터는 지우지 않았다. 이번 보존물에는 휴대전화 내부 SharedPreferences 백업이 포함되지 않는다.

후속 작업 범위는 [GOAL_SPEC.md](GOAL_SPEC.md), 실행 순서는 [todo.md](todo.md), 지정 API 기반 상품 추가는 [LINK_API_DESIGN.md](LINK_API_DESIGN.md)를 기준으로 이어간다. [쿠팡 파싱 조사](COUPANG_PARSING_TECHNICAL.md)는 과거 기술 조사이며 실행 계획에서 보류한다.

## 보존 위치와 무결성

보존 폴더: [`checkpoints/2026-09-29-paused/`](checkpoints/2026-09-29-paused/)

| 보존 파일 | 내용 |
| --- | --- |
| `apk-project-source.zip` | 현재 `apk-project/` 소스·리소스·Gradle 설정 50개 파일 |
| `source-manifest.json` | ZIP 내부 파일별 경로, 바이트 수, 원본 수정 시각, SHA256 |
| `apks/app-debug.apk` | 마지막으로 생성된 앱 디버그 APK |
| `apks/app-debug-androidTest.apk` | 기존 연결 테스트 APK |
| `test-results/jvm/*.xml` | JVM 테스트 결과 2개 |
| `test-results/android/TEST-SM-G970N-Android13.xml` | 실제 휴대전화 연결 테스트 결과 |
| `screenshots/*.png` | 중지 전에 이미 촬영한 화면 2개 |
| `screenshots/README.md` | 실제 화면 내용·원래 파일명·검증 한계 |
| `artifact-manifest.json` | 보존 파일 목록과 SHA256, 출처, 테스트 요약 |
| `SHA256SUMS.txt` | 보존 파일 SHA256 확인용 목록 |
| `documentation.zip` | 이 중지 시점의 명세·체크리스트·체크포인트·기술 조사·초기 문서 사본 |
| `documentation-manifest.json` | 문서 ZIP 및 내부 파일별 SHA256. 앱 산출물 manifest와 별도로 관리 |

소스 ZIP은 모든 경로의 `build/`, `.gradle/`, `.idea/`, `.git/`, `.kotlin/`, `local.properties`를 제외했다. `verification/`은 소스에서 제외하고 그 안의 기존 스크린샷을 별도 보존했다. 원본 프로젝트 파일은 변경하지 않았다.

주요 SHA256:

```text
4355d0264fb7aca2d080745b44983125fc1033be36151c1fb016bc85177d9c0d  apk-project-source.zip
1e2ec6bb29e4984bcbf045852458ed1b10e1f131aa9c3efd2a6b0f37ce660661  apks/app-debug.apk
```

`artifact-manifest.json`에는 자신의 해시를 넣지 않는다. `SHA256SUMS.txt`는 해당 manifest까지 포함하고 자기 자신은 제외한다. 소스 ZIP의 내부 내용은 `source-manifest.json`의 해시와 대조했다.

추가한 `documentation.zip`과 `documentation-manifest.json`은 별도 문서 보존물이다. 위의 앱 산출물 `SHA256SUMS.txt` 대상에 포함되지 않으며, 문서 manifest로 확인한다. 후속 작업에서 루트의 문서를 갱신해도 이 ZIP은 중지 시점 기록으로 보존한다.

## 작업 폴더와 초기 문서

| 경로 | 역할 / 현재 상태 |
| --- | --- |
| `apk-project/` | 실제 후속 개발 대상. 원래 비어 있던 프로젝트 위치에 Android scaffold를 옮겨 앱 구현 |
| `Haruman_Ralphthon/` | 기존 Android Studio scaffold 원본. 후속 수정 대상은 `apk-project` |
| `Personal Consumption Decision App/` | 사용자가 제공한 Figma 기반 React/Vite 디자인. 원본 디자인 소스는 보존 |
| `Personal Consumption Decision App.zip` | 기존 디자인 ZIP |
| `DEVELOPMENT_GUIDE.md` | 최초 기술 스택·개발 가이드. 최신 범위 안내를 상단에 추가 |
| `API_PREREQUISITES.md` | 최초 플랫폼 API 조사. 현재 쿠팡 분석 문서 링크를 상단에 추가 |
| `ralphthon-product-brief-b58jYyjbQYHVtRNKNPE6hpmB-20260926-v4 (1).md` | 초기 제품 브리프. 현재 범위와 다른 부분은 후속 명세를 우선 |

루트는 Git 저장소가 아니므로 이번 체크포인트는 커밋 대신 소스 ZIP과 파일별 해시로 보존했다. 루트의 초기 계획에 나온 Room/FastAPI·3개 플랫폼은 현재 구현 상태와 다르다.

위 Git 상태는 최초 코드 중지 시점 기준이다. 이후 사용자가 `udonsari0101/Kakao_Ralphthon`의 main에 현재 작업을 반영하도록 요청했다. 로컬 빌드·APK·스크린샷·압축 보존물은 그대로 유지하고 Git에는 소스·문서·정제된 조사/보존 메타데이터를 기록한다. 큰 바이너리·SDK 경로·기기 증거는 로컬 전용이므로 새 clone에 존재한다고 가정하지 않는다. 과거 `documentation.zip`은 v1 기록으로 유지하며 현재 요구와 구분한다.

## 과거 중지 APK와 테스트 결과

앱 ID는 `org.ralphthon.haruman_ralphthon`, 버전 이름은 `1.0`, 버전 코드는 `1`이다.

| 산출물 | 크기 | 원본 파일 수정 시각 KST |
| --- | ---: | --- |
| `app-debug.apk` | 10,834,519바이트 | 2026-09-29 10:50:09 |
| `app-debug-androidTest.apk` | 1,011,122바이트 | 2026-09-29 10:47:47 |

| 검증 | 결과 | XML 시각과 수행 시간 |
| --- | --- | --- |
| `DecisionRulesTest` | 의미 있는 JVM 테스트 8개 통과 | `2026-09-29T01:47:47.064Z`, 0.045초 |
| `ExampleUnitTest` | 기본 산술 예제 1개 통과 | `2026-09-29T01:47:47.056Z`, 0.004초 |
| 연결 테스트 | SM-G970N / Android 13에서 6개 통과 | `2026-09-29T01:50:32`, XML suite time 9.954초 |

JVM 합계는 **9개: 기능 테스트 8개 + 기본 예제 1개**다. Android 합계는 **6개: Compose 화면 테스트 5개 + 앱 패키지 확인 예제 1개**다. 모든 XML에서 실패·오류·건너뜀은 0이다. 연결 테스트 전체 Gradle 명령은 중지 전 실행 기록상 27초에 완료되었으며, 이 시간은 XML의 suite time과 구분한다. Android XML 시각 자체에는 시간대 접미사가 없다.

JVM 테스트 범위:

- 쿨다운 만료 경계와 진행률, 0길이 쿨다운.
- 만료 전 판단 거부, 기존 결정 유지, 포기 금액 합산.
- 대기 기간 연장과 양수 시간 조건.
- 쿠팡·인스타그램 공유 URL 수용, 유사 도메인·유튜브 등 거부.

Compose 테스트 범위:

- 빈 상품명 저장 금지 및 입력한 인스타그램 URL·가격 저장 콜백.
- 유튜브 URL 저장 금지.
- 가격 미상 `null` 및 쿨다운 없이 저장.
- 구매·포기 버튼의 결정 콜백과 구매 완료 오표시 방지.
- 연장 콜백이 구매·포기 결정을 동시에 내리지 않는지 확인.

화면 테스트는 `createComposeRule()`로 독립 화면을 띄운다. 실제 `MainActivity` 공유 진입부터 SharedPreferences 저장·프로세스 재시작까지 이어지는 전체 흐름의 검증 증거는 아니다.

## 스크린샷의 실제 내용

| 보존 파일 | 원본 | 확인한 내용 |
| --- | --- | --- |
| `screenshots/decision_before_centering.png` | `apk-project/verification/onboarding.png` | 온보딩이 아니라 예시 헤드폰의 결정 화면. 10:48:54 KST 저장. 마지막 중앙 정렬 수정 전 화면 |
| `screenshots/vault_scrolled_before_final_build.png` | `apk-project/verification/vault.png` | 목록이 아래로 스크롤된 보관함. 대기 상품 3개, 월 포기 금액 81,900원 표시. 10:49:54 KST 저장. 마지막 APK 빌드 전 촬영 |

두 파일을 직접 열어 내용을 확인하고 보존본 이름을 실제 화면에 맞췄다. 원본 파일명과 파일 내용은 그대로 유지했다. 이 화면들로 최종 APK 전체 화면의 시각 검수가 완료되었다고 판단하지 않는다.

## 과거 버전 1.0의 로컬 데이터와 동작

저장 구현: [`LocalStore.kt`](apk-project/app/src/main/java/org/ralphthon/haruman_ralphthon/data/LocalStore.kt)

SharedPreferences 이름은 `haruman_local_v1`이며 다음 키를 사용한다.

| 키 | 형식 | 초기값 / 의미 |
| --- | --- | --- |
| `onboarding_done` | Boolean | `false` |
| `default_cooldown_hours` | Int | `24`, 양수만 허용 |
| `items` | JSON 배열 문자열 | 저장한 상품과 결정 이력 |

각 상품의 JSON 필드는 `id`, `name`, `priceWon`, `platform`, `url`, `artwork`, `createdAt`, `cooldownUntil`, `decision`, `decidedAt`이다. `priceWon`과 `decidedAt`는 null일 수 있고 시간값은 epoch milliseconds이다. 현재 플랫폼 값은 `COUPANG/INSTAGRAM`, 결정 값은 `PENDING/BUY/SKIP`다.

- `items` 키가 없을 때만 예시 7개를 생성한다. 보류 4개 중 초기 2개가 판단 가능하고 2개는 대기 중이다. 이력 3개는 포기 2개와 구매 결정 1개다. 예시 포기 금액의 합은 81,900원이다.
- 예시의 ID는 `sample-`로 시작하며 원본 URL은 비어 있다. 저장된 빈 배열은 예시를 다시 생성하지 않는다.
- 만료 시각부터 판단할 수 있다. 이미 결정한 항목의 상태와 결정 시각은 후속 판단 호출로 바뀌지 않는다.
- 기간 연장은 현재 시각 + 지정 시간으로 계산하고 최초 저장 시각은 유지한다.
- 가격 미상은 포기 금액 합계에서 제외한다. 현재 예시 기록도 통계에 포함된다.
- 원문 URL이 같은 보류 항목만 중복 저장을 막는다. 추적 파라미터가 다른 같은 상품을 합치는 로직은 없다.
- 전체 JSON 손상 시 빈 목록을 반환하고, 개별 항목 해석 실패 시 해당 항목을 제외한다. 별도 데이터 마이그레이션·복구 안내는 없다.

## 과거에 남긴 재개 전 확인 항목

1. 쿠팡 전용 범위 전환 시 인스타그램 enum·샘플·필터·문구·테스트와 기존 저장 데이터 처리 기준을 함께 정한다.
2. 마지막 중앙 정렬 수정을 실제 휴대전화에서 다시 확인한다. 키보드가 열린 추가 화면과 작은 화면의 스크롤도 확인한다.
3. 실제 공유 → 저장 → 재실행 → 쿨다운 판단 → 이력 반영을 연결해서 확인한다.
4. 공유로 실행된 `MainActivity`는 `onCreate()`에서 기존 Intent를 다시 읽는다. 소비 후 Intent 자체를 제거하지 않으므로 회전·화면 재생성 시 같은 공유 입력이 다시 열리는지 확인한다. 현재는 코드 검토상 확인 필요 항목이며 재현 완료로 기록하지 않는다.
5. 저장 손상 처리, 샘플 통계 포함 여부, URL 정규화, 밝은 테마 고정 상태를 후속 요구에 맞춰 정리한다.

## 현재 사용하는 개발 환경과 데이터 보존 명령

여기부터는 v3.2 재개 후의 실행 절차다. 과거 보존 과정의 실행 기록과 구분한다. 실제 기기에 대한 설치·검증 전 대상과 서명 호환성을 확인한다.

| 항목 | 확인한 값 |
| --- | --- |
| 프로젝트 | `D:\Develop\ralphthon\apk-project` |
| JDK | Android Studio JBR 21.0.7, `C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | `C:\Users\UdonSari\AppData\Local\Android\Sdk` |
| Gradle / AGP | `8.13` / `8.13.2` |
| Kotlin / Compose BOM | `2.0.21` / `2024.09.00` |
| compileSdk / targetSdk / minSdk | `36` / `36` / `24` |
| Java/Kotlin JVM target | `11` |

기본 PATH의 Oracle JDK 24로는 Gradle 테스트 작업 생성 중 `Type T not present` 오류가 관찰되었다. 빌드할 PowerShell 프로세스에서 JBR 21을 지정한다.

```powershell
Set-Location -LiteralPath 'D:\Develop\ralphthon\apk-project'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:DEBUG = ''
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain
if ($LASTEXITCODE -ne 0) { throw '빌드 또는 JVM 테스트 실패' }
```

실제 폰에서는 `connectedDebugAndroidTest`를 **절대 실행하지 않는다.** 빌드 성공 후 저장소 루트에서 아래 runner를 실행한다. 원시 목록·serial·백업은 공개하지 않는다.

```powershell
Set-Location -LiteralPath 'D:\Develop\ralphthon'
$taskAdb = Read-Host 'adb.exe 전체 경로'
$taskSerial = Read-Host '검증할 기기의 adb serial'
python .\scripts\run_device_tests.py --serial $taskSerial --adb $taskAdb --apk .\apk-project\app\build\outputs\apk\debug\app-debug.apk --test-apk .\apk-project\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw '기기 테스트 또는 데이터 보존 확인 실패' }
```

runner는 사전 백업 실패 시 설치를 중단한다. 성공 후 UI를 이어서 확인할 때는 같은 기기를 지정해 실행한다. 아래 앱 실행 명령은 데이터 초기화를 수행하지 않는다.

```powershell
& $taskAdb -s $taskSerial shell am start -n 'org.ralphthon.haruman_ralphthon/.MainActivity'
```

기존 기기 데이터를 보존하기 위해 `pm clear`, 앱 제거·재설치, 앱 내 전체 기록 삭제는 재개 절차에 포함하지 않는다. ZIP으로 복원할 때는 기존 `apk-project` 위에 바로 덮어쓰지 말고 별도 디렉터리에 압축을 풀어 차이를 확인한다. 제외한 `local.properties`의 `sdk.dir`은 복원 환경의 Android SDK 경로로 다시 설정해야 한다.
