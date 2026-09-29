# 개발 중 검증과 APK 배포 절차

기준일: 2026-09-29 · GOAL_SPEC **v3.2** · 상태: **RC2/code3 서명 APK 보존·GitHub 프리릴리스 발행·재다운로드 해시 검증 완료**

2026-09-29 **12:04:47–15:04:47 KST**의 180분 개발 작업이 진행 중이다. 첫 통합 소스 체크포인트 `dde656c`의 GitHub `main` 반영을 확인했다. 이후 수정과 최종 artifact 검증은 별도로 기록하며, 이 commit이 모든 후속 수정을 포함한다고 가정하지 않는다.

구현 범위는 [GOAL_SPEC.md](./GOAL_SPEC.md), 실제 진행 상황은 [todo.md](./todo.md), 운영은 [DEVELOPMENT_RUNBOOK.md](./DEVELOPMENT_RUNBOOK.md), API 경계는 [LINK_API_DESIGN.md](./LINK_API_DESIGN.md)를 따른다. 사용자의 개발 재개 지시가 과거 문서 전용 제한을 대체했다. `md_plus/mascot-duo`의 Markdown 5개와 제공 벡터를 반영했다. 실제 상품 API endpoint·인증은 아직 없어 production은 `NotConfigured`이며, 추가 API 연결 문서를 기다린다.

## 1. 확인된 현재 상태

[`apk-project/app/build.gradle.kts`](./apk-project/app/build.gradle.kts)를 읽어 확인한 값이다.

| 항목 | 현재 값 |
| --- | --- |
| applicationId | `org.ralphthon.haruman_ralphthon` |
| 현재 소스 후보 versionName / versionCode | `1.1.0-rc.2` / `3` |
| compileSdk / targetSdk / minSdk | `36` / `36` / `24` |
| release signingConfig | 외부 환경변수로 전용 release 키를 주입하는 설정 구현 |
| debug 패키지 분리 | `applicationIdSuffix` 없음 |
| product flavor | 없음 |

**최종 배포 증거:** RC2(`1.1.0-rc.2`, code3), 소스 `d6fa562349905764d60c0924d505a4eb8b1b91f7`와 APK SHA256 `3e136b7a876f0868bb0fb69dcdf1b2bcf3e28a1ed059fc2b2c9ddfd0672a0a90`를 고정했다. `apksigner verify`/v2, package·버전·`debuggable=false`와 정확한 APK의 API36 설치·주요 흐름·재실행을 확인했다. 공개 인증서 SHA256은 `9e301264b6a98f3a8c79db0374717d09e4384c9778ee1a9ae2acd155c3831f04`다. [버전별 폴더](./releases/1.1.0-rc.2-3/)에 보존하고 [GitHub Release](https://github.com/udonsari0101/Kakao_Ralphthon/releases/tag/v1.1.0-rc.2)의 첨부파일5개를 다시 내려받아 체크섬을 대조했다. [publication.json](./releases/1.1.0-rc.2-3/publication.json)에 발행 결과를 기록한다. RC1은 과거 내부 후보이며 최종 파일의 증거로 대신하지 않는다.

## 2. 개발 중 반복 검증과 todo 갱신

작업은 **변경 → 필요한 빌드·테스트 → 실기기 확인 → 문제 수정·재확인 → 기록·소스 체크포인트** 순서로 진행한다. 앱이 실행된 것과 변경한 기능이 실제 동작한 것을 구분한다.

| 시점 | 확인·기록 |
| --- | --- |
| 기능 작업 시작 | `todo.md` 상태를 진행 중으로 바꾸고 시작 시각·확인할 동작 기록 |
| 의미 있는 기능 연결 완료 | 해당 변경의 단위/UI 검증과 실제 폰의 입력·전환·저장 흐름 확인 |
| 버그 발견 | 재현 단계, 영향, 관찰한 오류, 미완료 항목을 즉시 기록 |
| 수정 후 | 같은 재현 절차로 해결 여부를 확인하고 관련 회귀 범위만 검증 |
| 단계 마감 | 완료·부분 완료·막힌 조건, 테스트 수/결과, APK 구분, 소스 commit을 기록 |

실기기에서는 링크 직접 입력과 공유 진입, 키보드·스크롤, 부분 응답의 수동 보완, 수정·재실행, 테마·모션처럼 해당 단계에서 바뀐 동작을 직접 확인한다. 서명·패키지 구분을 기록해 debug 검증을 release 검증으로 옮겨 적지 않는다.

원시 logcat, 기기 고유 식별자, 계정 화면은 Git에 올리지 않는다. 공개할 QA 기록은 기기 모델·Android 버전·검증 시각·동작·결과와 필요한 비식별 근거만 담는다.

### 2.1 실제 폰의 데이터 보존 테스트

개발 중 `connectedDebugAndroidTest` 실행 경로가 기기 앱을 제거하는 부작용이 발생했다. **사건 이전 휴대전화 내부 데이터 백업은 없었으며, 그때 잃은 데이터를 복구했다고 보고하지 않는다.** 이후 만든 백업이 이전 손실을 되돌린다는 뜻도 아니다. 데이터가 있는 실제 폰에서는 `connectedDebugAndroidTest`를 절대 실행하지 않는다. `uninstall`, `pm clear`, 서명 충돌 해소를 위한 삭제도 사용하지 않는다.

최종 소스의 **JVM62개 + 테스트 전용 저장 공간을 사용하는 Android13 실기기63개**, 별도 OS모션0 검사1개와 runner 회귀3개가 통과했다. 최신 실기기 실행 전후 운영3파일 SHA256이 동일했다. 초기36개 실행은 runner의 목록 파싱 오류로 빈 hash 비교가 무효였으며, 이후 `ls -1a`·엄격 파싱·tar 검증으로 수정했다. 테스트 수는 전체 화면·기기 조합이나 최종 signed APK 자체의 검수를 대신하지 않는다.

저장 데이터가 있는 폰은 APK만 빌드한 다음 [runner](./scripts/run_device_tests.py)로 명시한 한 기기에 update 설치하고 수동 instrumentation을 실행한다. 명령은 저장소 루트에서 실행하며 serial과 SDK 경로는 로컬에서만 입력한다.

```powershell
Set-Location -LiteralPath 'D:\Develop\ralphthon'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:DEBUG = ''
Push-Location -LiteralPath '.\apk-project'
try {
    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw '빌드 또는 JVM 테스트 실패' }
} finally { Pop-Location }
$deviceAdb = Read-Host 'adb.exe 전체 경로'
$deviceSerial = Read-Host '검증할 기기의 adb serial'
python .\scripts\run_device_tests.py --serial $deviceSerial --adb $deviceAdb --apk .\apk-project\app\build\outputs\apk\debug\app-debug.apk --test-apk .\apk-project\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
if ($LASTEXITCODE -ne 0) { throw '기기 검증 실패: 비공개 결과를 확인하세요' }
```

runner는 `run-as` 접근을 먼저 확인해 `shared_prefs`와 `files/ui-workspace/<SHA256(UTF-8 haruman_local_v1)>/`만 tar로 백업한다. 운영 workspace는 `c4621bc908b1d6e3ffcac4fd6fd6f238b3270793c907b46e1906f955fa4ed105`이며 테스트 UUID namespace와 `files/` 전체를 백업하지 않는다. 설치된 앱의 접근·백업 실패는 **설치 전 중단**하고, 부재는 성공한 상위 목록 조회로 확인한다. 이후 두 APK의 `install -r`, `am instrument -w`, `OK(n tests)`, 앱 존재와 운영 파일 해시 불변을 검사한다. 원시 출력·serial·백업은 Git에서 제외된 `apk-project/verification/private-backups/`에만 남는다. 비디버그 release의 `run-as` 실패를 데이터 삭제로 해결하지 않는다.

## 3. 단계별 GitHub main 체크포인트

목표 저장소는 `udonsari0101/Kakao_Ralphthon`, 기본 작업 반영 브랜치는 `main`이다. 구현과 GitHub 반영이 승인된 재개 작업에서는 이미 허용된 정상적인 단계 반영을 매번 다시 묻지 않고 진행한다. 다음 항목은 자동 반영 전 실제로 확인한다.

- 변경 파일이 현재 단계와 관련되고, 앱 소스·재현 설정·문서·정제한 QA 기록으로 한정되는지 확인한다.
- APK, keystore, 비밀번호, 로컬 설정, 빌드 캐시, 기기 로그가 staging에 들어가지 않았는지 확인한다.
- 단계의 동작·테스트 결과와 `todo.md` 완료 표기가 맞는지 확인한다.
- 원격 상태를 읽어 변경이 있으면 기존 작업을 보존하며 정상적으로 합친다. `main` force push로 덮어쓰지 않는다.
- commit과 push 뒤 원격 `main`이 반영된 commit을 포함하는지 확인한다.

초기 180분 구간의 20·60·100·150분 지점을 소스 체크포인트 후보로 사용하되, 실패한 기능을 시간만 맞춰 완료로 표시하지 않는다. 중간 소스 체크포인트는 Release 발행과 독립적이다. 서명 준비가 안 되어도 구현·문서의 확인된 진척은 `main`에 남길 수 있다.

## 4. 서명과 버전 정책

Android APK는 설치·업데이트 전에 서명되어야 한다. `assembleRelease`가 끝났다는 사실이나 파일명에 `release`가 있다는 사실은 서명 완료를 증명하지 않는다. 서명 없는 `app-release-unsigned.apk`는 배포 대상으로 취급하지 않는다. [Android 앱 서명](https://developer.android.com/studio/publish/app-signing)

배포에는 계속 유지할 **release 전용 키**를 사용한다. debug key로 release를 서명해 설치 충돌을 피하거나, 빌드마다 새 키를 만드는 방식은 사용하지 않는다. 기존 앱 업데이트는 인증서 호환성에 영향을 받으므로 배포 키와 공개 인증서 SHA256을 지속적으로 관리한다. [Android 서명과 업데이트](https://developer.android.com/studio/publish/app-signing#considerations)

| 서명 정보 | 보관 방식 |
| --- | --- |
| release keystore | Git 작업 폴더 밖의 지속적인 위치, 별도 안전한 백업 |
| keystore 경로·alias | 로컬 환경 설정 또는 비밀 설정에서 제공. manifest에 내부 경로를 공개하지 않음 |
| store/key 비밀번호 | 환경변수나 승인된 비밀 저장소에서 주입. 소스·명령 인자·로그·문서에 실제 값을 쓰지 않음 |
| 공개 인증서 SHA256 | 배포 manifest와 릴리스 노트에 기록 가능 |

현재 서명 설정은 `HARUMAN_RELEASE_STORE_FILE`, `HARUMAN_RELEASE_STORE_PASSWORD`, `HARUMAN_RELEASE_KEY_ALIAS`, `HARUMAN_RELEASE_KEY_PASSWORD`를 사용한다. RC1 서명에는 전용 키를 사용했다. 실제 키 경로·alias·비밀번호는 공개 문서나 명령 예시에 넣지 않는다. 이후 환경에서 키를 읽지 못하면 미완료로 남기며 debug 서명이나 unsigned APK 공개로 대체하지 않는다.

release 제작이 승인된 재개 작업에서는 먼저 기존 배포 키·서명 기록·배포 이력을 확인한다. **기존 키와 배포 이력이 없음을 확인했다면 새 release 키 준비를 해당 제작 권한에 포함해 진행할 수 있다.** 작업자는 Git 밖의 지속적인 위치에 키를 한 번 생성하고, 비밀값을 로컬 비밀 저장소 또는 접근 제한된 로컬 설정에 보관하고, 별도 안전한 백업과 공개 인증서 fingerprint를 기록한다. 실제 비밀값은 콘솔·문서·commit에 노출하지 않는다. 기존 키의 존재나 사용 이력이 불명확하면 해당 키를 덮어쓰거나 새 키로 대체하지 않고, 확인되지 않은 조건을 인계한다.

배포할 APK마다 `versionName`, `versionCode`, tag의 대응을 정한다. 같은 applicationId의 이후 배포본은 이전 배포본보다 큰 `versionCode`를 사용한다. Android는 versionCode를 다운그레이드 판정에 사용한다. 과거 debug는 code 1, 내부 RC1은 code 2이며 현재 소스 후보 RC2는 `1.1.0-rc.2`/code 3이다. 실제 APK의 값이 이 계획과 일치하는지 검증하며 보존·배포 전 바뀌면 해당 값으로 기록을 맞춘다. [Android 버전 관리](https://developer.android.com/studio/publish/versioning)

내부 RC1의 versionName은 `1.1.0-rc.1`이지만 해당 이름의 공개 tag/Release가 생성됐다는 뜻은 아니다. 최종 정식 배포에는 실제 APK의 정식 versionName과 대응하는 tag를 사용한다. 보존을 확정한 버전의 APK 바이트가 바뀌면 새 versionCode와 폴더를 만든다.

## 5. 기존 debug 데이터와 서명 충돌 처리

현재 개발용 폰은 production과 같은 applicationId를 사용하는 debug 검증 대상이다. 정확한 설치 서명과 대상 APK의 호환성은 설치 전에 확인한다. 이 앱에 다른 release 인증서로 서명한 APK를 `install -r`하면 서명 충돌이 발생할 수 있다. 이 경우 **앱 제거, `pm clear`, 사용자 데이터 삭제로 진행하지 않는다.** 내부 RC1은 별도의 깨끗한 emulator에서 검수했다. [Android 서명 호환성](https://developer.android.com/studio/publish/app-signing#considerations)

아래 suffix 분리는 이후 선택지다. **현재 Gradle에는 `.debug`/`.releaseqa` suffix나 flavor를 적용하지 않았다.** 실제 검증 대상을 존재하지 않는 variant로 기록하지 않는다.

| 용도 | package 설계 | 검증 의미 |
| --- | --- | --- |
| 기존 설치 앱 | 현재 package와 데이터를 유지 | 기존 기록·마이그레이션 상태 보존 |
| 이후 개발 debug | `applicationIdSuffix = ".debug"` 사용 검토 | 기존 설치를 덮지 않는 새 개발용 데이터 공간 |
| 선택적 `releaseQa` flavor | `.releaseqa` suffix, release 설정·안정적인 서명 사용 | 연결 폰에서 release 설정에 가까운 흐름 검증 |
| 최종 배포 release | `org.ralphthon.haruman_ralphthon` 유지 | GitHub에 올릴 최종 APK 자체 검증 |

Android build variant는 build type 또는 flavor별 applicationId suffix를 지원한다. `.debug`로 바꾸면 기존 데이터를 자동으로 이동시키는 것이 아니므로 기존 앱은 그대로 두고 신규 개발 데이터로 확인한다. [Android build variant](https://developer.android.com/build/build-variants)

`.releaseqa` APK는 package가 달라 **최종 배포 APK와 같은 파일이 아니다.** 해당 기기의 QA 결과만으로 production APK의 설치·공유·서명 검증을 완료했다고 표시하지 않는다. 최종 package를 충돌 회피 목적으로 `.preview`나 `.releaseqa`에 영구 고정하지 않는다.

최종 production APK 자체는 **서명 충돌이 없는 별도 실기기 또는 깨끗한 emulator**에서 설치·동작 검증할 수 있다. 연결된 실제 폰에서는 기존 데이터를 보존하며 debug 또는 `.releaseqa` 검수를 이어가고, 최종 production APK를 emulator에서 확인했다면 두 검증 대상·환경·결과를 각각 기록한다. 다른 Android 사용자/프로필도 앱 설치 상태에 영향을 줄 수 있어, **프로필을 만들면 동일 package의 인증서 충돌이 해결된다고 가정하지 않는다.** 이미 사용할 수 있는 프로필을 검증 대상으로 삼을 때도 설치 호환성과 기존 데이터에 영향이 없는지 확인해야 한다. [AOSP 다중 사용자 동작](https://source.android.com/docs/devices/admin/multi-user)

연결된 폰에서 동일 APK를 검증할 수 없다는 이유만으로 Release를 막지 않는다. 깨끗한 emulator에서 최종 production APK 검증을 완료했다면 실제 폰의 `.releaseqa` 결과와 함께 그 범위를 명시한다. `.releaseqa`의 성공만으로 최종 artifact 성공을 대신 기록하지 않으며, **실기기와 emulator 어느 쪽에서도 최종 production APK의 설치·동작을 검증할 수 없을 때** 해당 gate를 미완료로 인계하고 공개 Release 발행을 보류한다.

## 6. 배포 전 확인해야 할 실제 APK

검증 대상은 **GitHub에 업로드할 바로 그 파일**이다. 파일 복사본은 SHA256이 같아야 하며, 검사 후 APK 내용을 바꾸거나 재서명했다면 새 파일로 다시 검증한다.

| gate | 통과 조건 |
| --- | --- |
| 소스 | 실행 코드·리소스·빌드 설정이 특정 commit에 고정되고 미커밋 앱 변경이 없음 |
| 빌드 | 필요한 테스트와 release 빌드 성공, 최종 variant 확인 |
| 서명 | `apksigner verify` 성공, 공개 인증서 SHA256이 예정 release 인증서와 일치 |
| APK 정보 | package·versionName·versionCode·minSdk·debuggable 값이 예정값과 일치 |
| 최종 설치 | 최종 APK를 서명 호환 실기기 또는 깨끗한 emulator에 기존 데이터 손실 없이 설치하고 실행 |
| 주요 흐름 | 해당 버전의 입력·저장·편집·공유·재실행과 관련 회귀 확인 |
| 해시 | 로컬 보존본·검증본·업로드 대상으로 선택한 APK의 SHA256 일치 |
| 공개 설명 | API 미연결, 알려진 제한, 기존 debug 설치 충돌 등 실제 상태를 명시 |

공식 `apksigner`는 서명 확인과 인증서 출력 기능을 제공하고, `apkanalyzer`는 APK manifest의 package·버전·debuggable 등을 확인할 수 있다. 출력에서 공개할 값만 manifest에 옮기고 불필요한 개인 인증서 정보나 로컬 경로를 그대로 붙이지 않는다. [apksigner](https://developer.android.com/tools/apksigner), [apkanalyzer](https://developer.android.com/tools/apkanalyzer)

아래는 실제 SDK 도구 경로와 서명된 APK를 정한 뒤 사용할 검증 명령 형식이며 이 문서 작성 중 실행하지 않았다.

```powershell
& $releaseApkSigner verify --verbose --print-certs $releaseApk
& $releaseApkAnalyzer manifest application-id $releaseApk
& $releaseApkAnalyzer manifest version-name $releaseApk
& $releaseApkAnalyzer manifest version-code $releaseApk
& $releaseApkAnalyzer manifest debuggable $releaseApk
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
```

각 명령의 성공 여부를 확인하며, 서명 검증 실패나 잘못된 package를 발견하면 설치·발행 단계로 넘어가지 않는다. 불러올 환경변수·도구 경로·APK 경로를 실제 값으로 정하기 전에는 이 명령 블록을 그대로 실행하지 않는다.

## 7. 버전별 로컬 보존

로컬 최상위 폴더는 [`releases/`](./releases/README.md)다. 완성된 버전 디렉터리는 덮어쓰지 않으며 다른 APK 바이트가 생기면 새 versionCode의 디렉터리를 만든다.

```text
releases/
  README.md
  <versionName>-<versionCode>/
    haruman-<versionName>-<versionCode>-release.apk
    manifest.json
    release-notes.md
    SHA256SUMS.txt
    qa-summary.md
    publication.json
    evidence/                 # 원시 로그·이미지는 로컬만 보존
    download-verification/    # 업로드 후 다시 받은 APK; 로컬만 보존
```

서명·최종 QA가 끝나기 전 파일은 별도 임시 작업 경로에서 다룬다. 최종 폴더에는 검증한 APK와 확정한 metadata를 한 번 보존한다. 공개 후 확인한 Release URL·asset ID·다운로드 해시는 `publication.json`을 나중에 추가해 기록한다. 최종 APK·manifest·notes·기존 해시 파일을 조용히 덮어쓰지 않는다.

Git은 `README.md`와 버전별 정제한 `manifest.json`, `release-notes.md`, `SHA256SUMS.txt`, `qa-summary.md`, `publication.json`을 추적한다. **APK 자체는 Git blob으로 commit하지 않고 GitHub Release asset으로 업로드한다.** GitHub Releases는 tag를 기반으로 배포 파일을 첨부하는 기능을 제공한다. [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)

루트 `.gitignore`의 release 보존 정책은 `*.apk`, `*.aab`와 `/releases/**/evidence/`, `/releases/**/download-verification/`, `/releases/**/staging/`를 제외하는 것이다. 공개할 metadata 전체가 제외되지 않았는지 확인한다. ignore 반영과 확인은 저장소 정리 작업에서 진행하며 이 문서와 동일한 정책을 유지한다.

## 8. manifest와 릴리스 노트

아래 항목은 후속 배포에서 실제 관찰값으로 채운다. 빈 값이나 미실행을 임의의 성공 값으로 만들지 않는다.

| manifest 항목 | 내용 |
| --- | --- |
| sourceCommit / tag | 빌드한 소스의 full commit SHA, 해당 commit을 가리키는 tag |
| packageName / versionName / versionCode / variant | 최종 APK에서 확인한 값 |
| artifactName / sizeBytes / apkSha256 | 보존할 최종 APK의 이름·크기·SHA256 |
| signingCertificateSha256 / signatureVerified | 공개 인증서 fingerprint와 실제 검증 결과 |
| debuggable / buildStartedAt / buildFinishedAt | 실제 APK와 빌드 기록 |
| toolchain | 실제 JBR·Gradle·AGP·Kotlin·SDK/build-tools 버전 |
| tests | 테스트 종류·실행 시각·개수·실패 수·관련 근거. 예제와 기능 테스트를 구분 |
| finalArtifactQa | 최종 production APK를 검증한 실기기 또는 emulator 구분, 모델/AVD·Android 버전·설치/동작/재실행 결과 |
| releaseQaVariantQa | 별도 검증 package를 사용했다면 그 결과와 최종 APK와의 차이 |
| apiIntegration | production의 `NotConfigured` 또는 `RealApiVerified`, 실제 연결 여부와 검증 환경. Fake 검증은 tests에 별도 기록 |
| knownLimitations | 미완료·미검증·기존 설치 호환 제한 |

릴리스 노트에는 사용자에게 보이는 변경, 검증한 동작, 버전·package·서명 fingerprint·APK SHA256, source commit, API 연결 상태, 알려진 제한을 기록한다. 구매 결정 기록을 주문 완료로 표현하지 않고 API 미설정을 실제 자동 추출 성공으로 표현하지 않는다.

`SHA256SUMS.txt`는 APK와 확정된 metadata의 해시를 담고 자기 자신은 제외한다. 나중에 추가되는 `publication.json`은 기존 checksum 목록을 다시 쓰지 않고 그 안에 업로드·다운로드 비교 결과를 기록한다. 원시 인증값·키 파일·기기 serial은 manifest와 Release asset에 포함하지 않는다.

### 8.1 구현된 로컬 보존 도구

[`scripts/stage_release.ps1`](./scripts/stage_release.ps1)은 실제 `apksigner`·`apkanalyzer` 출력, `debuggable=false`, package·버전·공개 인증서, full source SHA와 앱 경로의 미커밋 변경 여부, 정확한 APK SHA256에 대한 QA 증거를 확인한 뒤 새 `releases/<versionName>-<versionCode>/`를 만든다. 이미 있는 버전 폴더는 덮어쓰지 않는다. 도구 호출 중에만 `DEBUG` 환경변수를 비워 SDK BAT의 echo 출력을 막고 원래 값을 복원한다. 이 스크립트 자체는 빌드·서명·설치·tag 생성·업로드를 하지 않는다.

QA JSON의 정확한 형식은 스크립트 첫머리를 따른다. 최종 APK의 해시·full source SHA, 설치/실행/주요 흐름/재실행 관찰, 기기 또는 emulator 환경, 빌드/테스트/도구 버전, API 연결 여부와 검증 환경이 필요하다. 현재 API 상태는 `NotConfigured`, `realApiConnected=false`다. 빈 항목을 가짜 성공 값으로 채우지 않는다. QA 원문은 비공개 경로에 두며 공개 metadata에는 정제된 항목만 기록한다.

아래 변수에는 **최종 파일과 관찰값**을 지정한다. RC1 검사 결과를 수정된 새 APK의 QA로 재사용하거나 7자리 `dde656c`를 최종 full source SHA로 넣지 않는다.

```powershell
Set-Location -LiteralPath 'D:\Develop\ralphthon'
$releaseCertificate = '9e301264b6a98f3a8c79db0374717d09e4384c9778ee1a9ae2acd155c3831f04'
.\scripts\stage_release.ps1 `
    -ApkPath $releaseApk `
    -SourceCommit $releaseSourceCommit `
    -Tag $releaseTag `
    -ExpectedPackage 'org.ralphthon.haruman_ralphthon' `
    -ExpectedVersionName $releaseVersionName `
    -ExpectedVersionCode $releaseVersionCode `
    -ExpectedCertificateSha256 $releaseCertificate `
    -ApkSigner $releaseApkSigner `
    -ApkAnalyzer $releaseApkAnalyzer `
    -QaEvidencePath $releaseQaEvidencePath `
    -QaSummary $releaseQaSummary
```

성공해야 버전별 로컬 보존 완료로 기록한다. GitHub 발행과 다운로드 검증은 다음 절의 별도 단계다.

## 9. 정확한 commit에서 GitHub Release 발행

소스와 metadata commit은 구분한다. 앱을 빌드한 소스 commit을 `S`라고 할 때, QA 기록과 manifest를 담은 후속 문서 commit이 `main`에 추가되어도 Release tag는 **실제로 빌드한 `S`**를 가리킨다. 문서 commit이 생겼다는 이유로 tag를 검증하지 않은 최신 HEAD에 자동 생성하지 않는다.

실행 순서는 다음과 같다.

1. 단계의 소스와 버전 설정을 commit하고 `main`에 반영해 소스 SHA `S`를 확정한다.
2. 해당 소스에서 release를 빌드·서명하고 필요한 검증을 마친다. 수정이 필요하면 새 source commit부터 다시 시작한다.
3. 최종 APK·확정 metadata를 새 로컬 버전 폴더에 보존하고, 정제한 metadata만 GitHub `main`에 반영한다.
4. tag가 이미 있으면 가리키는 commit과 기존 Release를 먼저 비교한다. 충돌하는 tag를 이동하거나 강제 덮어쓰지 않는다.
5. `S`를 지정해 tag를 만들고 원격 tag의 commit을 대조한다.
6. 단계 후보는 prerelease로 만들고 `Latest`로 표시하지 않는다. 최종 gate를 통과한 정식 버전만 정식 Release와 `Latest`로 지정한다.
7. 업로드된 asset을 새 로컬 디렉터리로 내려받아 원본 APK와 SHA256을 비교하고 URL·asset 정보·결과를 기록한다.

`gh release create`는 기존 원격 tag를 요구하는 `--verify-tag`, 대상 commit을 지정하는 `--target`, 파일 기반 노트와 prerelease/latest 옵션을 제공한다. 기본 branch의 현재 HEAD에 tag가 자동 생성되는 동작에 의존하지 않는다. [GitHub CLI release create](https://cli.github.com/manual/gh_release_create)

아래 명령은 **발행 권한이 있는 재개 작업에서, 위 gate가 끝난 실제 값들을 지정한 후 사용할 형식**이다. 저장소·tag·source SHA·asset 경로를 확인하지 않은 상태에서 실행하지 않는다. 현재 후보의 tag는 실제 RC2/code3 APK와 대응하도록 정하며 이 예시가 tag 또는 Release 생성 완료를 뜻하지 않는다. `$releaseNotes`는 확정된 `release-notes.md`, `$releaseQaSummaryFile`은 확정된 `qa-summary.md`의 파일 경로다. QA 설명 문자열인 `$releaseQaSummary`와 혼동하지 않는다.

```powershell
git tag -a $releaseTag $releaseSourceCommit -m "Haruman $releaseTag"
git push origin "refs/tags/$releaseTag"
gh release create $releaseTag $releaseApk $releaseManifest $releaseChecksums $releaseNotes $releaseQaSummaryFile `
  --repo udonsari0101/Kakao_Ralphthon `
  --verify-tag --target $releaseSourceCommit `
  --title "하루만 $releaseTag" --notes-file $releaseNotes `
  --prerelease --latest=false
```

첨부 파일은 **APK, manifest.json, SHA256SUMS.txt, release-notes.md, qa-summary.md의 5개**다. 체크섬 목록에 든 네 파일을 모두 내려받아 검증할 수 있도록 노트와 QA 요약도 asset으로 첨부한다. `--notes-file`로 표시하는 본문과 노트 파일 첨부는 별개다.

정식 배포를 생성할 때는 prerelease 옵션을 빼고 `--latest`를 명시한다. RC의 versionName이 APK에 들어 있다면 이름만 정식으로 바꾸지 말고 정식 버전 APK를 별도 제작·검증한다. 같은 tag나 asset 이름의 기존 파일을 `--clobber`로 교체하지 않는다.

업로드가 중간에 실패하면 이미 올라간 tag·Release·asset을 조회해 같은 해시의 남은 작업만 이어간다. HTTP 성공이나 목록에 파일이 보이는 것만으로 업로드 확인을 끝내지 않고 다운로드 바이트를 대조한다. [GitHub CLI release download](https://cli.github.com/manual/gh_release_download)

## 10. 180분 운영과 최종 30분

0–20분 모델·호환 경계, 20–60분 링크 입력·공유·서비스, 60–100분 저장·편집·실패 처리, 100–150분 테마·반응형·캐릭터·모션, 150–180분 최종 QA·배포 검증이라는 기본 배분을 유지한다. 서명키·기기 설치 호환성처럼 마지막에 막힐 수 있는 조건은 시작 구간에 읽기 확인해 조기에 기록한다.

기능이 일찍 연결되어도 예정된 실기기 QA와 인계를 생략하고 조기 종료하지 않는다. 남은 시간은 요구된 품질 확인, 재현된 결함 수정, 관련 재검증에 사용한다. 의미 없는 빌드 반복이나 시간 채우기 대기는 하지 않는다. 마지막 30분은 최종 APK·서명·동작·보존·Release 확인을 위해 확보한다.

180분이 되었다는 이유로 gate를 실패한 APK를 배포하거나 미검증 기능을 완료로 표시하지 않는다. 남은 필수 작업과 구체적 장애를 `todo.md`에 기록하고 확인된 소스·로컬 산출물을 보존한다. API 계약 초안은 수령했지만 실제 연결 주소·인증·운영 조건이 아직 없으므로 미연결 상태를 명시하고 준비 구조와 독립 로컬 기능을 검증한다.

## 11. 최종 상태 표현

| 상태 | 보고할 내용 |
| --- | --- |
| 소스 체크포인트 완료 | main commit, 구현 범위, 테스트 결과 |
| release 빌드만 완료 | 서명·설치·동작 gate의 남은 항목. 배포 완료라고 하지 않음 |
| 서명 또는 최종 APK 검증 불가 | 키 이력 미확인·서명 실패 또는 실기기/emulator 모두에서 최종 설치·동작 확인 불가, 기존 데이터 보존, 공개 배포 보류 |
| 로컬 release 보존 완료 | 버전 폴더·APK·SHA256·최종 artifact 검증 결과 |
| GitHub 배포 완료 | Release/tag/source commit·asset URL·다운로드 SHA256 일치·로컬 보존 위치 |

현재 확인된 상태는 **내부 서명 RC1 제작·emulator 설치 검수, 최종 공개 배포 대기**다. 최종 APK의 QA·불변 폴더 보존·GitHub asset 업로드·다운로드 해시 확인까지 마쳐야 배포 완료로 표시한다. 실제 배포가 끝나면 GitHub 다운로드 링크와 로컬 버전 APK 경로, 검증 결과와 남은 제한을 함께 제공한다.
