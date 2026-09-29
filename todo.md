# 하루만 개발 todo — v3.2

기준: 2026-09-29 · 진행 중 · **12:04:47–15:04:47 KST**

[목표](GOAL_SPEC.md) · [API](LINK_API_DESIGN.md) · [디자인](UI_CHARACTER_DESIGN.md) · [애셋](ASSET_PLAN.md) · [운영](DEVELOPMENT_RUNBOOK.md) · [배포](RELEASE_WORKFLOW.md) · [추가 MD 적용 이력](md_plus/APPLIED.md)

## 실시간 진행판

| 항목 | 현재 상태 |
| --- | --- |
| 목표 | 쿠팡 링크/수동 입력·정보 수정·모아/토리·테마·반응형·정리/절약 숲 구현과 검수, signed release 배포 |
| 현재 작업 | 임시 저장 경계 보강·실폰36개 검증 완료, 반응형/테마/모션 시각 검수 |
| 시간 | 시작 12:04:47, 목표 마감 15:04:47 KST |
| 마지막 원격 확정 | dde656c — API 경계·로컬 상품 흐름·mascot-duo 구현, 원격 SHA 확인 |
| 빌드/테스트 | debug/AndroidTest APK 빌드 성공, JVM38개·실폰 Android36개 통과 |
| API | 계약 적용·HTTPS adapter·상태 처리 구현. 실제 base URL/인증 미제공, production은 NotConfigured |
| 캐릭터 | md_plus/mascot-duo MD5개·벡터18개 적용. 원본 보존 |
| release | 전용 키로 RC1 서명·API36 emulator 설치/기본 흐름 확인. 최종 APK/게시 전 |
| md_plus | 15초 해시 감시, 약3분/단계 경계 검토. 13:10 스캔 신규 없음 |

`[x]`는 해당 범위의 관찰 근거가 있을 때만 표시한다. 구현과 실서버/실폰 전체 검증을 구분한다. 아래 테스트 fake는 테스트 소스에만 존재하며 실제 상품 수신을 증명하지 않는다.

## T01 계약·모델·호환

- [x] API 초안2개와 최신 애셋45개, mascot-duo 가이드5개를 계획에 적용했다.
- [x] LINK_INPUT/LINK_SHARE/MANUAL, 가격 null/0, 원본 API snapshot/사용자 수정값, desire, 결정 가격 snapshot을 분리했다.
- [x] 기존 JSON/Instagram/예시 기록 읽기 호환, 신규 설치 빈 보관함, 예시 실적 제외를 구현·JVM 검증했다.
- [x] SYSTEM 기본값과 LIGHT/DARK 저장, 말풍선/모션 감소 설정을 구현했다.
- [x] 재개 직전 중지 체크포인트 소스50개 hash 일치를 확인했다.
- [ ] 기존 기기 데이터 보존: **테스트 도구 부작용 발생**, 아래 사건 기록 참조. 보존 완료로 표시하지 않는다.

## T02 링크 입력·Android 공유

- [x] 직접 링크 입력/명시적 붙여넣기와 text/plain 공유, 쿠팡 HTTPS 호스트·scheme 검사, 여러 URL 선택을 구현했다.
- [x] 인스타 신규 추가/유튜브/사진 선택/스크린샷 업로드를 제외했다.
- [x] 공유로 들어온 URL 유지, 작성 draft 보관/복원, 같은 요청 중복 방지를 격리 실폰 테스트로 검증했다.
- [ ] 실제 공유 chooser의 아이콘·cold/warm 진입·여러 링크·작성 중 공유를 폰에서 시각 확인한다.
- [ ] 회전·프로세스 재생성에서 같은 intent가 재실행되지 않는지 최종 검수한다.

## T03–T04 API 경계·응답·자동 저장

- [x] ProductLinkService/HTTPS transport/strict codec, success/partial/error·HTTP 오류·옵션 식별자 검증을 구현했다.
- [x] 테스트 응답의 정상 자동 저장1회, partial 보완 draft, cancel/close/수동저장 뒤 늦은 응답 무시를 검증했다.
- [x] 실패 시 실제 다음 행동을 안내하고 입력을 유지한다. 미연결 시 임의 상품/가격을 만들지 않는다.
- [x] acquisitionId 저장 단위 중복 방지와 로컬 저장 실패 처리·기존 결정 금액 보존을 검증했다.
- [ ] 실제 API 주소·인증·timeout 정책이 도착하면 설정하고 사용자 제공3개 링크로 실서버 검증한다.
- [ ] 실제 API/이미지 호스트와 장치 네트워크에서 자동 추가·부분 응답·오류를 확인한다.
- [ ] 현재 API 계약에 없는 description 자동 수신은 후속 문서가 필요하다. 지금은 수동 설명으로 제공한다.

## T05 수동 입력·수정

- [x] 상품명·선택 가격·설명·메모·대기시간·갖고 싶은 정도를 입력한다.
- [x] 공백 이름/음수/소수/초과 금액 거부, 가격 미정과0원 구분, 숨긴 이미지 상태를 UI 테스트로 확인했다.
- [x] 기존 상세에서 수정, 원본 링크/API snapshot·결정 시점 금액 유지, 설정 변경 후 draft 유지 테스트 통과.
- [ ] 폰에서 직접 작성→저장→수정→재실행 흐름과 IME/스크롤을 시각 검수한다.
- [x] 연속 공유 draft FIFO·기존 수정 복원·API 재조회 시 수동 값 유지·중복 상품 전환·대량 리뷰 상태를 8개 추가 실폰 테스트로 검증했다.

## T06–T08 테마·반응형·모션

- [x] 빨강 CTA·라이트/다크 palette·시스템 모드 설정, 공유 컴포넌트48dp, 대비 보정, 시스템바/시작 배경을 적용했다.
- [x] 320dp×420dp/글꼴2배 정리 버튼 접근성 테스트 통과.
- [x] 누름/ripple, 저장 수신 overlay<900ms, 카드240/300ms, 금액550ms·일회성 confetti를 구현했다.
- [x] OS 애니메이션0 및 앱 모션 감소 설정을 읽고 화면 복귀 때 재확인한다.
- [ ] 320/360/412dp, 600/840dp, 가로, 글꼴1/1.3/2, IME 상태를 실폰/별도 emulator에서 확인한다.
- [ ] LIGHT/DARK/SYSTEM 즉시 적용·재실행·OS 변경 추종과 cold-start 첫 프레임을 검수한다.
- [ ] 정상 모션·감소/0배율·연타·재진입을 녹화하고 겹침/멈춤/반복 축하/터치 차단을 확인한다.
- [ ] 최종 폰 설정은 작업 전 값으로 복원한다.

## T12 모아·토리 디자인

- [x] R1 공통10포즈/말풍선/접근성/제공벡터18개, 원본 assets 해시/매핑 기록.
- [x] R2/R3 온보딩·모아의 방·개수별 포즈·정리 CTA 연결. 실폰 기본 보관함/기록/MY 화면 확인.
- [x] R4 desire 저장/복원, 저장 성공 후 모아 수신 반응 연결.
- [x] R5 도토리 BURIED/SPROUT/READY 경계 규칙 JVM 확인, 행/상세 연결.
- [x] R6 카드 스와이프/버튼·즉시 저장·중복 방지·4초 되돌리기·요약/명시 텍스트 공유, 실폰 격리 UI 테스트 통과.
- [x] R7/R8/R9 결과 캐릭터·세션당 축하1회·실제 기록 절약 숲·빈 상태·해요체 연결.
- [x] P1 캐릭터 탭/결정 토리/MY 말풍선·모션 설정 반영.
- [ ] 전체 상품 흐름에서 포즈/반응형/축하/숲 추가 결과를 폰에서 다시 확인한다.
- [ ] 추가 이미지가 꼭 필요하면 승인된 범위에서 테마에 맞게 생성하고 provenance/적용 위치를 남긴다. 현재 제공 애셋으로 구현해 새 생성본은 없다.

## T09–T11 검수·체크포인트·배포

- [x] 첫 통합 assembleDebug/assembleDebugAndroidTest 성공, JVM38개(도메인37+기본1) 실패0.
- [x] Android28개(Scaffold11+Review6+Workflow10+기본1) 실폰 수동 instrumentation 실행 통과.
- [x] 이후 테스트는 고유 test prefs만 사용한다. 현재 production prefs는 로컬 비공개 backup을 만든다.
- [x] 기존 release 이력/키 설정 없음 확인 후 Git 밖 전용 키·접근 제한·DPAPI 비밀번호·로컬 중복 백업 준비.
- [x] 구현 체크포인트 dde656c를 main에 반영하고 원격 SHA 확인.
- [ ] 최종 source commit 고정 후 signed release 빌드·서명/cert/package/version/debuggable/hash 확인.
- [ ] 최종 정확 APK를 깨끗한 API36 emulator에 설치해 시작·입력·수정·공유·재실행 확인. 실폰 debug QA와 구분.
- [ ] releases/<versionName>-<versionCode>/에 APK·manifest·SHA256·release notes 보존(기존 폴더 덮어쓰기 금지).
- [ ] GitHub Releases에 동일 APK 업로드, 다운로드 후 hash 비교. 버전별 로컬 경로와 URL 인계.
- [ ] 실제 API 미연결/확인된 제한/데이터 사건을 최종 문서와 인계에 명시.

## 실행·결함 기록

| KST | 내용/증거/후속 |
| --- | --- |
| 12:04:47 | 구현 재개, 소스50개 hash 확인, Android13 실폰 연결, md_plus 감시 시작 |
| 12:19 | mascot-duo 가이드5개 수령. R1–R9 반영, Instagram/스크린샷 옛 문구는 재도입하지 않음 |
| 12:22 | defaultHours JVM setter 충돌 발견→updateDefaultHours로 수정 |
| 12:34 | 첫 통합 빌드/JVM38개 성공, debug update 설치 성공 |
| 12:36–12:38 | Gradle connectedDebugAndroidTest가 앱까지 제거한 부작용 확인. 이후 앱 재설치. 이전 기기 prefs 백업 없으므로 기존 데이터 복구를 보장하지 못함. 사용자에게 알림. 이 명령은 이후 실제 폰에서 사용하지 않음 |
| 12:38 | 잠금 상태에서 시작한 theme 테스트만 화면 탐색 실패; 깨어 있는 기기 재실행 통과 |
| 12:44–12:46 | 현재 prefs 비공개 로컬 tar 백업 후 APK update+am instrument로Android28개 전부 통과. 앱 제거 없음 |
| 12:46 | 전용 release 키 준비, 기존 AVD와 별개의 Haruman_Release_API_36 생성/부팅 |
| 12:50–12:58 | dde656c 기반 signed RC1 제작, debug=false·v2 서명 확인. 별도 emulator에서 온보딩→수동 입력→저장→정리 스와이프→절약 요약 확인. 게시 전 내부 후보 |
| 13:00 | 녹화에서 저장 모아가 CTA를 덮는 문제 발견→상단 고정100dp 영역으로 이동. 작은 보관함 빈 상태 높이/글자2배 입력 모드 줄바꿈 개선 |
| 13:01 | 직접 ADB 공유 Intent 주입이 자동 승인 검토에서 정책 차단(상세 사유 미제공). 직접 시스템 공유 검수는 미완료; 모델/격리 UI 검증과 구분 |
| 13:05–13:08 | 안전한 별도 runner로 실폰36개 테스트 통과, 앱 설치 유지 및 production prefs/workspace SHA 불변 확인. draft/대량 리뷰는 AtomicFile로 보관 |

### 기기 임시 설정 복원값

font_scale=1, accelerometer_rotation=1, user_rotation=0, animator/transition/window_animation_scale=1.0, 처음 관찰한 night=yes, stay_on_while_plugged_in=0, wm size=1080×2280/density440. 테스트 동안 USB stayon을 켰다. 원시 기기 식별자/스크린샷/로그/백업은 Git에 올리지 않는다.

## 종료 기준

첫 빌드에서 끝내지 않는다. 남은 시간 동안 기능/시각/모션/반응형 결함을 수정하고, 최종 정확 release APK와 배포 해시를 검증한다. 외부 API 정보가 끝까지 없으면 그 조건을 명시한 로컬 기능 배포로 완료 범위를 제한하며 실제 연동 성공으로 표현하지 않는다. 미완료/사건을 숨기지 않는다.
