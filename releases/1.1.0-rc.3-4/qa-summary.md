# Final artifact QA

- APK SHA256: c5ef704f9c33008bd9e7d48948c276c0bea35bead519f13e333ef20b71d3378d
- Source commit: 067ce4782480a317e56df82f3e45528e97e34f4e
- Environment: emulator / Haruman_Release_API_36 / Android 16 (API 36)
- Checked at: 2026-09-29T07:45:26.6170910+00:00
- Installed: true
- Launched: true
- Main flows: true
- Restarted: true
- API: RealApiVerified (Exact signed RC3 APK on Android 16; embedded configuration; one 11st product extraction returned name and 21900 KRW; no duplicate saved.), real connection: True

상품 링크 API·수동 보완·예시 소비 패턴·테라스 마당·로고를 반영했어요. 상품 추가/수정 초안을 분리하고 저장 충돌을 방어해요. 토리 옆 말풍선은 터치로 바뀌며, 구덩이 구매하기→완료 후 해당 구덩이가 재실행 후에도 사라져요. 구매 기록과 금액은 보존하며 실제 결제는 하지 않아요. 정확 release에서 내장 API 응답, 제거/주월/행복 토리/기록 보존/재시작, 큰 글꼴 팝업과 라이트·다크 로고를 확인했어요.

## Tests

Final source JVM 91 passed. Physical Android 13 instrumentation: 110 total, 109 executed passes and 1 opt-in live API skip; all 3 production file hashes unchanged. Physical normal-app pit removal/cold restart preserved 9 existing products and all other fields, decisions and amounts. Exact signed release independently passed install, launch, embedded live API response, persistent pit removal, week/month zero-pit/happy Tori, retained 12345-KRW BUY record, cold restart, OS font 2 dialog, light/dark logos. Release assemble and lintVital passed.

## Known limitations

- The user-authorized common test API token is bundled in the APK and can be extracted. Actual values and signing material are excluded from public source and documentation.
- Four supplied links were registered on the physical development build. This exact signed APK verified one live provider response; this does not guarantee every product or shop succeeds.
- Pit removal is a persisted local app action. The 500-KRW purchase dialog does not call a payment provider or charge money; product decisions and amounts remain unchanged.
- Consumption-pattern charts use a fixed 100-person example, not real user analytics.
- The physical phone retains the debug-signed same-package app to preserve its data. The exact release was tested on a separate AVD; debug and release cannot be directly updated across signing certificates.
- Direct cold/warm Android system share-menu QA was previously blocked by automatic approval review and remains unverified. Internal share handling tests do not replace this check.
- Testing covers one physical Android 13 phone and one Android 16 AVD plus selected layout/font tests, not every OEM, display, rotation or time-boundary combination.
- An earlier RC2 test-tool run removed the phone app before backups existed. Recovery of that earlier data remains unverified; later runs use backup, install-r, isolated data and strict production hashes.
- Detailed digging/watering choreography and real calendar-boundary report playback remain outside the verified scope.
