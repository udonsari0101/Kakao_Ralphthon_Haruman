# 하루만 · Kakao Ralphthon

사고 싶은 마음을 잠시 보관하고, 정한 시간이 지나면 모아·토리와 다시 살펴보는 Android 앱이에요.

## 현재 상태

**2026-09-29 후속 개발: 테라스 마당·토리·상품 추가 버그·로고·실제 API 연결을 반영한 RC3 검수 중이에요.** 아래 RC2 배포는 이전 버전이며, 최신 검수는 [RC3 QA](QA_TERRACE_RC3.md)와 [todo](todo.md)에 기록해요.

- 상품 링크 입력/공유 수신, 직접 텍스트 입력, 보관·정보 수정·구매 결정/포기·소비 기록
- 모아·토리 캐릭터, 볼주머니5단계, 주·월 도토리 마당, 스와이프 정리 시간과 되돌리기
- 선택형 소비 목표, 구매 전 금액 미리보기, 저장방 목록과 지난 기간 마당 일기
- 빨강 테마, 라이트·다크·시스템 모드, 말풍선·모션 감소 설정
- API 응답 검증/자동 추가/부분 보완 구조, 취소·늦은 응답·중복 저장 방어

**사용자 요청으로 시험용 API 주소·토큰을 APK에 내장했어요.** 공개 Git에는 값이 없고 로컬 빌드 환경에서 주입해요. APK에서는 공통 토큰을 추출할 수 있어 시험용으로 사용해요. 제공된 11번가·올리브영·무신사·네이버 링크4개는 실제 폰에서 불러와 각각 한 번 저장했어요. [등록 검수](LINK_REGISTRATION_QA.md)에 결과를 남겼어요. 오류가 나면 직접 입력할 수 있고, API 계약에 없는 상품 설명도 수동 입력해요. 인스타그램 신규 추가·유튜브·스크린샷 업로드·앱 내부 페이지 파싱은 제외했어요.

상품 상세에는 같은 상품을 담은 가상100명의 시간에 따른 선택을 그래프로 보여줘요. ‘예시 데이터’라고 표시하며 실제 이용자 통계나 구매 추천에 쓰지 않아요. ‘마당으로’ 버튼은 없애고 뒤로가기와 기본 탭으로 이동해요.

테라스 마당에는 월요일부터 일요일까지 주별 밭이 쌓여요. 토리는 밭 아래에서 구덩이 유무에 따라 표정이 바뀌고, 구매 구덩이를 누르면 “구덩이 제거는 500원이에요” 안내만 보여줘요. 실제 결제나 기록 삭제는 하지 않아요.

구매 결정은 소비 기록이며 실제 주문을 실행하지 않습니다. 예시 상품은 절약 실적에서 제외합니다.

### 이전 RC2 검증 및 배포

JVM62개와 Android13 실기기 테스트63개가 통과했어요. 작성 복원·API 상태·테마·모션·마당·목표를 격리 검증했고, 큰 글꼴 이전 기간 버튼·저장방 닫기 노출·목표 비율 표시를 보완했어요. 실제 관찰 범위와 한계는 [QA 기록](QA_REPORT.md)과 [todo](todo.md)에 기록합니다.

**[1.1.0-rc.2 배포 APK 받기](https://github.com/udonsari0101/Kakao_Ralphthon/releases/tag/v1.1.0-rc.2)** · code3 · 소스 `d6fa562`.

최종 서명 APK 자체를 API36 emulator에서 설치·입력·수정·구매 포기·cold 재실행·테마·화면 크기별로 검수했어요. [로컬 버전 폴더](releases/1.1.0-rc.2-3/)에 보존했고, GitHub 첨부파일5개를 다시 내려받아 체크섬을 확인했어요. 자세한 범위와 제한은 [최종 QA 요약](releases/1.1.0-rc.2-3/qa-summary.md)에 있어요.

연결된 실제 폰에는 같은 소스의 debug APK를 유지했어요. Release와 서명이 달라 바로 덮어쓸 수 없으니 데이터를 옮기기 위해 기존 앱을 먼저 삭제하지 마세요. 실제 시스템 공유 메뉴의 cold/warm 진입은 검수 도구의 승인 차단으로 직접 확인하지 못했어요.

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

초기 [개발 가이드](DEVELOPMENT_GUIDE.md), [API 조사](API_PREREQUISITES.md), [링크 파싱 조사](COUPANG_PARSING_TECHNICAL.md)는 이력을 위해 보존하며 현재 목표와 충돌하면 최신 명세를 따릅니다.

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
