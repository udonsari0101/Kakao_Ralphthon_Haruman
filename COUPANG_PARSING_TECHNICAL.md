# 쿠팡 상품 정보 파싱 기술 검토

> **보류된 조사 기록 — v2 범위 변경:** 사용자 요청으로 링크 파싱은 미지원이며 틀만 남긴다. 아래의 HTTP/WebView 검증·파서 구현·25분 조사 계획은 현재 실행하지 않는다. 다음 개발은 스크린샷/직접 입력, 문서 수령 후 연결할 이미지 API 준비 구조에 집중한다. 최신 실행 기준은 [GOAL_SPEC.md](./GOAL_SPEC.md)와 [todo.md](./todo.md)다. 아래 관찰 결과는 과거 조사로 보존한다.

> 조사일: 2026-09-29, 약 10:52–10:57 KST  
> 범위: 사용자가 제공한 상품 링크 3개에 대한 읽기 전용 조사. 앱 구현·빌드·기기 테스트는 중지 상태.  
> 실행 계획: [GOAL_SPEC.md](./GOAL_SPEC.md), [todo.md](./todo.md) · 현재 구현: [CHECKPOINT.md](./CHECKPOINT.md)

## 1. 결론

**상품 데이터는 확인했다. Android 앱에서 그 페이지를 안정적으로 가져오는 경로는 아직 검증하지 않았다.**

- 세 상품 모두 일반 Chrome 세션에서 상품명·대표 이미지·상품/옵션 ID·가격을 확인했다. 페이지 DOM에는 `Product` JSON-LD와 Open Graph가 있었다.
- 같은 링크를 인증 정보를 넣지 않은 일반 HTTP GET으로 요청하면 최종 상품 페이지가 모두 `403 Access Denied`였다. 서버에 HTTP 파서만 붙이는 방식은 현재 관찰 결과로는 작동하지 않는다.
- Chrome은 이미 로그인된 사용자 세션이었다. 쿠키·토큰을 읽거나 복사하지 않았다. 브라우저 성공의 원인이 로그인인지, 브라우저 환경인지, 다른 조건인지 분리 검증하지 않았다.
- 두 홈캠 상품에서는 **JSON-LD `offers.price`도 쿠폰 적용 가격**이었다. 구조화 데이터라고 해서 모든 사용자에게 적용되는 가격은 아니다.
- 다음 작업에서는 **Android의 실제 접근 가능성부터 짧게 검증**한다. 정상 페이지를 확보하면 JSON-LD → OG → 상품 영역 DOM 순서로 추출한다. 차단되면 명확한 실패 상태와 수동 입력으로 연결한다.
- 앱 내부의 사용자에게 보이는 WebView는 검증 후보이다. Chrome에서 열렸다는 결과로 Android WebView 성공을 보장할 수 없다. WebView 경로도 실패하면 자동 추출은 미완료로 보고한다.

이 문서는 접근 허가나 서비스 약관 준수를 확정하는 문서가 아니다. 공개적으로 읽히는 데이터의 형태를 조사한 것이며, 상용 운영을 위한 수집·캐시·이미지 사용 조건은 별도 확인이 남아 있다.

## 2. 실제 관찰 결과

### 2.1 링크 및 상품 식별

| ID | 제공 링크 / 확인된 상품 | productId | itemId | vendorItemId |
| --- | --- | --- | --- | --- |
| C1 | [레고 마인크래프트 21595 엔더드래곤](https://www.coupang.com/vp/products/9542831658?itemId=28466573033&vendorItemId=95416915229) | `9542831658` | `28466573033` | `95416915229` |
| C2 | [ipTIME 가정용 CCTV 홈캠 네트워크카메라](https://www.coupang.com/vp/products/9047670772), 선택 옵션 `C400G × 1개` | `9047670772` | `26553563024` — JSON-LD sku와 화면에서 확인 | 미확인 |
| C3 | [제공된 단축 링크](https://link.coupang.com/a/hdpv0PnT9E) → [타포 360도 회전형 FHD 화질 스마트 Wi-Fi 홈 CCTV 실내용](https://www.coupang.com/vp/products/8542956019?itemId=24735276366&vendorItemId=91744077867), 선택 옵션 `Tapo C200C × 1개` | `8542956019` | `24735276366` | `91744077867` |

C1 원본에는 `searchId`, `sourceType`, `storeId`, `subSourceType`, `vendorId` 등의 검색·광고 파라미터가 있었다. 위 링크는 식별에 필요한 `itemId`, `vendorItemId`를 남긴 비교용 URL이다. C3 리디렉션의 일회성 추적 파라미터는 연구 기록에서 제외했다. 원본 URL은 앱에서 출처 보존용으로 별도 취급한다.

### 2.2 전송 방식별 결과

| ID | 인증 정보를 주입하지 않은 HTTP 요청 | 일반 Chrome 세션의 상품 DOM |
| --- | --- | --- |
| C1 | `403`, `text/html`, 402 bytes, `Access Denied` | 제목·대표 이미지·Product JSON-LD·OG·가격 표시 확인 |
| C2 | `403`, `text/html`, 397 bytes, `Access Denied` | 동일 + 선택 옵션·쿠폰 조건 확인 |
| C3 | 첫 응답 `302`, 상품 URL로 이동 후 `403`, 402 bytes | 리디렉션 후 동일 + 선택 옵션·쿠폰 조건 확인 |

HTTP 조사: Python requests, 링크마다 새 Session, GET, 요청 timeout 15초, 리디렉션 최대 6회로 제한한 단발 조사. 차단 HTML에는 OG/JSON-LD/Next 데이터가 없었다. 이것은 해당 PC·해당 시점의 결과이며, 모든 서버와 네트워크에서 항상 실패한다는 뜻은 아니다. 403의 정확한 발생 원인도 확정하지 않았다.

브라우저 조사: 이미 열람 가능한 Chrome 세션에서 공개 상품 영역과 `head` 메타데이터 및 `application/ld+json`만 읽었다. 장바구니·구매·쿠폰 받기·옵션 변경·로그인 조작은 하지 않았다. 계정 이름, 배송지, 쿠키, 인증 토큰, 전체 hydration payload, 전체 페이지 HTML은 연구 파일에 보존하지 않았다.

### 2.3 가격 — 관찰값이며 현재 판매가 보증이 아님

| ID | JSON-LD `offers.price` | 취소선 기준 가격 | 별도 가격/조건 | 파싱 결과의 해석 |
| --- | ---: | ---: | --- | --- |
| C1 | 72,500원 | 93,900원 | 관찰한 가격 영역에는 별도 쿠폰 문구 없음 | 확인한 세션의 표시가. 조건이 전혀 없다고 단정하지 않음 |
| C2 | 18,400원 | 49,000원 | `12,000원 웰컴백 쿠폰할인`, 적용 전으로 읽히는 취소선 30,400원 | 조건부 표시가. 12,000원을 상품가로 오인하면 안 됨 |
| C3 | 25,900원 | 48,900원 | `12,000원 웰컴백 쿠폰할인`, 적용 전으로 읽히는 취소선 37,900원 | 조건부 표시가. 37,900원과 48,900원의 역할도 구분 |

쿠폰 적용 전 금액의 해석은 DOM 문구·취소선 및 차액(C2 30,400−12,000=18,400, C3 37,900−12,000=25,900)을 종합한 것이다. 익명 사용자 가격은 검증하지 않았다. 구매 확정 시점의 가격은 원문에서 다시 확인해야 한다.

정리한 필드 증거는 [coupang-observations-2026-09-29.json](./research/coupang-observations-2026-09-29.json)에 있다. 이는 도구 출력에서 필요한 값만 옮긴 기록이며 원본 HTML 덤프가 아니다.

## 3. 데이터가 있는 위치와 추출 우선순위

### 3.1 1순위: `script[type="application/ld+json"]`의 Product

세 페이지에서 실제 확인한 구조의 축약 예(C1):

```json
{
  "@type": "Product",
  "sku": "9542831658-28466573033",
  "name": "레고 마인크래프트 21595 엔더드래곤",
  "image": ["https://thumbnail.coupangcdn.com/thumbnails/remote/492x492ex/image/retail/images/2026/05/12/15/0/9ee86b91-8cc0-412c-8ccb-b48aed16270f.png"],
  "brand": {"@type": "Brand", "name": "레고"},
  "offers": {
    "@type": "Offer",
    "priceCurrency": "KRW",
    "price": "72500",
    "availability": "https://schema.org/InStock",
    "priceSpecification": {
      "@type": "UnitPriceSpecification",
      "price": "93900",
      "priceType": "https://schema.org/StrikethroughPrice"
    },
    "url": "https://www.coupang.com/vp/products/9542831658?itemId=28466573033&vendorItemId=95416915229"
  }
}
```

`Product`에는 이름·이미지·sku·brand·offers 등이, `Offer`에는 가격·통화·재고 상태 등이 정의되어 있다. 다만 페이지 제공자가 채운 값과 현재 선택한 상품의 대응은 별도로 검증해야 한다. [Schema.org Product](https://schema.org/Product), [Schema.org Offer](https://schema.org/Offer)

구현 규칙:

1. JSON-LD의 최상위 객체·배열·`@graph`, 문자열/배열 형태의 `@type`을 처리한다. 깨진 script 한 개 때문에 전체 추출을 실패시키지 않는다.
2. 상품 페이지의 productId/선택 itemId에 맞는 Product만 채택한다. 추천 상품의 첫 Product를 무조건 사용하지 않는다. 여러 후보가 충돌하면 `Partial`로 내려 확인을 요청한다.
3. `image`는 문자열·배열·ImageObject를 처리하고, `//...` 주소는 HTTPS로 정규화한다.
4. `offers`가 여러 개이면 현재 옵션과 대응하는 Offer를 찾는다. `AggregateOffer.lowPrice`만 있으면 선택 옵션의 확정 가격으로 저장하지 않는다.
5. `offers.price`는 **가격 후보**로 수집한다. `priceSpecification.price`의 StrikethroughPrice는 기준 가격으로 분리한다.
6. 페이지의 쿠폰·회원·카드·수량 조건을 함께 조사한다. 조건을 판별하지 못하면 `UNKNOWN`으로 남긴다.

### 3.2 2순위: Open Graph

세 페이지에 `og:title`, `og:image`, `og:url`이 있었다. 제목에는 `- 카테고리 | 쿠팡` 접미사가 있었으므로 JSON-LD `name` 또는 상품 h1을 우선한다. OG만 확보되면 제목·이미지만 채우고 가격은 비워 둔다.

주의: 세 페이지의 `link[rel=canonical]`은 productId만 남겼다. C1/C3의 `og:url`도 itemId가 없었다. **canonical 태그를 그대로 저장하면 선택 옵션이 사라질 수 있다.** 정규화 URL은 유효한 입력/최종 URL과 검증된 Offer/sku를 조합해 별도로 만든다.

### 3.3 3순위: 상품 영역 DOM

조사 당시 데스크톱 페이지에서 확인한 선택자:

| 목적 | 확인한 선택자 | 적용 시 주의 |
| --- | --- | --- |
| 상품명 | `h1.product-title` | 광고·추천 카드 제목과 분리 |
| 대표 이미지 | `img[alt="Product image"]` | 복수 후보·lazy src·HTTPS 주소 검증 |
| 상품 헤더 | `.product-buy-header` | 이 인접 상품 영역을 기준으로 범위 제한 |
| 가격/조건 묶음 | `.price-container` | 전체 문구를 의미별로 해석. 첫 숫자/최솟값 사용 금지 |
| 선택 옵션 표시 | `.option-picker-select > div`의 선택 라벨 영역 | 숨겨진 옵션 목록 전체의 textContent를 선택 옵션으로 쓰지 않음 |
| 상품 번호 등 | `.product-description` | productId/itemId와 교차 확인 |

이 선택자는 **관찰한 데스크톱 DOM 기준**이다. 모바일/WebView, A/B 배치, 로그인 상태에서 달라질 수 있다. Tailwind 스타일 클래스나 `nth-child` 순번에 의존하는 가격 추출은 우선 경로로 사용하지 않는다.

## 4. URL 처리와 중복 식별

### 입력·정규화

- 입력은 공유 텍스트에서 URL을 분리한 뒤 URI parser로 검증한다. `coupang.com.evil.example`, `https://coupang.com@evil.example`, javascript/file/data/intent 스킴은 상품 추출 경로에서 거부한다.
- 첫 버전의 네트워크 진입 host는 확인한 `www.coupang.com`, `coupang.com`, `link.coupang.com`으로 제한하고 경로는 `/vp/products/<digits>` 또는 `/a/<token>`만 허용한다. 다른 실제 공유 도메인이 필요하면 샘플로 검증 후 추가한다.
- HTTPS만 요청한다. HTTP 입력은 알려진 host일 때 HTTPS로 바꾼 다음 동일 검증을 거친다. userinfo·비표준 포트는 거부한다.
- 리디렉션은 매 hop 검증한다. 임의 외부 host/내부 IP로 넘어가거나 횟수를 넘으면 중단한다. 정규화 전에 자동 리디렉션으로 모두 따라가면 검증이 무의미해진다.
- `productId`, `itemId`, `vendorItemId`는 숫자 문자열로 저장한다. 잘못된 중복 query 값은 임의 선택하지 않고 명시적으로 거부하거나 미확인으로 처리한다.
- `originalUrl`은 사용자가 준 링크, `canonicalUrl`은 추적 파라미터를 제외하고 검증된 옵션 ID를 남긴 비교용 링크다. 로그에는 원본 추적 파라미터를 남기지 않는다.

Android 역시 URI 문자열의 부분 일치로 host를 검증하지 말고 scheme와 host를 각각 검사하도록 안내한다. [Android Unsafe URI Loading](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading)

### 중복 판정

확정된 `(productId, itemId, vendorItemId)`를 기본 비교 키로 쓴다. vendorItemId가 없는 C2는 **미확인**을 유지한다. 누락값을 임의로 추정하거나 같은 productId의 모든 옵션을 합치지 않는다. 처음에는 가능한 중복을 안내하고 사용자가 확인하도록 한다. 단축 링크와 긴 링크가 같은 옵션으로 해석되면 기존 pending 항목으로 안내한다. 구매/포기 이력의 재등록 정책은 pending 중복 처리와 분리한다.

## 5. 권장 구현 구조 및 기술 스택

현재 APK에는 네트워크 권한과 추출기가 없다. 아래 내용은 다음 세션의 제안이며 현재 구현 사실이 아니다.

| 영역 | 현재 / 다음 선택 | 이유 |
| --- | --- | --- |
| Android UI | 기존 Kotlin + Compose + Material 3 유지 | Figma/TDS 기반 화면을 이어서 개선 |
| 비동기 상태 | ViewModel + Coroutines/StateFlow를 필요한 입력 화면부터 적용 | 취소·중복 요청·회전·늦은 응답 처리 |
| URL 및 순수 파서 | Kotlin URI 처리 + `org.json`, HTML 파싱 필요 시 Jsoup | 기존 JSON 사용과 연결, 브라우저 전송과 파서 책임 분리 |
| HTTP 전송 | 검증된 단일 HTTP client(OkHttp 후보) | timeout·리디렉션·응답 제한을 명시하기 쉬움 |
| 렌더링 전송 후보 | 사용자에게 보이는 Android WebView | HTTP 차단 시 별도 가능성 검증. 자동 성공 가정 금지 |
| 이미지 | Compose 이미지 로더(Coil 후보) + 로컬 placeholder | 실패 시에도 저장 흐름 유지 |
| 저장 | 기존 SharedPreferences JSON에 호환 가능한 필드 추가 | 3시간 안에 DB 전면 교체를 동시에 하지 않음 |
| 서버 | 이번 목표에 필수 아님 | 먼저 기기에서 가능한 전송 경로를 확인. 기존 FastAPI 구상은 보류 |
| 테스트 | JUnit 순수 파서/상태 테스트 + Compose UI + 실제 공유 흐름 | fixture 성공과 실페이지 성공을 구분 |

새 라이브러리를 도입할 때 공식 문서와 Kotlin/minSdk 호환성을 확인하고 버전을 catalog에 고정한다. 이 문서는 검증하지 않은 최신 버전 번호를 지정하지 않는다.

권장 분리:

```text
공유/붙여넣기
  → CoupangUrlResolver (정규화·ID·리디렉션 검증)
  → ProductPageSource (HTTP / 검증을 통과한 visible WebView)
  → CoupangProductParser (JSON-LD / OG / 제품 DOM)
  → ProductDraft (출처·가격 조건·수집 시각 포함)
  → 사용자 확인/수정
  → 기존 LocalStore에 확정 정보 저장
```

실패도 같은 입력 화면에서 끝까지 처리한다. `Unavailable`은 수동 입력으로 저장 가능하지만 자동 추출의 성공으로 집계하지 않는다.

### 반환 계약 제안

```text
ProductDraft
  originalUrl, canonicalUrl?
  productId?, itemId?, vendorItemId?, optionLabel?
  title?, imageUrl?, brand?
  priceCandidates[] { amountWon, role, conditionText?, source }
  displayedPriceWon?, referencePriceWon?, currency?
  priceCondition: NONE_OBSERVED | COUPON | MEMBERSHIP | CARD | UNKNOWN
  availability?, fetchedAt, source: JSON_LD | OG | DOM | MANUAL
  missingFields[], warnings[]

ExtractionResult
  Success(draft)                  # 핵심 항목 추출, 저장 전 확인 필요
  Partial(draft, missingFields)   # 예: 제목만 있거나 가격 조건 불명
  Unavailable(reason)            # BLOCKED / TIMEOUT / UNSUPPORTED / PARSE_ERROR
```

금액은 KRW의 음수 아닌 정수 Long으로 검증한다. 가격 미확인은 `null`이며 `0원`으로 대체하지 않는다. 소수·overflow·통화 불일치·여러 Offer 충돌은 자동 확정하지 않는다. 조건부 표시가는 후보로 제시하고 사용자가 확인한 금액만 기존 `WishItem.priceWon`으로 저장한다. 이미지 주소와 수집 시각도 별도 보존한다.

`userEditedTitle`, `userEditedPrice` 또는 필드별 수정 상태를 두어 응답이 늦게 도착해도 사용자 입력을 덮어쓰지 않는다. URL을 바꾸면 이전 요청은 취소하고 requestId와 현재 URL이 일치하는 결과만 적용한다.

## 6. 전송 검증 계획과 실패 기준

### 다음 세션 초반의 제한된 조사

1. 현재 소스/기기 상태를 확인하고, 제공 링크 3개를 동일 조건으로 한 차례씩 확인한다. HTTP에는 계정 쿠키·토큰을 주입하지 않는다.
2. HTTP가 정상 HTML을 반환한 경우만 파서에 넘긴다. 응답 status·content-type·body 크기·Access Denied/챌린지 여부를 먼저 검사한다.
3. HTTP가 계속 차단되면 사용자에게 보이는 WebView 후보를 연결 기기에서 검증한다. 외부 쿠팡 앱/Custom Tab만 열리는 결과는 앱이 DOM을 가져온 성공으로 세지 않는다.
4. 상품 화면이 실제로 읽히는 경로가 확인되면 그 경로 하나를 먼저 완성한다. 모두 막히면 `Unavailable(BLOCKED)`와 수동 입력을 완성하고 자동 추출 미완료를 남긴다.

3시간 작업의 **초기 25분 안에 전송 경로 선택 또는 차단 판정**을 기록한다. 원인 불명 차단을 해결하려고 반복 요청·프록시 회전·브라우저 쿠키 복사·기기 위장에 시간을 쓰지 않는다.

초기 HTTP 정책 제안: connect timeout 5초, 전체 call timeout 15초, redirect 최대 5회, HTML 최대 2 MiB. 차단/429 응답 자동 재시도 없음. 사용자가 다시 시도할 수 있는 버튼은 제공하되 진행 중 중복 요청은 합친다. 이 수치는 제안값이며 현재 코드에 적용되지 않았다.

### WebView 후보의 구현 범위

WebView는 페이지 로드/JavaScript 사용을 지원하며, 페이지와 앱 코드 사이 연결에는 별도 보안 고려가 필요하다. 앱에는 INTERNET 권한이 필요하다. [Android WebView 가이드](https://developer.android.com/develop/ui/views/layout/webapps/webview)

- 사용자가 연 쿠팡 상품 화면에서 제품 데이터만 읽는 흐름으로 설계한다. 백그라운드 로그인·자동 구매·장바구니 조작은 기능 범위에 없다.
- JavaScript가 필요하다면 알려진 상품 페이지에서만 허용하고, `evaluateJavascript` 결과는 JSON으로 검증한다. 원본 URL이나 상품명을 JavaScript 소스 문자열에 연결하지 않는다.
- 네이티브 권한을 노출하는 범용 `addJavascriptInterface`는 만들지 않는다. 파일/content 접근, 혼합 콘텐츠, SSL 오류 무시를 허용하지 않는다.
- 추출 직전에도 현재 URL을 검사한다. 제품 필드만 반환하고 쿠키, localStorage, 계정 헤더, 전체 HTML/스크립트 상태는 읽거나 저장하지 않는다.
- `onPageFinished`만으로 상품 렌더링 완료를 단정하지 않는다. 제목/JSON-LD가 나타나는지 제한된 시간 안에 확인하고, timeout 시 수동 입력으로 돌아간다.
- Chrome의 로그인 상태를 WebView에 옮기는 경로를 만들지 않는다. WebView에서 로그인·챌린지가 필요하면 해당 사실을 표시하고 자동 추출 가능 여부를 미확인/실패로 기록한다.

## 7. 기존 앱과 연결할 지점

| 현재 파일 | 다음 작업 |
| --- | --- |
| `MainActivity.kt` | ACTION_SEND 수신 후 URL 분리. 소비된 공유 Intent가 화면 재생성 때 다시 열리는지 검증 |
| `ui/HarumanApp.kt` | 요청 상태/취소·중복 판정·draft 확정 연결. 기존 보관/결정 흐름 유지 |
| `ui/AddSheet.kt` | 쿠팡 링크 입력·불러오기·진행/실패 표시·수동 수정·가격 조건 확인 |
| `data/LocalStore.kt` | 원본/정규화 URL, 상품 ID, imageUrl, 확인 정보의 호환 저장. 마이그레이션 테스트 |
| `ui/Screens.kt`, `ui/DecisionScreens.kt` | 실제 썸네일과 fallback, 불명 가격 표시, 원문 열기 |
| 신규 `data/coupang/` | resolver, page source, parser, draft/result 계약을 UI와 분리 |

현재 `Platform.INSTAGRAM`을 삭제하면 과거 JSON 역직렬화가 실패해 항목이 조용히 사라질 수 있다. **신규 인스타 입력만 보류하고 저장된 enum 값과 기존 항목 읽기는 보존**한다. 새 쿠팡 필드는 nullable/default로 추가한다. 저장 실패가 생기면 기존 JSON을 덮어쓰지 않는다.

## 8. 검증 항목

### 자동 검증

- C1 Product fixture → 이름/이미지/72,500원 후보/옵션 ID 일치, 93,900원을 현재가로 사용하지 않음.
- C2 → itemId `26553563024` 확인, vendorItemId 미확인 유지, 18,400원 후보에 쿠폰 조건, 12,000원은 할인액으로 처리.
- C3 → 단축 URL 해석 후 ID 보존, 25,900원 후보에 쿠폰 조건. live HTTP403을 parser 성공으로 바꾸지 않음.
- 배열/`@graph`/복수 Product/깨진 JSON-LD/OG만 있음/가격 없음/품절/옵션 불일치/단위 가격·할부액 혼재.
- 차단 HTML·timeout·외부 redirect·URL userinfo/유사 host·과도한 응답 크기·잘못된 금액·통화.
- 입력 도중 늦은 응답·취소·화면 회전·같은 상품 중복·기존 인스타 JSON 호환·사용자 수정값 보존.

현재 조사 JSON은 테스트 fixture 자체가 아니다. 상품 필드만으로 최소 fixture를 만들 때 **재구성 fixture**라고 표시한다. fixture 테스트를 live 페이지 성공 증거로 보고하지 않는다.

### 실제 Android 확인

세 링크 각각 공유 → 추출 상태 확인 → 사용자 확인 → 저장 → 상세 → 앱 재실행 후 보존을 확인한다. 선택 옵션과 가격 조건이 맞는지 원문과 비교한다. 요청한 링크·결과(성공/부분/차단)·추출 출처·실기기/시간을 표로 남긴다. 차단 후에도 수동 저장 가능한지 별도 검증한다. 구매 결정은 앱 내 기록이며 실제 주문이 아니다.

## 9. 남은 불확실성

- Android WebView/기기 HTTP에서 세 페이지에 정상 접근 가능한지.
- 모바일 DOM과 이번 데스크톱 DOM의 차이.
- C2의 vendorItemId 및 옵션 변경 시 식별자 갱신 방식.
- 로그아웃/회원/쿠폰 조건별 가격 차이, 상품 삭제·품절·해외배송 페이지 형식.
- 이미지 URL의 장기 유효성, 정상 이미지 로드 실패 시 처리.
- 수집·캐시·이미지 사용의 서비스 조건과 상용 운영 적합성.

현 단계에는 API 키·파트너스 계정·별도 서버가 필수라고 확인된 근거가 없다. 판매자용 API가 임의 소비자 상품 조회를 대체한다고 가정하지 않는다. 기존 API 조사는 [API_PREREQUISITES.md](./API_PREREQUISITES.md)를 참고하되, 현재 쿠팡 파싱의 관찰 결과와 실행 순서는 이 문서를 따른다.
