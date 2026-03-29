# Kinover 백엔드 아키텍처

## 1. 시스템 아키텍처 (전체)

```mermaid
flowchart TB
    subgraph Client["클라이언트 (React Native)"]
        App[Kinover App]
    end

    subgraph Internet["인터넷"]
        Nginx[Nginx<br/>HTTPS · kinover.shop]
    end

    subgraph EC2["Amazon EC2 (Ubuntu)"]
        Spring[Spring Boot App<br/>:9090]
        Redis[(Redis<br/>Pub/Sub · 6379)]
        Maria[(MariaDB)]
    end

    subgraph External["외부 서비스"]
        S3[AWS S3<br/>Presigned URL]
        FCM[Firebase FCM<br/>푸시 알림]
        Kakao[Kakao API<br/>로그인]
        Apple[Apple Sign In<br/>JWT 검증]
        OpenAI[OpenAI API<br/>키노 챗봇]
    end

    App -->|HTTPS| Nginx
    Nginx -->|Reverse Proxy| Spring
    Spring --> Redis
    Spring --> Maria
    Spring --> S3
    Spring --> FCM
    Spring --> Kakao
    Spring --> Apple
    Spring --> OpenAI
```

---

## 2. 애플리케이션 레이어 (Spring Boot 내부)

```mermaid
flowchart LR
    subgraph Entry["진입점"]
        REST["REST API<br/>/api/*"]
        WS["WebSocket<br/>/chat, /status, /family-status"]
    end

    subgraph Security["보안"]
        JWT[JwtAuthenticationFilter]
        Security[SecurityConfig<br/>CORS · Stateless]
    end

    subgraph Controller["Controller Layer"]
        LoginCtrl[LoginController]
        UserCtrl[UserController]
        FamilyCtrl[FamilyController]
        PostCtrl[PostController]
        ChatCtrl[ChatRoomController]
        ScheduleCtrl[ScheduleController]
        ImageCtrl[ImageController]
        CategoryCtrl[CategoryController]
        FcmCtrl[FcmTokenController]
        Others[Comment, Challenge, ...]
    end

    subgraph Service["Service Layer"]
        Auth[Kakao/Apple UserService]
        UserSvc[UserService]
        FamilySvc[FamilyService]
        PostSvc[PostService]
        ChatSvc[ChatRoomService]
        MessageSvc[MessageService]
        ScheduleSvc[ScheduleService]
        S3Svc[S3Service]
        FcmSvc[FcmNotificationService]
        OpenAiSvc[OpenAiService]
    end

    subgraph Data["Data Layer"]
        Repos[(JPA Repositories)]
    end

    REST --> JWT
    WS --> JWT
    JWT --> Security
    Security --> LoginCtrl
    Security --> UserCtrl
    Security --> FamilyCtrl
    Security --> PostCtrl
    Security --> ChatCtrl
    Security --> ScheduleCtrl
    Security --> ImageCtrl
    Security --> CategoryCtrl
    Security --> FcmCtrl
    Security --> Others

    LoginCtrl --> Auth
    UserCtrl --> UserSvc
    FamilyCtrl --> FamilySvc
    PostCtrl --> PostSvc
    ChatCtrl --> ChatSvc
    ImageCtrl --> S3Svc
    FcmCtrl --> FcmSvc

    UserSvc --> Repos
    FamilySvc --> Repos
    PostSvc --> Repos
    ChatSvc --> Repos
    MessageSvc --> Repos
    ScheduleSvc --> Repos
    Auth --> Repos
```

---

## 3. 실시간 채팅 & 접속 상태 (WebSocket + Redis)

```mermaid
sequenceDiagram
    participant C1 as 클라이언트 A
    participant WS as WebSocket<br/>/chat, /status
    participant Redis as Redis Pub/Sub
    participant Sub as ChatMessageSubscriber<br/>UserStatusSubscriber
    participant C2 as 클라이언트 B

    Note over C1,C2: 채팅 메시지
    C1->>WS: 메시지 전송 (텍스트/이미지 등)
    WS->>Redis: PUBLISH chat:messages
    Redis->>Sub: onMessage
    Sub->>WS: 같은 채팅방 참가자 세션 조회
    WS->>C2: TextMessage 전송
    Sub->>FCM: (오프라인 사용자) 푸시 알림

    Note over C1,C2: 접속 상태 (online/offline)
    C1->>WS: /status 연결 (JWT)
    WS->>Redis: PUBLISH family:status:{familyId}
    Redis->>Sub: UserStatusSubscriber
    Sub->>WS: 해당 가족 세션들 조회
    WS->>C2: 접속 상태 이벤트 브로드캐스트
```

```mermaid
flowchart LR
    subgraph Clients["클라이언트"]
        A[App A]
        B[App B]
    end

    subgraph Spring["Spring Boot"]
        WSChat[WebSocketMessageHandler<br/>/chat]
        WSStatus[WebSocketStatusHandler<br/>/status]
        WSFamily[WebSocketFamilyStatusHandler<br/>/family-status]
        Pub[StringRedisTemplate<br/>publish]
    end

    subgraph Redis["Redis"]
        ChatChannel["chat:messages"]
        StatusChannel["family:status:*"]
    end

    subgraph Subscribers["Subscribers"]
        ChatSub[ChatMessageSubscriber]
        StatusSub[UserStatusSubscriber]
    end

    A --> WSChat
    B --> WSChat
    A --> WSStatus
    B --> WSStatus

    WSChat --> Pub
    Pub --> ChatChannel
    ChatChannel --> ChatSub
    ChatSub --> WSChat

    WSStatus --> Pub
    Pub --> StatusChannel
    StatusChannel --> StatusSub
    StatusSub --> WSFamily
```

---

## 4. 인증 흐름

```mermaid
flowchart LR
    subgraph Client["앱"]
        Req[API 요청]
        Token[JWT in Header]
    end

    subgraph Backend["백엔드"]
        Filter[JwtAuthenticationFilter]
        Login[LoginController]
        KakaoSvc[KakaoUserService]
        AppleSvc[AppleUserService]
        TokenSvc[TokenService]
        DB[(User · UserFamily)]
    end

    subgraph External["외부"]
        KakaoAPI[Kakao API]
        AppleJWKS[Apple JWKS]
    end

    Req --> Token
    Token --> Filter
    Filter -->|유효한 JWT| Req
    Filter -->|/api/login/**| Login

    Login -->|/kakao| KakaoSvc
    Login -->|/apple| AppleSvc
    KakaoSvc --> KakaoAPI
    AppleSvc --> AppleJWKS
    KakaoSvc --> DB
    AppleSvc --> DB
    KakaoSvc --> TokenSvc
    AppleSvc --> TokenSvc
```

---

## 5. 이미지 업로드 (Presigned URL)

```mermaid
sequenceDiagram
    participant App as 앱
    participant API as ImageController<br/>/api/image/upload-urls
    participant S3 as AWS S3

    App->>API: POST (content-type, 파일 수 등)
    API->>API: S3Service.generatePresignedUrls()
    API->>App: Presigned URL 목록
    App->>S3: PUT (각 URL로 직접 업로드)
    S3-->>App: 200 OK
    App->>API: 게시글/채팅 등 생성 시 URL 전달
```

---

## 6. CI/CD (GitHub Actions → EC2)

```mermaid
flowchart LR
    subgraph GitHub["GitHub"]
        Push[push to main]
        Build[Build bootJar]
        Artifact[app.jar]
    end

    subgraph EC2["EC2"]
        Deploy[/deploy/app.jar]
        Systemd[systemd<br/>kinover-backend.service]
        App[Spring Boot]
    end

    Push --> Build
    Build --> Artifact
    Artifact -->|scp| Deploy
    Deploy -->|ssh systemctl restart| Systemd
    Systemd --> App
```

---

## 7. API 도메인 요약

| 도메인 | 경로 | 주요 기능 |
|--------|------|-----------|
| 인증 | `/api/login`, `/api/auth` | Kakao/Apple 로그인, 토큰 발급 |
| 사용자 | `/api/user` | userinfo, 프로필, 알림, 배지 |
| 가족 | `/api/family` | 생성/참여/탈퇴, 공지, family-status |
| 게시글 | `/api/posts` | CRUD, 이미지, 알림 설정 |
| 댓글 | `/api/comments` | CRUD, 알림 설정 |
| 채팅방 | `/api/chatRoom` | 생성/참여/나가기, 메시지 fetch, 읽음, 미디어 |
| 일정 | `/api/schedules` | 조회/추가/삭제, 일별 개수 |
| 이미지 | `/api/image` | Presigned URL 발급 |
| 카테고리 | `/api/categories` | CRUD |
| FCM | `/api/fcm` | 토큰 등록 |
| WebSocket | `/chat`, `/status`, `/family-status` | 실시간 메시지, 접속 상태 |

이 문서는 프로젝트 루트의 `docs/ARCHITECTURE.md`에서 유지됩니다.
