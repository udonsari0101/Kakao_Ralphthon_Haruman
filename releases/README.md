# 로컬 버전별 release 보존

상태: **보존 규칙만 작성. 이 디렉터리에 release APK를 제작·검증·업로드한 기록은 아직 없다.**

개발 재개 후 검증된 서명 release를 다음 형식으로 보존한다. 전체 절차는 [RELEASE_WORKFLOW.md](../RELEASE_WORKFLOW.md)를 따른다.

```text
releases/<versionName>-<versionCode>/
  haruman-<versionName>-<versionCode>-release.apk
  manifest.json
  release-notes.md
  SHA256SUMS.txt
  qa-summary.md
  publication.json
  evidence/
  download-verification/
```

- 완성된 버전 폴더와 APK를 덮어쓰지 않는다. APK가 바뀌면 새 versionCode의 새 폴더에 보존한다.
- APK는 GitHub Releases의 asset으로 올린다. Git source commit에 APK blob을 포함하지 않는다.
- Git에는 정제한 manifest·notes·checksum·QA 요약·발행 확인 기록을 남긴다.
- 원시 로그·기기 스크린샷·다운로드 확인용 APK는 로컬만 보존한다.
- manifest에는 source commit, tag, package, 버전, 서명 인증서 SHA256, APK SHA256, 실제 검증 상태를 기록한다.
- `publication.json`은 업로드 후 URL·asset·다운로드 해시 확인을 기록할 때 추가한다. 기존 APK와 확정 metadata는 변경하지 않는다.
- 키 파일·비밀번호·인증값·기기 serial은 이 폴더에 넣지 않는다.
- unsigned APK나 debug key로 서명한 APK를 release 배포본으로 보관·공개하지 않는다.
- 다른 package의 `releaseQa` 검증 결과는 최종 배포 APK 검증 결과와 구분한다.

기존 개발 APK는 [CHECKPOINT.md](../CHECKPOINT.md)에 기록된 로컬 체크포인트에 그대로 보존되어 있다. release 폴더로 복사하거나 이름만 바꾸어 검증된 release로 취급하지 않는다.
