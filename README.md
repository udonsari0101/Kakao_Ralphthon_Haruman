# 하루만 · Kakao Ralphthon

사고 싶은 마음을 잠시 보관하고, 정한 시간이 지나면 모아·토리와 다시 살펴보는 Android 앱이에요.

## 현재 상태

**개발 진행 중: 2026-09-29 12:04:47–15:04:47 KST.**

- 쿠팡 링크 입력/공유 수신, 직접 텍스트 입력, 보관·정보 수정·구매 결정/포기·소비 기록
- 모아·토리 캐릭터, 도토리 대기 표시, 스와이프 정리 시간, 되돌리기, 절약 숲
- 빨강 테마, 라이트·다크·시스템 모드, 말풍선·모션 감소 설정
- API 응답 검증/자동 추가/부분 보완 구조, 취소·늦은 응답·중복 저장 방어

**실제 상품 API 주소와 인증 정보가 아직 없어 production 서비스는 미연결입니다.** 미연결 상태에서는 직접 입력해 저장할 수 있어요. 실제 상품 조회 성공으로 표시하거나 임의 상품 정보를 만들지 않습니다. API 계약에 없는 상품 설명은 수동으로 입력합니다. 인스타그램 신규 추가·유튜브·스크린샷 업로드·앱 내부 페이지 파싱은 제외했어요.

구매 결정은 소비 기록이며 실제 주문을 실행하지 않습니다. 예시 상품은 절약 실적에서 제외합니다.

### 검증 및 배포

첫 통합 체크포인트는 `dde656c`이고 main 반영을 확인했습니다. JVM38개와 연결 Android13 실기기 테스트28개가 통과했어요. 이후 발견한 초안 보존·화면 상태 크기·수신 캐릭터 위치 문제를 수정하고 있습니다. 최신 결과는 [todo](todo.md)에 기록합니다.

서명된 `1.1.0-rc.1` 후보를 제작해 깨끗한 API36 emulator에 설치했고, 서명·패키지·버전·debuggable=false를 확인했습니다. **아직 GitHub Release로 발행하지 않았습니다.** 최종 APK 검증·업로드 후 버전별 로컬 폴더와 Release 링크를 추가합니다.

검수 도구가 기기 앱을 제거하는 부작용이 한 번 발생했습니다. 이전 기기 데이터 백업이 없어 복구를 보장하지 못하며, 이후 테스트는 앱 제거 없이 사전 백업과 `install -r`/수동 instrumentation만 사용합니다. 사건과 후속 조치는 todo에 기록했습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [GOAL_SPEC](GOAL_SPEC.md) | 현재 v3.2 목표와 완료 기준 |
| [todo](todo.md) | 실시간 실행·결함·검증·배포 현황 |
| [LINK_API_DESIGN](LINK_API_DESIGN.md) | 입력/공유·API 계약·응답 검증·저장 |
| [UI_CHARACTER_DESIGN](UI_CHARACTER_DESIGN.md) | 모아·토리·화면·반응형·모션·문구 |
| [ASSET_PLAN](ASSET_PLAN.md) | img 원본·제공 캐릭터 매핑과 적용 위치 |
| [DEVELOPMENT_RUNBOOK](DEVELOPMENT_RUNBOOK.md) | 자율 작업·폰 검수·체크포인트 운영 |
| [RELEASE_WORKFLOW](RELEASE_WORKFLOW.md) | 서명·정확한 APK 검증·버전별 배포 |
| [CHECKPOINT](CHECKPOINT.md) | 과거 중지 시점 소스·산출물 보존 기록 |
| [md_plus/APPLIED](md_plus/APPLIED.md) | 추가 문서 검토·해시·적용 이력 |

초기 [개발 가이드](DEVELOPMENT_GUIDE.md), [API 조사](API_PREREQUISITES.md), [쿠팡 파싱 조사](COUPANG_PARSING_TECHNICAL.md)는 이력을 위해 보존하며 현재 목표와 충돌하면 최신 명세를 따릅니다.

## 프로젝트 구조

- `apk-project/`: 실제 Kotlin/Compose 앱. Android Studio에서 이 폴더를 열어요.
- `img/`: 사용자 제공 로고·아이콘45개 원본과 metadata
- `md_plus/mascot-duo/`: 캐릭터 가이드·벡터·PNG·미리보기 원본
- `md_plus/`: 작업 중 추가 문서를 읽고 계획을 갱신하는 입력 폴더
- `Personal Consumption Decision App/`: 초기 Figma 기반 React/Vite 디자인
- `Haruman_Ralphthon/`: 초기 Android scaffold
- `releases/`: 버전별 signed APK 로컬 보존 폴더. APK는 GitHub Releases asset으로 관리
- `checkpoints/`: 과거 중지 소스/산출물 보존
- `apk-project/verification/`: 비공개 로컬 QA 증거·기기 백업. Git 제외

제공 React 디자인의 기존 Git LFS 설정을 유지합니다. 필요한 경우 `git lfs pull`로 이미지까지 받아 주세요.

## 빌드

JBR21, Gradle8.13, AGP8.13.2, Kotlin2.0.21, Compose, compile/target SDK36, minSdk24를 사용합니다. SDK 경로·비밀 설정·서명 키는 Git에 넣지 않아요.

```powershell
Set-Location apk-project
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:DEBUG = ''
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain
```

Debug APK는 `apk-project/app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 데이터가 있는 실제 폰에서는 `connectedDebugAndroidTest`를 사용하지 마세요. [데이터 보존 테스트 runner](scripts/run_device_tests.py)는 백업 후 update 설치하고, 테스트 종료 뒤 앱·기존 prefs 보존을 확인합니다.

```powershell
python scripts/run_device_tests.py --serial <adb-serial> --adb <adb.exe> --apk apk-project/app/build/outputs/apk/debug/app-debug.apk --test-apk apk-project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Release는 외부 환경변수로 안정적인 전용 키를 주입해 빌드하고 [stage_release.ps1](scripts/stage_release.ps1)로 최종 APK/서명/소스/QA 해시를 확인한 뒤 버전별 폴더에 보존합니다. 실제 서버가 연결되기 전에는 API 연동 완료로 배포 설명하지 않습니다.
