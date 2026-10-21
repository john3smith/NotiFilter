# NotiFilter: ONE store / Galaxy Store 준비

스토어 제출은 아직 하지 않았습니다. 기존 debug/release 경로와 별도로 `assembleStoreRelease`를 추가했습니다.
서명키·판매자·공개 방침·앱 ID 선택이 없으면 스토어 빌드는 실패합니다. GitHub에 배포된 debug APK를 스토어 제출용으로 사용하지 마세요.

## 빌드 설정

다음 환경변수는 로컬 비공개 환경에서 설정하세요. 비밀번호와 개인키를 소스·메신저·로그에 넣지 마세요.

- `STORE_APP_ID_SUFFIX`: 기존 ID 유지 시 `same`, 별도 설치 시 `.store` 등 선택한 접미사. 두 스토어의 ID/서명 전략을 먼저 확정합니다.
- `STORE_PUBLISHER_NAME`, `STORE_SUPPORT_EMAIL`, `STORE_PRIVACY_URL`: 실제 판매자명, 고객지원 메일, 공개된 HTTPS 개인정보처리방침 URL.
- `STORE_KEYSTORE`, `STORE_STORE_PASSWORD`, `STORE_KEY_ALIAS`, `STORE_KEY_PASSWORD`: 장기 보관할 정식 배포용 키. 비밀번호는 환경변수로만 전달하고 콘솔에 출력하지 마세요.
- `gradlew.bat validateStoreConfiguration assembleStoreRelease` (YTDown은 기존 빌드 도구와 arm64-v8a 런타임 준비도 필요).

기본 debug는 `storepreview` 접미사를 사용하지 않습니다. `storeRelease`만 명시적인 ID 선택을 요구하며, 선택이 없을 때의 `.storepreview`는 출시에 사용할 수 없습니다.
기존 앱과 서명이 다르면 덮어쓰기 설치가 실패합니다. 기존 ID를 유지하려면 데이터 백업/재설치 및 서명 이행 가능성을 먼저 확인하세요. 앱 ID·서명키는 스토어 최초 등록 뒤 함부로 변경하지 않습니다.
같은 ID·키의 APK를 두 스토어에 배포하면 교차 업데이트가 발생할 수 있습니다. 결제 SDK를 도입하면 스토어별 패키지/상품·서명 정책을 다시 검토해야 합니다.

## 고지 및 등록자료

- 메인화면의 `개인정보·권한 안내`는 오프라인에서도 열립니다. 로그인·권한 허용을 요구하지 않습니다.
- `privacy-policy-draft.txt` 내용을 실제 처리 방식과 판매자 정보에 맞춰 확정하고 외부에서 읽을 수 있는 HTTPS 페이지로 게시하세요. GitHub 원문 TXT 링크만으로 스토어의 방침 URL 검증을 통과한다고 가정하지 않습니다.
- 스토어 설명에는 앱 내 기능·필요 권한·제약을 정확히 쓰고 실제 UI 스크린샷, 독자 제작 아이콘/홍보 이미지를 준비합니다.
- 알림·접근성·포그라운드 서비스 등 민감 권한의 필요성과 거부/해제 방법을 심사 노트에 설명하세요.
- 현재 무료/광고/인앱결제 방식과 판매자 정보는 미확정이며 결제·광고 SDK를 새로 넣지 않았습니다.
- 기존 자체 코드의 이용허락은 변경하지 않았습니다. 공개 저장소라는 이유만으로 임의 오픈소스 라이선스를 부여하지 않았습니다.

## 공식 근거 (2026-10-21 확인)

- [원스토어 개인정보 보호·보안](https://onestore-dev.gitbook.io/dev/docs/review/one-store-review-guideline/undefined-2)
- [원스토어 상품 등록정보·APK 서명·versionCode](https://onestore-dev.gitbook.io/dev/docs/review/one-store-review-guideline/undefined-5)
- [원스토어 지식재산권](https://onestore-dev.gitbook.io/dev/docs/review/one-store-review-guideline/undefined-4)
- [Galaxy Store FAQ](https://developer.samsung.com/galaxy-store/faq.html)
- [Samsung 판매자 계정·commercial seller](https://developer.samsung.com/galaxy-store/overview.html)

최소 요구 API와 64비트 조건은 삼성 공개 FAQ와 실제 Seller Portal 공지를 함께 재확인하세요. 현재 타깃 SDK를 낮추지 않았습니다. 심사 승인과 Samsung 실제 기기 동작은 아직 검증되지 않았습니다.
