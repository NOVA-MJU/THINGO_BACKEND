# FCM 푸시 실제 수신 테스트

백엔드 → Google FCM 발송은 이미 검증됨(dryRun OK, project thingo-8eee1).
여기서는 실제로 **브라우저에 푸시가 뜨는 것**을 눈으로 확인한다.

## 1. Firebase 콘솔에서 값 2개 받기
https://console.firebase.google.com → 프로젝트 `thingo-8eee1`

1. ⚙️ 프로젝트 설정 → **일반** → "내 앱" → 웹 앱(`</>`)이 없으면 추가 →
   `firebaseConfig` 객체 복사 → `index.html` 과 `firebase-messaging-sw.js` 의 동일 자리에 붙여넣기.
2. ⚙️ 프로젝트 설정 → **클라우드 메시징** → "웹 푸시 인증서(Web Push certificates)" →
   키 쌍 생성/복사 → `index.html` 의 `VAPID_KEY` 에 붙여넣기.

## 2. 로컬 서버로 페이지 열기
서비스워커는 `file://` 에서 안 되고 `http://localhost` 가 필요하다.
```
cd docs/fcm-test
python -m http.server 8080        # 또는: npx http-server -p 8080
```
브라우저에서 http://localhost:8080 열기 → **"알림 허용 + 토큰 발급"** 클릭 → 표시된 토큰 복사.

## 3. 백엔드에서 그 토큰으로 실제 발송
프로젝트 루트에서:
```powershell
# PowerShell
$env:FCM_TOKEN="2번에서 복사한 토큰"
./gradlew test --tests "*FcmManualSendIT"
```
```bash
# bash
FCM_TOKEN="복사한 토큰" ./gradlew test --tests "*FcmManualSendIT"
```
성공하면:
- 콘솔에 `[FCM-MANUAL] sent id=projects/thingo-8eee1/messages/...`
- 브라우저 페이지(또는 백그라운드 알림)에 **"'학식' 키워드 새 소식"** 푸시가 뜬다.

## 더 간단히 (코드 없이)
토큰만 있으면 Firebase 콘솔 → Messaging → "첫 캠페인/테스트 메시지" → "테스트 메시지 전송"
에 토큰을 붙여 바로 쏠 수도 있다.

## 전체 앱 파이프라인까지 보려면
실제 앱(또는 위 웹페이지)이 그 토큰을 `POST /api/v1/device-tokens` 로 등록하면,
공지/학식이 올라올 때 서버가 자동으로 이 흐름을 타고 푸시를 보낸다. 데모는 위 수동 발송으로 충분.
```
