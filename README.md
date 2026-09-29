# 하루만 · Kakao Ralphthon

충동적으로 사고 싶은 상품을 보관하고, 정한 시간 뒤 다시 판단하는 Android 앱이다.

## 현재 상태

**앱 구현 진행 중이다(2026-09-29 12:04:47–15:04:47 KST).** 이 저장소는 기본 Kotlin/Compose 앱과 이후 약 3시간의 개발을 위한 v3.1 명세·설계·체크리스트를 담는다. 문서 정리 후 사용자가 구현 재개와 md_plus 주기 감시를 지시했다. 작업 중 추가된 API 계약 초안 두 개를 설계에 반영하며, 실제 base URL·인증과 캐릭터 디자인 문서는 대기 중이다. 최신 요구사항이 현재 APK에 모두 구현되어 있다는 의미는 아니다.

| 현재 구현 | 다음 구현 목표 |
| --- | --- |
| 수동 상품 추가, 보관함, 대기 시간, 구매 결정/포기, 기록, MY | 링크 입력/직접 공유 → 지정 API → 정상 응답 자동 추가, 보완·저장 후 수정 |
| SharedPreferences JSON, 쿠팡/인스타그램 텍스트 공유 분류 | 기존 기록을 보존하고 인스타그램 신규 서비스 보류 |
| 파랑 기반 밝은 화면 | 빨강 기반 라이트·다크·시스템 모드 설정 |
| 기본 Compose 화면/일부 테스트 | 반응형, 클릭 반응, 축하 애니메이션, 실제 입력 흐름 검증 |
| 외부 API/자동 추출 미적용 | 링크 기반 상품 설명 API 경계와 독립적인 수동 입력 |
| 최종 캐릭터 미정 | 디자인 문서를 받을 캐릭터 상태 슬롯·자산 계약 |
| img 심벌·로고·마스코트 확보/점검 | 런처·시작 화면·로고 적용, 필요한 보조 이미지 생성·검수 |
| 기존 debug APK만 보존 | 실기기 반복 QA, 서명 release APK의 GitHub/로컬 버전 보관 |

**스크린샷 선택·이미지 공유·이미지 업로드는 범위에서 제외했다.** 상품 정보는 수령한 API 초안에 따라 링크를 전송해 가져올 예정이다. 앱 내부 상품 페이지 파싱은 보류한다. API 응답의 상품 썸네일 표시는 유지하며, 현재 API가 연결된 상태는 아니다. 수령 계약에 상품 설명 필드는 없어 설명 자동 수신은 후속 확인이 필요하다. 구매 결정 기록은 실제 주문이 아니다.

## 먼저 읽을 문서

1. [GOAL_SPEC.md](./GOAL_SPEC.md) — 현재 목표, 입력 흐름, 테마/모션, 180분 계획, 완료 조건
2. [todo.md](./todo.md) — 설계 완료/문서 대기/구현 대기, 단계별 실행·검증 체크리스트
3. [LINK_API_DESIGN.md](./LINK_API_DESIGN.md) — 공유·API·응답 판정·자동 추가·수동 수정·오류/중복 처리
4. [UI_CHARACTER_DESIGN.md](./UI_CHARACTER_DESIGN.md) — 화면·문구·캐릭터 슬롯·테마·반응형·모션
5. [ASSET_PLAN.md](./ASSET_PLAN.md) — 제공 이미지 실물 점검·화면별 배치·필요 이미지 생성 범위
6. [DEVELOPMENT_RUNBOOK.md](./DEVELOPMENT_RUNBOOK.md) — 3시간 자율 개발, todo 즉시 갱신, 폰 UI/모션/반응형 검수
7. [RELEASE_WORKFLOW.md](./RELEASE_WORKFLOW.md) — main 체크포인트, 서명·버전·최종 APK 검증, GitHub Releases
8. [CHECKPOINT.md](./CHECKPOINT.md) — 실제 코드/검증 상태, 소스 위치, 빌드 환경, 재개 명령

초기 [개발 가이드](./DEVELOPMENT_GUIDE.md), [API 조사](./API_PREREQUISITES.md), [쿠팡 파싱 조사](./COUPANG_PARSING_TECHNICAL.md)는 변경 이력을 위해 보존한다. 현재 명세와 충돌하면 현재 명세를 따른다.

## 프로젝트 구조

- `apk-project/`: 실제 Android 개발 대상. Android Studio에서 이 폴더를 연다.
- `Personal Consumption Decision App/`: 제공된 Figma 기반 React/Vite 디자인 소스.
- `Haruman_Ralphthon/`: 초기 Android scaffold 원본.
- `research/`: 상품 필드만 기록한 과거 쿠팡 조사 자료.
- `img/`: 사용자 제공 심벌·로고 원본과 파일별 메타데이터. 앱에 아직 적용하지 않았다.
- [`releases/`](./releases/README.md): 향후 서명 release APK의 버전별 로컬 보관 위치. 현재 APK는 없다. 정제 metadata는 Git, APK는 GitHub Release asset으로 관리한다.
- `checkpoints/`: 중지 시점 소스/산출물의 보존 메타데이터. APK, ZIP, 기기 화면과 테스트 원본은 로컬에만 보존하며 Git에 포함하지 않는다.

제공 디자인의 기존 Git LFS 설정을 유지한다. 디자인 이미지까지 내려받을 때는 Git LFS가 설치된 환경에서 clone하거나 `git lfs pull`을 실행한다. Android 소스와 Gradle wrapper는 일반 Git 파일이다.

## Android 빌드

현재 확인한 환경은 JBR 21, Gradle 8.13, AGP 8.13.2, Kotlin 2.0.21, compile/target SDK 36, minSdk 24다. 로컬 SDK 경로는 커밋하지 않는다. Android Studio에서 `apk-project`를 열어 SDK 위치를 설정한다.

```powershell
Set-Location apk-project
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

APK 출력: `apk-project/app/build/outputs/apk/debug/app-debug.apk`.

중지 전 JVM 9개(기능 8 + 예제 1), Android 6개(Compose 5 + 예제 1)가 통과했다. 이는 새 v3.1 기능의 검증 결과가 아니다. 이전 테스트 이후의 새 구현 검증은 todo의 실행 기록에 별도로 남긴다.
