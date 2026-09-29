# 개발 중 검증과 APK 배포 절차

기준일: 2026-09-29 · 현재 상태: **운영 절차 설계만 작성**

이 문서는 개발을 재개한 뒤 연결 Android 실기기로 반복 검증하고, `todo.md`를 갱신하며, 단계별 소스를 GitHub `main`에 보존하고, 검증된 release APK를 GitHub Releases와 로컬 버전 폴더에 남기는 절차다. 문서를 작성하면서 앱 수정, 빌드, adb, 키 생성, Release 발행은 실행하지 않았다. 현재 문서·디자인 자산의 GitHub main 반영은 앱 구현·release 제작과 구분한다.

구현 범위는 [GOAL_SPEC.md](./GOAL_SPEC.md), 실제 진행 상황은 [todo.md](./todo.md), 재개 후 운영은 [DEVELOPMENT_RUNBOOK.md](./DEVELOPMENT_RUNBOOK.md), 링크 API 경계는 [LINK_API_DESIGN.md](./LINK_API_DESIGN.md)를 따른다. 사용자는 이번 작업을 명세·todo 반영으로 확정했다. 앱 구현·기기 검증·release 제작은 이후 개발 재개 지시에 따라 실행한다.

## 1. 확인된 현재 상태

[`apk-project/app/build.gradle.kts`](./apk-project/app/build.gradle.kts)를 읽어 확인한 값이다.

| 항목 | 현재 값 |
| --- | --- |
| applicationId | `org.ralphthon.haruman_ralphthon` |
| versionName / versionCode | `1.0` / `1` |
| compileSdk / targetSdk / minSdk | `36` / `36` / `24` |
| release signingConfig | 정의·연결 없음 |
| debug 패키지 분리 | `applicationIdSuffix` 없음 |
| product flavor | 없음 |

현재 보존 APK와 이전 테스트 결과는 [CHECKPOINT.md](./CHECKPOINT.md)에 기록되어 있다. 이를 **서명된 release APK의 제작·설치·검증 증거로 사용하지 않는다.** release keystore가 준비되어 있는지, 배포 인증서가 무엇인지, 최종 APK가 기기에 설치되는지는 아직 확인하지 않았다.

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

후속 구현에서 사용할 환경변수 이름의 예는 `HARUMAN_RELEASE_STORE_FILE`, `HARUMAN_RELEASE_STORE_PASSWORD`, `HARUMAN_RELEASE_KEY_ALIAS`, `HARUMAN_RELEASE_KEY_PASSWORD`다. 현재 존재하거나 구성됐다는 뜻은 아니다. 키가 준비되지 않았다면 서명 준비를 미완료로 기록하며 임시 debug 서명이나 unsigned APK 공개로 대체하지 않는다.

release 제작이 승인된 재개 작업에서는 먼저 기존 배포 키·서명 기록·배포 이력을 확인한다. **기존 키와 배포 이력이 없음을 확인했다면 새 release 키 준비를 해당 제작 권한에 포함해 진행할 수 있다.** 작업자는 Git 밖의 지속적인 위치에 키를 한 번 생성하고, 비밀값을 로컬 비밀 저장소 또는 접근 제한된 로컬 설정에 보관하고, 별도 안전한 백업과 공개 인증서 fingerprint를 기록한다. 실제 비밀값은 콘솔·문서·commit에 노출하지 않는다. 기존 키의 존재나 사용 이력이 불명확하면 해당 키를 덮어쓰거나 새 키로 대체하지 않고, 확인되지 않은 조건을 인계한다.

배포할 APK마다 `versionName`, `versionCode`, tag의 대응을 정한다. 같은 applicationId의 이후 배포본은 이전 배포본보다 큰 `versionCode`를 사용한다. Android는 versionCode를 다운그레이드 판정에 사용한다. 현재 code 1 이후의 실제 배포 이력을 확인한 뒤 다음 값을 정한다. [Android 버전 관리](https://developer.android.com/studio/publish/versioning)

단계 배포 tag의 예는 `v0.1.0-rc.1`, 다음 후보는 `v0.1.0-rc.2`다. 이것은 이름 규칙의 예시이며 이미 존재하는 버전이 아니다. 최종 정식 배포에는 실제 APK의 정식 versionName과 대응하는 tag를 사용한다. 버전·APK가 바뀌면 새 versionCode와 폴더를 만든다.

## 5. 기존 debug 데이터와 서명 충돌 처리

기존 폰에는 production과 같은 applicationId의 debug APK가 있을 수 있다. 정확한 설치 서명은 재개 후 읽기 검증으로 확인한다. 이 앱에 다른 release 인증서로 서명한 APK를 `install -r`하면 서명 충돌이 발생할 수 있다. 이 경우 **앱 제거, `pm clear`, 사용자 데이터 삭제로 진행하지 않는다.** [Android 서명 호환성](https://developer.android.com/studio/publish/app-signing#considerations)

권장 분리는 다음과 같다. 아직 적용한 Gradle 설정이 아니다.

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
| apiIntegration | NotConfigured / Fake 검증 / 실제 API 검증 중 관찰된 상태 |
| knownLimitations | 미완료·미검증·기존 설치 호환 제한 |

릴리스 노트에는 사용자에게 보이는 변경, 검증한 동작, 버전·package·서명 fingerprint·APK SHA256, source commit, API 연결 상태, 알려진 제한을 기록한다. 구매 결정 기록을 주문 완료로 표현하지 않고 API 미설정을 실제 자동 추출 성공으로 표현하지 않는다.

`SHA256SUMS.txt`는 APK와 확정된 metadata의 해시를 담고 자기 자신은 제외한다. 나중에 추가되는 `publication.json`은 기존 checksum 목록을 다시 쓰지 않고 그 안에 업로드·다운로드 비교 결과를 기록한다. 원시 인증값·키 파일·기기 serial은 manifest와 Release asset에 포함하지 않는다.

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

아래 명령은 **발행 권한이 있는 재개 작업에서, 위 gate가 끝난 실제 값들을 지정한 후 사용할 형식**이다. 저장소·tag·source SHA·asset 경로를 확인하지 않은 상태에서 실행하지 않는다.

```powershell
git tag -a $releaseTag $releaseSourceCommit -m "Haruman $releaseTag"
git push origin "refs/tags/$releaseTag"
gh release create $releaseTag $releaseApk $releaseManifest $releaseChecksums `
  --repo udonsari0101/Kakao_Ralphthon `
  --verify-tag --target $releaseSourceCommit `
  --title "하루만 $releaseTag" --notes-file $releaseNotes `
  --prerelease --latest=false
```

정식 배포를 생성할 때는 prerelease 옵션을 빼고 `--latest`를 명시한다. RC의 versionName이 APK에 들어 있다면 이름만 정식으로 바꾸지 말고 정식 버전 APK를 별도 제작·검증한다. 같은 tag나 asset 이름의 기존 파일을 `--clobber`로 교체하지 않는다.

업로드가 중간에 실패하면 이미 올라간 tag·Release·asset을 조회해 같은 해시의 남은 작업만 이어간다. HTTP 성공이나 목록에 파일이 보이는 것만으로 업로드 확인을 끝내지 않고 다운로드 바이트를 대조한다. [GitHub CLI release download](https://cli.github.com/manual/gh_release_download)

## 10. 180분 운영과 최종 30분

0–20분 모델·호환 경계, 20–60분 링크 입력·공유·서비스, 60–100분 저장·편집·실패 처리, 100–150분 테마·반응형·캐릭터·모션, 150–180분 최종 QA·배포 검증이라는 기본 배분을 유지한다. 서명키·기기 설치 호환성처럼 마지막에 막힐 수 있는 조건은 시작 구간에 읽기 확인해 조기에 기록한다.

기능이 일찍 연결되어도 예정된 실기기 QA와 인계를 생략하고 조기 종료하지 않는다. 남은 시간은 요구된 품질 확인, 재현된 결함 수정, 관련 재검증에 사용한다. 의미 없는 빌드 반복이나 시간 채우기 대기는 하지 않는다. 마지막 30분은 최종 APK·서명·동작·보존·Release 확인을 위해 확보한다.

180분이 되었다는 이유로 gate를 실패한 APK를 배포하거나 미검증 기능을 완료로 표시하지 않는다. 남은 필수 작업과 구체적 장애를 `todo.md`에 기록하고 확인된 소스·로컬 산출물을 보존한다. API 문서가 오지 않았다면 미연결 상태를 그대로 기록한 채 승인된 준비 구조 범위만 검증한다.

## 11. 최종 상태 표현

| 상태 | 보고할 내용 |
| --- | --- |
| 소스 체크포인트 완료 | main commit, 구현 범위, 테스트 결과 |
| release 빌드만 완료 | 서명·설치·동작 gate의 남은 항목. 배포 완료라고 하지 않음 |
| 서명 또는 최종 APK 검증 불가 | 키 이력 미확인·서명 실패 또는 실기기/emulator 모두에서 최종 설치·동작 확인 불가, 기존 데이터 보존, 공개 배포 보류 |
| 로컬 release 보존 완료 | 버전 폴더·APK·SHA256·최종 artifact 검증 결과 |
| GitHub 배포 완료 | Release/tag/source commit·asset URL·다운로드 SHA256 일치·로컬 보존 위치 |

현재는 이 표의 release 실행 결과가 생성되지 않았다. 실제 배포가 끝나면 사용자에게 GitHub 다운로드 링크와 로컬 버전 APK 경로, 검증 결과와 남은 제한을 함께 제공한다.
