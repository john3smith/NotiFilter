# NotiFilter

## 1.0.8 · 규칙 평가 모듈 분리

- AND/OR 문구 파싱을 `RuleExpression`으로 분리하고 최대 128개 식을 캐시합니다.
- 알림별로 규칙 저장소를 다시 만들거나 같은 JSON을 다시 해석하지 않습니다.
- 저장된 규칙 문자열이 바뀌면 즉시 다시 읽습니다. 반환 목록은 복사본이며 잘못된 규칙은 차단에 사용하지 않습니다.
- 기존 이력 암호화와 앱별/전체 앱 규칙 의미를 유지합니다.

## HTML 사용 설명서

[기능 소개 · 설치 · 사용법 · 문제 해결 (HTML)](docs/user-guide.html)

HTML 파일을 다운로드한 뒤 브라우저에서 열어 주세요. GitHub 파일 화면에서는 소스가 보일 수 있습니다.
설명서 기준 버전과 설치 APK/EXE·Release 버전은 다를 수 있으므로 문서의 버전 안내를 확인하세요.

[사용 설명서](USER_GUIDE.md) · [APK 다운로드](https://github.com/john3smith/NotiFilter/releases)

Android 8.0 이상에서 앱별 포함 문구에 일치하는 알림을 게시 직후 자동으로 제거하는 로컬 앱입니다.

## 사용법

1. APK를 설치하고 앱을 엽니다.
2. **알림 접근 설정 열기**에서 NotiFilter의 알림 접근을 허용합니다.
3. **규칙 추가**에서 아이콘과 검색을 이용해 대상 앱(또는 모든 앱)을 고르고 포함 문구를 지정합니다.
4. 규칙별 스위치로 켜고 끄거나 전체 필터를 일시정지할 수 있습니다.
5. 기존 규칙 카드를 터치하면 대상 앱과 문구를 수정할 수 있습니다. 저장은 같은 규칙을 갱신하며 순서와 활성 상태를 유지합니다. 취소나 뒤로가기는 변경을 저장하지 않습니다.

새 규칙 저장, 활성 규칙 수정 저장, 규칙 토글을 다시 켜는 순간, 전체 필터 일시정지를 해제할 때 현재 알림도 검사합니다. 끄는 순간에는 차단을 수행하지 않습니다. 알림 접근이 없거나 전체 일시정지 중이면 지우지 않습니다. 앱 시작·리스너 재연결만으로 기존 알림을 지우지는 않습니다.

메인의 **삭제한 알림 이력**에서 삭제 확인 시간, 제목·본문, 앱 이름·패키지, 앱 아이콘을 최신순으로 확인합니다. 본문은 생략하지 않습니다. 이력 화면의 새로고침으로 최신 삭제 결과를 확인하세요. 요청을 보낸 알림 중 시스템의 `onNotificationRemoved`와 `REASON_LISTENER_CANCEL`을 확인한 경우만 기록하므로 삭제 실패·사용자 수동 삭제는 이력에 넣지 않습니다. 기기 내부 SQLite에 최근 1,000개를 저장하며 오래된 항목은 자동 정리됩니다. 앱 아이콘을 함께 저장하여 앱 제거 후에도 이력 아이콘이 유지됩니다(아이콘 조회가 불가능하면 기본 아이콘). 앱 업데이트·재실행 시 이력은 유지되지만 이 기능 도입 이전의 삭제 내역은 복원할 수 없습니다.

규칙과 이력은 기기 내부에만 저장하며 인터넷 권한이 없고, 클라우드 백업·기기 전송에서 제외합니다. 이력에는 알림의 개인정보가 포함될 수 있으니 기기 잠금을 사용하세요. 시스템이 알림을 게시한 다음 리스너가 지우므로 알림 표시나 소리가 아주 잠깐 발생할 수 있습니다. 일부 시스템·지속 알림은 제거할 수 없으며 연결 중단 직전의 삭제 확인은 이력에서 누락될 수 있습니다.

버전: 1.0.5 (versionCode 6). 외부 라이브러리 추가 없이 표준 Android API 사용.

## 문구 조건과 우선순위

규칙 추가 창의 **?** 아이콘에서 설명과 예시를 확인할 수 있습니다.

- `,`는 AND: `택배,배송`은 같은 알림에 두 문구가 모두 있어야 일치합니다.
- `;`는 OR: `광고;이벤트`는 둘 중 하나 이상 포함되면 일치합니다.
- **AND가 OR보다 우선**합니다. `택배,배송;배달,완료`는 `(택배 AND 배송) OR (배달 AND 완료)`입니다.
- `광고;결제,승인`은 `광고 OR (결제 AND 승인)`입니다. 단어 순서는 무관합니다.
- 각 문구 앞뒤 공백과 영문 대소문자는 무시하지만, 문구 내부의 띄어쓰기는 유지합니다.
- 빈 조건(`광고,,이벤트`, `광고;` 등)은 저장하지 못하며, 기존에 저장된 잘못된 조건도 알림을 차단하지 않습니다.
- 반각 `,`와 `;`만 구분자입니다. 괄호/정규식/이스케이프/구분자 자체 검색은 지원하지 않습니다.
- 기존 구분자 없는 규칙은 동일하게 작동합니다. 기존 쉼표/세미콜론 포함 규칙은 이제 조건식으로 해석되므로 확인해 주세요.
- 대상 앱, 개별 규칙 토글, 전체 일시정지 및 기존 알림 검사에도 같은 조건이 적용됩니다.

규칙 추가와 대상 앱 선택 팝업은 연속으로 눌러도 중복으로 열리지 않습니다. 팝업 바깥을 눌러서는 닫히지 않으며, 취소 또는 Android 뒤로가기로 닫을 수 있습니다. 저장 후에는 다시 추가할 수 있습니다.

## 개발 검증

`gradlew.bat testDebugUnitTest lintDebug assembleDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`


## 1.0.7 security update

- Notification history bodies use AES-GCM with an Android Keystore key. Existing version-1 rows migrate atomically without changing their displayed content, timestamps, packages or row IDs. Encryption failure does not fall back to plaintext.
- Release APKs are non-debuggable; backup remains disabled. App names, package names, timestamps and icons remain local database metadata. History keeps its existing 1,000-row limit.
- The release currently keeps the existing debug signing certificate for data-preserving updates. Non-debuggable does not mean a new production signing identity; a future signing-key migration needs a compatible rotation plan.
- Migration uses SQLite secure_delete. This does not promise forensic erasure of old OS backups, filesystem snapshots, or externally copied databases.
- Verified on the regular API 35 emulator: existing 13 histories retained, metadata unchanged, displayed text identical, all old plaintext bodies absent from migrated DB; a newly filtered synthetic notification was also stored as ciphertext.
