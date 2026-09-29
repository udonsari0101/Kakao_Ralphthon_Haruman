# 색 토큰 · 모션 · 촉각 반응 명세

상태: `target`. 앱 전체 모션 원칙은 [원본 v2 명세 §8](../../../reference/kakao-ralphthon-source/GOAL_SPEC.md)을 따른다: 일상 조작은 절제, 축하 순간에만 풍성하게, 모션 감소 시 정적 상태로 즉시 전환, 데이터는 애니메이션 완료에 의존하지 않음.

## 1. 캐릭터 색 (테마와 무관하게 고정)

자원 안에 이미 들어 있는 값이다. Compose에서 같은 색이 필요하면(말풍선 꼬리, 도토리 진행 표시 등) `MascotColors` 객체로 옮긴다.

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `Outline` | `#5B3A22` | 모든 외곽선 |
| `MoaFur` / `MoaLight` / `MoaPaw` | `#F4B56E` / `#FFF4E4` / `#FAD7B5` | 모아 털·배·발 |
| `ToriFur` / `ToriDark` / `ToriLight` | `#C9773D` / `#A85E2B` / `#FBE2C2` | 토리 털·손발·배 |
| `ToriTail` / `ToriTailStripe` | `#B9662F` / `#E39B5C` | 꼬리 |
| `Blush` | `#FF8F8F` (55%) | 볼 홍조 |
| `AcornCap` / `AcornNut` | `#8A5A2B` / `#DB9550` | 도토리 |
| `Soil` / `Leaf` | `#C29366` / `#78C166` | 흙더미 / 새싹 |
| `Canopy` / `Trunk` | `#71C06D` / `#A06F3F` | 절약 숲 나무 |
| `Sparkle` | `#FFC83D` | 반짝임 |
| `AccentLine` / `AccentWarm` | `#8FA6D6` / `#F0A060` | 장식선(다크에서도 보이도록) |

## 2. 캐릭터 무대·말풍선 (테마 연동)

| 토큰 | 라이트 | 다크 | 쓰임 |
|---|---|---|---|
| `MascotStage` | `#FFF4E4` | `#2A2420` | 보관함 헤더 카드, 결과 카드의 캐릭터 뒤 바탕 |
| `BubbleSurface` | `AppColors.Surface` | 다크 Surface | 말풍선 면 |
| `BubbleBorder` | `AppColors.Border` | 다크 Border | 말풍선 테두리 0.5–1dp |
| `BubbleText` | `AppColors.Text` | 다크 TextPrimary | 14sp / 20sp line-height, 최대 2줄 |
| 강조(CTA·스와이프 라벨) | `AppColors.Blue` | 목표 테마 도입 시 `BrandPrimary` | 기존 주 행동 색을 그대로 쓴다 |
| 보내기 라벨 | `AppColors.Green` / `GreenTint` | 다크 대응값 | 기존 “안 샀어요” 의미 색과 일치 |

현재 앱은 파랑 라이트 고정이다. 목표 설계의 빨강·다크 테마가 들어오면 위 테마 연동 토큰만 교체한다. 캐릭터 자원은 바꾸지 않는다.

## 3. 크기

| 위치 | 캐릭터 크기 | 비고 |
|---|---|---|
| 온보딩 장면 | 너비 300dp (`scene_duo_onboarding`) | 좁은 화면에서는 `fillMaxWidth(0.85f)` |
| 보관함 헤더 | 모아 88dp, 토리 56dp | 카드 높이 112dp |
| 추가 시트 상단 | 모아 56dp | 말풍선 옆 |
| 저장 “받았다” 오버레이 | 모아 140dp | 하단 중앙 |
| 정리 시간 카드 위 | 토리 96dp | 카드 상단에 걸치게 |
| 결과·정리 결과 | 120–140dp | 기존 64dp 아이콘 박스 자리 |
| 상품 행 도토리 | 28dp | 기존 시계 아이콘 자리 |
| 절약 숲 나무 | 작은 나무 32dp, 큰 나무 40dp | 한 줄 최대 6그루, 최대 2줄 |

## 4. 모션

| ID | 상황 | 동작 | 시간 · 곡선 | 모션 감소 시 |
|---|---|---|---|---|
| MO-1 | 캐릭터 대기(호흡) | `graphicsLayer` scaleY 1→1.03, 피벗 발밑 | 2400ms 왕복, `FastOutSlowIn`, 무한 | 정지 |
| MO-2 | 포즈 전환 | 이전 포즈 fade-out + 새 포즈 scale 0.92→1 | 180ms fade, spring(dampingRatio 0.55, stiffness 500) | 즉시 교체 |
| MO-3 | 캐릭터 탭 | rotation 0→-4→4→0 + 말풍선 교체 | 300ms | 말풍선만 교체 |
| MO-4 | 말풍선 등장 | alpha 0→1, translateY 6→0dp | 180ms | 즉시 표시 |
| MO-5 | 저장 “받았다” | 모아 `RECEIVE`가 아래서 올라옴(translateY 40→0) → 선물 아이콘이 볼 쪽으로 이동하며 scale 1→0 → 모아 `STUFFED` 150ms → `IDLE` | 전체 900ms 이하, 탭하면 즉시 종료 | 생략, 스낵바만 |
| MO-6 | 도토리 단계 변경 | 새 단계 아이콘 scale 0.8→1 | 220ms | 즉시 |
| MO-7 | 정리 카드 드래그 | 카드가 손가락을 따라 이동, rotation = dragX / width × 12° , 라벨 alpha = |progress| | 실시간 | 회전 없이 이동만 |
| MO-8 | 카드 판정 | 임계값(너비 30% 또는 속도 1200dp/s) 넘으면 화면 밖으로 날아감, 못 넘으면 원위치 spring | 240ms / spring | 버튼 결과와 같게 fade 150ms |
| MO-9 | 다음 카드 | 뒤 카드 scale 0.95→1, translateY 8→0dp | 220ms | 즉시 |
| MO-10 | 조금 더(연장) | 카드가 아래로 scale 1→0.3 하며 흙더미 쪽으로 이동 + 토리 `BURY` | 300ms | fade 150ms |
| MO-11 | 절약 금액 카운트업 | 0→금액 숫자 증가 | 400–650ms, `LinearOutSlowIn` | 최종값 즉시 |
| MO-12 | 축하 confetti | Canvas 입자 24–36개, 빨강·코랄·웜톤 + 도토리색, 위에서 흩어져 낙하 | 1200–1600ms, 이벤트당 1회 | 생략, 정적 체크·문구 |
| MO-13 | 나무 자라기(기록 진입 시 새 나무) | 새로 추가된 나무만 scaleY 0→1, 피벗 밑동 | 360ms, spring | 즉시 |

규칙:

- 모션 감소 판단: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`를 앱 시작과 화면 재개 때 읽어 `LocalReduceMotion`으로 내려준다. P1의 MY 설정 “캐릭터 움직임 줄이기”가 켜져 있어도 같은 값으로 취급한다.
- 축하는 이벤트 ID(결정한 `item.id` + `decidedAt`, 정리 세션 ID)로 한 번만 재생하고, 재생 여부를 `rememberSaveable`에 둔다. 회전·재구성·탭 전환·목록 재진입으로 다시 재생하지 않는다.
- 저장·결정·합산은 버튼/스와이프 판정 시점에 즉시 반영한다. 연출은 그 뒤에 겹쳐 보여 줄 뿐이다.
- 화면에 동시에 움직이는 캐릭터는 최대 2개. 목록 행 안에서는 캐릭터를 움직이지 않는다(도토리 단계 전환만).

## 5. 촉각 반응(Haptics)

`LocalHapticFeedback` 사용. 반복 진동 금지.

| 상황 | 종류 |
|---|---|
| 스와이프가 임계값을 처음 넘을 때 | `HapticFeedbackType.TextHandleMove` (한 번) |
| 카드 판정 확정 | `HapticFeedbackType.LongPress` |
| 저장 “받았다” | `HapticFeedbackType.TextHandleMove` |
| 축하 | 없음(시각 효과만) |
