# 랄프톤 API 사전 조사

> **최신 v3:** 실제 연동 대상은 추후 사용자가 문서를 제공할 **링크 기반 상품 설명 API**다. 스크린샷/이미지 전송은 제외했고 인스타그램 보류, 유튜브 제외는 유지한다. 사양 미정 항목은 [LINK_API_DESIGN.md](./LINK_API_DESIGN.md)에 정리했다. 아래 API 조사는 과거 참고 자료이며 키 발급·서버 설정의 실행 지시가 아니다. 현재 범위는 [GOAL_SPEC.md](./GOAL_SPEC.md)와 [todo.md](./todo.md)를 따른다.

> 초기 플랫폼 API 조사와 [쿠팡 페이지 분석](./COUPANG_PARSING_TECHNICAL.md)은 이력으로 보존한다. 현재 APK 상태는 [CHECKPOINT.md](./CHECKPOINT.md)를 참고한다.

> 조사일: 2026-09-29  
> 대상: [제품 브리프 v4](<./ralphthon-product-brief-b58jYyjbQYHVtRNKNPE6hpmB-20260926-v4 (1).md>)의 쿠팡·유튜브·인스타그램 공유 수집  
> 상태: 공식 문서 확인 완료. 팀 계정 생성, API 키 발급, 실제 공유 링크에 대한 호출 테스트는 아직 수행하지 않음.

## 먼저 결론

| 출처 | 지금 준비할 자격 증명 | 공식적으로 확보 가능한 것 | 제품에 적용할 방식 |
| --- | --- | --- | --- |
| YouTube | Google Cloud 프로젝트의 YouTube Data API v3 **API 키** | 공개 영상의 제목·설명·썸네일 | 서버에서 `videos.list(part=snippet)` 호출. 구매 대상 상품명·가격은 사용자 확인 |
| Instagram | 공개 게시물 **임베드만** 쓸 경우 현재 토큰·앱 심사 불필요 | oEmbed의 임베드 HTML과 제공자 정보 | 랄프톤 저장 데이터는 공유 URL과 사용자가 입력한 상품 정보 중심. oEmbed 응답을 상품 데이터로 추출·저장하지 않음 |
| Coupang | 일반 소비자 상품 조회에 쓸 공식 키는 확인되지 않음. 판매자 Open API는 Wing 판매자 키 필요 | 판매자 본인이 등록한 상품 정보 조회 | 일반 소비자 공유 링크는 우선 URL 수신. 제목·이미지·가격 자동 추출은 실물 링크와 허용 범위 검증 후 결정 |
| Android 공유 | 키 불필요 | 각 앱이 `ACTION_SEND`로 보낸 텍스트/URL | 수신 후 URL 식별. 실제 전달 형식은 앱별 실기기 테스트 필요 |

위 표의 쿠팡 결론은 **확인한 공개 공식 문서 범위에서의 판단**이다. 쿠팡의 모든 계약형·제휴형 접근 경로가 존재하지 않는다는 뜻은 아니다.

## 1. YouTube Data API v3

### 준비물과 신청 순서

1. 팀 소유 Google 계정으로 Google Cloud 프로젝트를 만든다.
2. 프로젝트에서 **YouTube Data API v3**를 사용 설정한다.
3. 일반 공개 영상 정보 조회용 API 키를 발급하고 API 사용 대상을 YouTube Data API로 제한한다. 서버 IP가 고정된 배포 환경이라면 호출 출처 제한도 설정한다.
4. 키는 서버 환경 변수 `YOUTUBE_API_KEY`에 보관한다. APK와 저장소에는 넣지 않는다.
5. 팀의 실제 공개 영상 링크로 요청 1건을 시험하고 프로젝트의 할당량 화면을 확인한다.

Google은 공개 API 접근에 API 키를 사용하며, 키의 제한 설정과 클라이언트 코드·저장소에 키를 포함하지 않는 방식을 권장한다. [프로젝트·인증 설정](https://developers.google.com/youtube/registering_an_application), [API 키 보관 지침](https://docs.cloud.google.com/docs/authentication/api-keys-best-practices)

### 호출과 데이터

`GET https://www.googleapis.com/youtube/v3/videos?part=snippet&id=VIDEO_ID`

`snippet`에 `title`, `description`, `thumbnails` 등이 들어 있다. 이 호출의 비용은 **1 quota unit**이다. 기본 할당량은 현재 프로젝트당 하루 **10,000 units**로 안내되며 변경 가능하므로 콘솔의 실제 값을 확인한다. [videos.list](https://developers.google.com/youtube/v3/docs/videos/list), [할당량 설명](https://developers.google.com/youtube/v3/getting-started)

- 앱이 공유한 `youtube.com/watch`, `youtu.be`, `youtube.com/shorts` 등의 URL에서 영상 ID를 식별한다. 지원 URL은 실측 결과로 확정한다.
- `items`가 비어 있으면 비공개·삭제·잘못된 ID 가능성을 포함해 `link_only`로 처리한다.
- 영상 정보에는 **그 영상에서 사용자가 사고 싶어진 상품명·가격**이 없다. 저장 전 사용자 확인 화면이 필요하다.
- 공식 `captions.download`는 해당 영상을 **수정할 권한이 있는 사용자**를 요구하고 호출 비용도 200 units이다. 임의의 공개 영상 스크립트 자동 수집을 MVP 요구사항으로 잡지 않는다. [자막 다운로드 조건](https://developers.google.com/youtube/v3/docs/captions/download)
- API에서 가져온 제목·설명·썸네일 등 비인증 데이터는 30일이 지나면 삭제하거나 새로 조회하는 정책을 설계한다. 사용자가 입력한 구매 대상 정보와 원본 URL은 별도 필드로 관리한다. [YouTube 개발자 정책 III.E.4](https://developers.google.com/youtube/terms/developer-policies)

## 2. Instagram oEmbed

Meta 공식 문서에 따르면 **2026-06-15부터 공개 Meta 콘텐츠 임베드에는 액세스 토큰과 App Review가 필요하지 않다.** 토큰 없는 oEmbed 요청 한도는 엔드포인트당 시간당 **1,000건**이다. [Meta oEmbed 현재 조건](https://developers.facebook.com/docs/graph-api/reference/oembed-post/)

`GET https://graph.facebook.com/v26.0/instagram_oembed?url=POST_URL`

공식 예시 응답은 임베드용 `html`, `provider_name`, `provider_url`, `type`, `width`, `version`을 포함한다. `thumbnail_url`과 작성자 관련 필드는 2025년 변경으로 더 이상 제공되지 않는다. 캡션이나 상품 가격을 구조화된 필드로 기대할 수 없다. [Instagram oEmbed 문서](https://developers.facebook.com/documentation/instagram-platform/oembed), [Meta oEmbed 필드 변경](https://developers.facebook.com/docs/graph-api/reference/oembed-post/)

**용도 제한이 제품 설계에 직접 영향을 준다.** Meta는 oEmbed를 화면에서 Instagram 콘텐츠를 임베드하는 용도로 제한하고, 그 응답의 메타데이터·콘텐츠를 다른 목적으로 추출·가공·저장하는 것을 금지한다고 명시한다. 따라서 oEmbed의 HTML에서 캡션을 파싱하거나 임베드 데이터를 랄프톤 상품 레코드에 저장하는 구현은 채택하지 않는다. 공개 링크라도 비공개·비활성·연령 제한 게시물, 임베드를 끈 계정, 스토리는 지원되지 않는다. [공식 제한 사항](https://developers.facebook.com/documentation/instagram-platform/oembed)

MVP에서 안전하게 확정할 수 있는 흐름은 **Instagram 공유 URL 수신 → 사용자가 구매 대상 상품명·가격 입력 → 랄프톤 보관함 저장**이다. 게시물 자체를 앱에서 보여 줄 필요가 있다면 oEmbed를 화면 렌더링에만 사용할지 별도 결정한다. 앱이 공유 텍스트에 캡션을 동봉하는지는 실기기에서 확인해야 한다. Instagram 웹페이지의 다른 메타데이터를 자동 저장하는 방식은 이 조사에서 허용 조건을 확정하지 못했다.

별도의 [Business Discovery API](https://developers.facebook.com/documentation/instagram-platform/instagram-api-with-facebook-login/business-discovery)는 앱 사용자의 Instagram 프로페셔널 계정을 통한 로그인과 대상 **프로페셔널 계정**의 사용자 이름을 전제로 한다. 임의의 개인 계정 게시물 URL을 공유받아 캡션·이미지를 가져오는 범용 경로로 채택하지 않는다.

## 3. Coupang Open API와 소비자 상품 링크

쿠팡의 공개 개발자 센터는 **판매자·연동사**의 상품 등록, 재고, 가격, 주문 관리를 설명한다. 사용하려면 Wing 판매자 계정에서 Access Key·Secret Key를 발급하고 HMAC 서명 등을 준비한다. 상품 조회 예시는 `sellerProductId`로 등록 상품을 조회하며, 문서의 오류 예시에는 다른 판매자의 상품 조회 거부도 있다. 그러므로 이 판매자 API를 임의의 소비자 공유 링크에서 상품명·현재 가격을 가져오는 용도로 설계하면 안 된다. [Open API 시작 안내](https://developers.coupang.com/en/getting-started/coupang-open-api), [상품 조회 API](https://developers.coupang.com/en/api/products/querying-product)

쿠팡 파트너스는 별도의 제휴 포털이다. 이번 조사에서는 일반 소비자 상품 링크를 조회할 수 있는 **공개 공식 API 사양과 발급 조건을 확인하지 못했다**. 파트너스 가입이나 판매자 키 발급을 MVP의 선행 필수 작업으로 두지 않는다. [쿠팡 파트너스 공식 포털](https://partners.coupang.com/)

쿠팡 앱에서 받은 실제 링크로 아래 사항을 먼저 확인한다.

1. 공유 텍스트에 URL 외에 상품명이 포함되는가?
2. 단축 링크가 최종 쿠팡 상품 URL로 정상 이동하는가?
3. 공개 페이지에서 제목·이미지·가격 후보가 접근 가능한가? 로그인·옵션·회원 가격에 따라 값이 달라지는가?
4. 자동 수집 방식이 당시의 플랫폼 정책과 허용 범위에 맞는가?

이 결과가 나오기 전 확정 가능한 기능은 **링크 수신과 사용자의 상품명·가격 확인/입력**이다. 가격을 가져오더라도 수집 시각과 사용자가 확인한 금액을 구분해 저장한다.

## 4. 앱 공유 수신

Android 앱은 매니페스트에 `ACTION_SEND` + `text/plain` 수신을 선언하고 공유된 `Intent.EXTRA_TEXT`를 읽는다. Android 공식 문서도 수신 내용을 사용자가 확인·수정할 수 있게 만드는 흐름을 권장한다. [Android 공유 수신 가이드](https://developer.android.com/develop/ui/compose/sharing/receive)

세 플랫폼의 앱 버전과 공유 위치에 따라 실제 전달 텍스트가 다를 수 있으므로 문서 예시 대신 **실기기 캡처 결과**로 URL 파서를 확정한다. 테스트 입력은 정상 URL, 단축 URL, URL 앞뒤 설명 텍스트, 중복 공유, 지원하지 않는 링크를 포함한다. 공유 텍스트에 포함될 수 있는 사용자 정보를 로그에 그대로 남기지 않는다.

## 5. 구현 전 준비 목록과 판단 기준

| 준비물 | 담당자가 확보할 것 | 완료 판정 |
| --- | --- | --- |
| Google Cloud | 프로젝트 소유자, YouTube Data API 사용 설정, 제한된 서버용 키 | 팀 공개 영상 1개에서 제목·설명·썸네일 수신 |
| Android 실기기 | 시연 기기와 쿠팡·유튜브·인스타그램 앱 | 각 앱에서 공유한 원문과 링크 형태 확인 |
| 테스트 링크 | 플랫폼별 공개 링크 5개 이상과 실패 사례 | 링크 형식·추출 필드·실패 사유 기록 |
| HTTPS 서버 | 시연 기기에서 접근할 API 주소, 서버 측 키 저장 방법 | 모바일 데이터 또는 시연 네트워크에서 미리보기 호출 성공 |
| 제품 문구 | 자동 정보 수집 범위 재확인 | 실제 가능한 자동 입력과 사용자 입력을 구분해 시연 |

**브리프와의 차이:** 브리프는 쿠팡 가격, 유튜브 스크립트, 인스타그램 썸네일·캡션 자동 추출을 포함한다. 현재 공식 API 자료만으로는 이 세 항목을 범용적으로 보장할 수 없다. 팀은 링크 실측 후 구현 가능 범위를 확정하고, 시연에서는 자동 추출 필드와 사용자가 직접 확인·입력한 필드를 분명히 표시해야 한다.

이 문서는 공식 문서를 확인한 사전 조사다. 계정 로그인, 키 발급, 실제 API 인증 호출, 실제 상품·게시물 링크 테스트, 정책에 대한 법률 검토는 수행하지 않았다.
