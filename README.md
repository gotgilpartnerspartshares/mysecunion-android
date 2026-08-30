# MySecUnion Android

secunion.co.kr 조합원용 안드로이드 WebView 앱. 제3자(비공식) 클라이언트 — secunion.co.kr 운영사와 무관하며, 상표 사용 방지 위해 별도 브랜드명(`MySecUnion`) 사용.

## 스펙

| 항목 | 값 |
|---|---|
| 언어 | Kotlin |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| Package | `com.mysecunion.app` |
| UI | ViewBinding, WebView + SwipeRefreshLayout |
| 푸시 | Firebase Cloud Messaging (HTTP v1) |
| 원격 설정 | Firebase Remote Config |

## 주요 기능

- WebView 세션 유지, pull-to-refresh, 확대/축소
- 도메인 화이트리스트 — `allowed_hosts` 밖 URL, `tel:`/`mailto:`/기타 스킴은 외부 앱으로 전환
- 첨부파일 업로드(갤러리+카메라), 다운로드(쿠키/UA 포함 DownloadManager)
- FCM 푸시 → 채널 3종(notice/emergency/general) 분리, msg_id 중복 방지, 딥링크 이동
- Remote Config로 시작 URL/점검모드/강제·선택 업데이트 버전 제어 (앱 재배포 없이 대응)
- 스플래시 화면, 로드 실패 시 전용 오류 화면

전체 요구사항은 별도 SRS 문서 참고.

## 빌드 전 준비

1. Firebase 콘솔에서 프로젝트 생성, 패키지명 `com.mysecunion.app`으로 앱 등록
2. `google-services.json` 다운로드 → `app/` 아래 위치 (`.gitignore`에 이미 제외 처리됨, 커밋 금지)
3. Gradle wrapper 생성: `gradle wrapper` 또는 Android Studio로 열면 자동 생성

## Remote Config 키

| 키 | 타입 | 용도 |
|---|---|---|
| `base_url` | String | 시작 화면 URL |
| `allowed_hosts` | String(JSON 배열) | 인앱 탐색 허용 도메인 |
| `maintenance_mode` | Boolean | 점검 모드 전환 |
| `maintenance_message` | String | 점검 안내 문구 |
| `latest_version` | String | 최신 배포 버전 (안내용) |
| `min_supported_version` | String | 이 미만 버전 강제 업데이트 |
| `apk_url` | String | 업데이트 다운로드 링크 |

## FCM 토픽

- `notice` — 앱 최초 실행 시 자동 구독. 관리자는 Firebase 콘솔에서 이 토픽으로 발송.
- 페이로드 규격은 SRS 부록 C 참고 (`data.category`: notice/emergency/general, `data.msg_id`, `data.url`).

## 배포

- APK 직접 배포 (Play 스토어 미등록). GitHub Releases를 공식 배포 채널로 사용, 릴리스마다 SHA-256 해시 병기 권장.
- 서명 keystore는 오프라인 이중 백업 — 분실 시 업데이트 영구 불가.

## 미해결 사항

- 운영사(secunion.co.kr) 사전 서면 동의 확보 필요
- 하단 탭 구성, 앱 아이콘/브랜드 최종 확정
- iOS 지원 여부 (Phase 3 재검토)
