# 하루만 · Kakao Ralphthon

충동적으로 사고 싶은 상품을 보관하고, 정한 시간 뒤 다시 판단하는 Android 앱이다.

## 현재 상태

**앱 구현은 일시 중지 상태다.** 이 저장소는 기본 Kotlin/Compose 앱과 이후 약 3시간의 개발을 위한 v2 명세·체크리스트를 담는다. 최신 요구사항이 현재 APK에 모두 구현되어 있다는 의미는 아니다.

| 현재 구현 | 다음 구현 목표 |
| --- | --- |
| 수동 상품 추가, 보관함, 대기 시간, 구매 결정/포기, 기록, MY | 스크린샷 추가 + 독립적인 직접 입력 |
| SharedPreferences JSON, 쿠팡/인스타그램 텍스트 공유 분류 | 기존 기록을 보존하고 인스타그램 신규 서비스 보류 |
| 파랑 기반 밝은 화면 | 빨강 기반 라이트·다크·시스템 모드 설정 |
| 기본 Compose 화면/일부 테스트 | 반응형, 클릭 반응, 축하 애니메이션, 실제 입력 흐름 검증 |
| 외부 API/자동 추출 미적용 | 링크 파싱 미지원 틀, 이미지 분석 API 미연결 틀 |

이미지 API는 추후 제공될 문서로 연결한다. 현재 자동 이미지 분석과 쿠팡 링크 파싱을 지원한다고 표시하지 않는다. 구매 결정 기록은 실제 주문이 아니다.

## 먼저 읽을 문서

1. [GOAL_SPEC.md](./GOAL_SPEC.md) — 현재 목표, 입력 흐름, 테마/모션, 180분 계획, 완료 조건
2. [todo.md](./todo.md) — 단계별 실행·검증 체크리스트
3. [CHECKPOINT.md](./CHECKPOINT.md) — 실제 코드/검증 상태, 소스 위치, 빌드 환경, 재개 명령

초기 [개발 가이드](./DEVELOPMENT_GUIDE.md), [API 조사](./API_PREREQUISITES.md), [쿠팡 파싱 조사](./COUPANG_PARSING_TECHNICAL.md)는 변경 이력을 위해 보존한다. 현재 명세와 충돌하면 현재 명세를 따른다.

## 프로젝트 구조

- `apk-project/`: 실제 Android 개발 대상. Android Studio에서 이 폴더를 연다.
- `Personal Consumption Decision App/`: 제공된 Figma 기반 React/Vite 디자인 소스.
- `Haruman_Ralphthon/`: 초기 Android scaffold 원본.
- `research/`: 상품 필드만 기록한 과거 쿠팡 조사 자료.
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

중지 전 JVM 9개(기능 8 + 예제 1), Android 6개(Compose 5 + 예제 1)가 통과했다. 이는 새 v2 기능의 검증 결과가 아니다. 현재 문서 작업 중에는 앱 빌드·설치·테스트를 다시 실행하지 않았다.
