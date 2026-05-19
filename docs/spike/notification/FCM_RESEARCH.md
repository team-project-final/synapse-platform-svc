# Firebase Cloud Messaging (FCM) 기술 조사 보고서

## 1. 개요
본 문서는 Step 5 샘플링(FCM 디바이스 토큰 등록) 및 향후 알림 기능 구현을 위해 Firebase Cloud Messaging(FCM)의 동작 원리 및 관리 최선의 사례(Best Practices)를 정리합니다.

---

## 2. FCM 아키텍처 및 구성 요소

FCM은 메시지를 생성하여 클라이언트 앱으로 전달하기 위해 다음과 같은 4가지 주요 엔티티로 구성됩니다.

| 구성 요소 | 역할 |
| :--- | :--- |
| **Message Sender** | 메시지를 생성하는 주체. (애플리케이션 서버 또는 Firebase Console) |
| **FCM Backend** | 메시지 요청을 수신하여 큐에 쌓고, 대상 장치로 라우팅하는 Google 인프라. |
| **Transport Layer** | 플랫폼별 메시지 전달 서비스 (Android: Google Play Services, iOS: APNs, Web: Web Push). |
| **Client App** | FCM SDK가 통합된 실제 사용자 장치의 애플리케이션. |

---

## 3. 동작 원리 (Lifecycle)

### A. 등록 (Registration)
1. 클라이언트 앱은 FCM SDK를 통해 **Registration Token**을 요청합니다.
2. FCM SDK는 해당 장치/앱 인스턴스를 식별하는 고유 토큰을 반환합니다.
3. 클라이언트 앱은 이 토큰을 **애플리케이션 서버**로 전송하고, 서버는 이를 DB에 저장합니다. (Step 5의 주요 작업)

### B. 메시지 전송 (Downstream)
1. 서버는 FCM v1 API를 사용하여 HTTP POST 요청을 FCM Backend로 보냅니다. (Payload + 대상 토큰 포함)
2. FCM Backend는 요청을 검증하고 라우팅합니다.
3. 대상 장치의 플랫폼에 맞는 Transport Layer를 거쳐 메시지가 전달됩니다.
4. 장치가 오프라인인 경우, FCM은 일정 기간(기본 4주) 메시지를 보관했다가 재연결 시 전달합니다.

### C. 메시지 유형
- **Notification Messages**: OS가 직접 처리. 앱이 백그라운드일 때 시스템 트레이에 표시.
- **Data Messages**: 클라이언트 앱이 처리. 백그라운드에서도 앱 로직(Silent 업데이트 등)을 실행 가능.

---

## 4. 토큰 관리 최선의 사례 (Best Practices)

효율적인 알림 전송과 리소스 절약을 위해 서버측 토큰 관리는 매우 중요합니다.

### A. 타임스탬프 기반 저장
- 토큰만 저장하지 말고 `last_updated` 타임스탬프를 함께 기록합니다.
- 클라이언트 앱이 구동될 때마다 서버로 토큰을 보내 `last_updated`를 갱신하도록 설계합니다.

### B. 비활성 토큰 정리 (Proactive Cleanup)
- FCM은 약 한 달 이상 연결되지 않은 토큰을 "Stale(부패)"한 것으로 간주합니다.
- **권장 사항**: 60일(2개월) 이상 갱신되지 않은 토큰은 DB에서 정기적으로 삭제(Cleanup Job)합니다.

### C. 전송 실패 시 즉시 처리 (Reactive Cleanup)
- 메시지 전송 시 FCM Backend로부터 다음과 같은 에러 코드를 받으면 즉시 DB에서 해당 토큰을 삭제해야 합니다.
    - `UNREGISTERED` (HTTP 404): 앱이 삭제되었거나 토큰이 만료됨.
    - `INVALID_ARGUMENT` (HTTP 400): 토큰 형식이 잘못됨.

### D. 클라이언트 자가 치유 (Self-Healing)
- 앱에서 `onNewToken` 리스너를 통해 토큰 변경 시 즉시 서버에 반영합니다.
- 토큰이 변경되지 않았더라도 최소 한 달에 한 번은 서버로 토큰을 전송하여 "하트비트" 역할을 수행하게 합니다.

---

## 5. 참고 문서
- [Firebase 공식 문서: FCM 아키텍처 개요](https://firebase.google.com/docs/cloud-messaging/fcm-architecture)
- [Firebase 블로그: FCM 등록 토큰 관리 가이드](https://firebase.blog/posts/2024/07/fcm-registration-token-management/)
- [RFC 8030: Generic Event Delivery Using HTTP Push](https://tools.ietf.org/html/rfc8030)
