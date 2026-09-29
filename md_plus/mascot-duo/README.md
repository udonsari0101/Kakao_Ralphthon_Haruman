# 모아·토리 캐릭터 인계 폴더

하루만 앱에 햄스터 **모아**(담기)와 다람쥐 **토리**(다시 보기)를 더하는 작업의 지침과 디자인 자원을 한곳에 모은 폴더다. 이 폴더만 전달하면 구현을 시작할 수 있다.

상태: `target` — 설계와 자원만 있고 앱 코드는 바뀌지 않았다.

## 무엇부터 볼까

1. **[preview.html](preview.html)** — 브라우저로 열면 캐릭터 자원 시트(라이트/다크), 기존 화면에 캐릭터를 얹은 목업, 시연 흐름을 한 번에 본다.
2. **[implementation-requirements.md](implementation-requirements.md)** — 구현 요구사항 본문. 우선순위(P0/P1/P2), 화면별 변경, 데이터 변경, 파일 지도, 검증, 3분 시연 시나리오.
3. [guides/character-bible.md](guides/character-bible.md) — 캐릭터 성격·역할·말투 원칙, 포즈↔자원 매핑.
4. [guides/copy-deck.md](guides/copy-deck.md) — 상황별 말풍선 문장.
5. [guides/motion-and-tokens.md](guides/motion-and-tokens.md) — 색 토큰, 크기, 모션 시간·곡선, 모션 감소 규칙, 촉각 반응.

## 폴더 구성

```
mascot-duo/
├─ README.md                       ← 지금 이 문서
├─ implementation-requirements.md  ← 구현 요구사항
├─ preview.html                    ← 자원 시트 + 화면 목업 (오프라인으로 열림)
├─ guides/
│  ├─ character-bible.md
│  ├─ copy-deck.md
│  └─ motion-and-tokens.md
└─ assets/
   ├─ android-drawable/  ← res/drawable 에 그대로 복사할 VectorDrawable 18개
   ├─ svg/               ← 같은 그림의 SVG (Figma·발표 자료용)
   ├─ png/               ← 발표·메신저 공유용 PNG (2배, 투명 배경)
   └─ generator/         ← 자원 생성 스크립트 (build.py: Python 3 표준 라이브러리만 사용)
```

## 자원 목록

| 파일 | 설명 |
|---|---|
| `mascot_moa_idle` · `receive` · `stuffed` · `happy` · `sleep` | 모아 5포즈 (240×240 뷰포트, 160dp) |
| `mascot_tori_idle` · `present` · `bury` · `wave` · `cheer` | 토리 5포즈 (240×240, 160dp) |
| `prop_acorn_buried` · `sprout` · `ready` | 대기 진행 도토리 3단계 (96×96, 48dp) |
| `prop_acorn` · `prop_tree_small` · `prop_tree_big` · `prop_gift` | 도토리 아이콘, 절약 숲 나무 2종, 담긴 물건 |
| `scene_duo_onboarding` | 온보딩용 두 캐릭터 장면 (360×240, 300dp) |

VectorDrawable의 그룹에는 `android:name`(예: `cheeks`, `tail`, `eyes`, `paws`, `sparkles`)이 붙어 있어 이후 AnimatedVectorDrawable로 부분 애니메이션을 붙일 수 있다. 기본 구현은 포즈 교체 + Compose `graphicsLayer` 변형으로 충분하다.

## 자원 수정 방법

그림을 고칠 때는 `assets/generator/assets.py`를 수정하고 아래를 실행한다. SVG와 Android XML이 함께 다시 만들어진다. PNG는 선택 사항으로, Node.js와 Playwright가 있으면 두 번째 명령으로 갱신한다.

```
cd assets/generator
python build.py
node export_png.js ../svg ../png
```

## 구현 착수 시

이 폴더는 설계·자원의 원본이다. 실제 구현 작업은 프로젝트 규칙대로 `planning` 스킬로 `docs/work/`에 작업 세션을 만들고, 요구사항 출처로 이 폴더의 `implementation-requirements.md`를 연결한다. 구현이 끝나면 확정된 동작은 [현재 구현](../../architecture/haruman/current.md)에 반영한다.
