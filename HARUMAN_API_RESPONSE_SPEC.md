# 하루만 상품 API 반환 명세와 예시

앱 연동용으로 합의한 응답 구조. 서버 구현 전 계약 문서이며, 모든 응답은 동일한 최상위 키를 사용한다.

## 요청

`POST /v1/products/extract`

```json
{
  "url": "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892"
}
```

## 반환 필드

| 필드 | 형식 | 의미 |
|---|---|---|
| status | string | success / partial / error |
| code | string 또는 null | 오류 코드. success와 partial에서는 null |
| message | string 또는 null | 사용자에게 표시할 안내 문구 |
| source | string 또는 null | 쇼핑몰 식별값. 예: coupang |
| original_url | string | 앱에서 전달한 원본 URL |
| product_url | string 또는 null | 확인한 상품 URL. 옵션 식별자 보존 |
| product | object 또는 null | 상품 정보. 실패 시 null |
| product.id | string 또는 null | 판매처의 상품 ID |
| product.item_id | string 또는 null | 옵션 식별자. 해당하지 않으면 null |
| product.vendor_item_id | string 또는 null | 판매 옵션 식별자. 해당하지 않으면 null |
| product.name | string 또는 null | 상품명 |
| product.price | integer 또는 null | 원 단위 금액. 미상은 0이 아닌 null |
| product.currency | string 또는 null | 확인한 통화. 한국 원화는 KRW |
| product.price_type | string 또는 null | displayed / coupon / member / unknown |
| product.price_note | string 또는 null | 쿠폰·회원 등 가격 조건 |
| product.image_url | string 또는 null | 대표 사진 주소. 이미지 바이너리는 포함하지 않음 |
| product.option_label | string 또는 null | 옵션 설명. 예: 블랙 / 1개 |
| fetched_at | string 또는 null | ISO 8601 조회 시각. 시간대 포함 |

- 정의한 키는 생략하지 않고 값이 없으면 null을 반환한다.
- success는 상품명·가격·대표 사진을 확인한 상태다.
- partial은 일부만 확인한 상태다. product 객체는 유지하고 미확인 필드는 null로 반환한다.
- error는 추출 실패 상태다. product는 null이고 code와 message로 원인을 전달한다.
- 앱 분기는 status와 code를 기준으로 한다. message의 문구를 비교해 분기하지 않는다.
- displayed는 관찰한 화면의 표시 가격이며 결제 확정 금액을 의미하지 않는다.
- 원본 URL과 옵션 식별자를 보존하며 다른 판매자·반품 최저가·적립금을 선택 상품 가격에 섞지 않는다.

## 1. 성공 예시

아래 상품명·가격·이미지 주소는 2026-09-29 브라우저에서 확인한 G102 기준이다. fetched_at은 형식 설명을 위한 예시 시각이다. 실제 API 또는 Qwen 실행 결과는 아니다.

```json
{
  "status": "success",
  "code": null,
  "message": null,
  "source": "coupang",
  "original_url": "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892",
  "product_url": "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892",
  "product": {
    "id": "6011227725",
    "item_id": "19848126302",
    "vendor_item_id": "70778258892",
    "name": "로지텍 G102IC 2세대 LIGHTSYNC 게이밍 유선마우스",
    "price": 24010,
    "currency": "KRW",
    "price_type": "displayed",
    "price_note": null,
    "image_url": "https://thumbnail.coupangcdn.com/thumbnails/remote/640x640ex/image/1025_amir_coupang_oct_80k/243b/566c5e1c6edc04f8a07a96374c7d03275399cb239fbdc3201a3677c73562.jpg",
    "option_label": null
  },
  "fetched_at": "2026-09-29T11:40:00+09:00"
}
```

앱 표시: 상품 사진과 상품명을 보여주고 가격을 `24,010원`으로 표시한다.

## 2. 부분 성공 예시

같은 상품에서 가격만 읽지 못했다고 가정한 처리 예시다. 실제로 이 상품의 가격 추출이 실패했다는 의미는 아니다.

```json
{
  "status": "partial",
  "code": null,
  "message": "가격을 확인하지 못했어요. 직접 입력해주세요.",
  "source": "coupang",
  "original_url": "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892",
  "product_url": "https://www.coupang.com/vp/products/6011227725?itemId=19848126302&vendorItemId=70778258892",
  "product": {
    "id": "6011227725",
    "item_id": "19848126302",
    "vendor_item_id": "70778258892",
    "name": "로지텍 G102IC 2세대 LIGHTSYNC 게이밍 유선마우스",
    "price": null,
    "currency": "KRW",
    "price_type": null,
    "price_note": null,
    "image_url": "https://thumbnail.coupangcdn.com/thumbnails/remote/640x640ex/image/1025_amir_coupang_oct_80k/243b/566c5e1c6edc04f8a07a96374c7d03275399cb239fbdc3201a3677c73562.jpg",
    "option_label": null
  },
  "fetched_at": "2026-09-29T11:40:00+09:00"
}
```

앱 표시: 상품명과 사진은 채우고 가격 입력란은 비워둔다. message로 직접 입력을 안내한다. null을 0원으로 표시하지 않는다.

## 3. 실패 예시

브랜드관·검색 결과처럼 상품 하나를 특정할 수 없는 링크에는 상품 상세 링크를 요청한다.

```json
{
  "status": "error",
  "code": "product_detail_required",
  "message": "상품 상세 페이지 링크를 보내주세요.",
  "source": "coupang",
  "original_url": "https://shop.coupang.com/A00114131/285427",
  "product_url": null,
  "product": null,
  "fetched_at": null
}
```

앱 표시: message를 보여주고 상세 상품 링크를 다시 입력받는다.

## 오류 코드와 HTTP 상태

| HTTP | code | 의미 |
|---|---|---|
| 200 | null | success 또는 partial |
| 422 | invalid_url | 잘못된 상품 URL |
| 422 | unsupported_site | 지원하지 않는 쇼핑몰 |
| 422 | product_detail_required | 상품 상세 페이지 링크 필요 |
| 502 | blocked | 쇼핑몰에서 접근 차단 |
| 502 | login_required | 정보를 보려면 쇼핑몰 로그인 필요 |
| 429 | busy | 다른 요청 처리 중. 재시도 필요 |
| 504 | timeout | 제한 시간 안에 결과 확보 실패 |
| 503 | model_unavailable | 모델이 필요한 요청에서 모델 사용 불가 |
| 502 | extraction_failed | 그 밖의 상품 정보 추출 실패 |

로그인 필요는 쇼핑몰 페이지의 상태이며, 호출한 앱의 API 인증 실패와는 구분한다.
