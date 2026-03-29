### (1) Kinover — 가족 커뮤니케이션 앱 (채팅·일정·추억·AI 챗봇)

**2025.03 – 2026.02 | PM · Full-stack | 팀 프로젝트(3인)**

**프로젝트 개요**

가족 구성원 간 소통을 **채팅·일정·추억**으로 묶고, AI 챗봇 '키노'로 대화/기록 경험을 확장한 모바일 앱입니다. **가족 ID 스코프**를 중심으로 인증/데이터/실시간 흐름이 안정적으로 연결되도록 구조 설계에 집중했습니다.

**주요 기여**

**프로젝트 관리 & 프론트엔드**
- PM으로서 핵심 기능 정의 및 우선순위·범위·일정 조율
- **Feature-Based 아키텍처**로 도메인별 screens/components/hooks/store 분리 설계
- 가족 전용 채팅방 + AI 챗봇 '키노' 대화 흐름(가이드·시나리오) 설계 및 구현
- **일정 추가/수정 바텀시트 UX**와 공휴일 연동 흐름 설계
- **WebSocket 기반 실시간 접속 상태 및 메시지 이벤트** 연동 설계
- **JWT + Keychain + Biometrics** 인증·자동 로그인 및 앱 진입 분기(Root → Auth/Tab) 구조 설계
- babel-plugin-module-resolver로 **path alias** 적용, import 경로 통일

**백엔드**
- **Spring Boot 기반 REST API** 설계 및 구현 (PostService, ChatRoomService, UserService 등 핵심 서비스)
- **Sign in with Apple 백엔드 지원** 구현 (JWT 검증, 사용자 정보 파싱, 자동 회원가입)
- **WebSocket + Redis Pub/Sub** 기반 실시간 채팅 메시지 전송 및 접속 상태 관리
- **AWS S3 Presigned URL** 기반 사진/영상 업로드 API 구현 및 content-type 검증
- **JWT 인증 및 Spring Security** 설정 (필터 체인, CORS, 엔드포인트 보안)
- **JPA/Hibernate** 기반 게시글 목록 조회 성능 최적화 (N+1 문제 해결, fetch join 최적화)
- **Swagger API 문서화** 및 Spring Boot 3.4 호환성 문제 해결
- **GlobalExceptionHandler** 구현으로 일관된 에러 응답 및 500 에러 방지

**Problem Solving**

**프론트엔드**
- **Android 이미지 캐러셀 스와이프 비정상 동작**
    - 원인: FlatList initialScrollIndex 렌더 타이밍으로 인덱스 동기화 깨짐
    - 해결: useEffect에서 scrollToIndex로 초기 위치 강제 동기화 + 렌더 조건 최적화
- **바텀시트에서 키보드 노출 시 snapPoints/레이아웃 버벅임**
    - 원인: 콘텐츠 측정 기반 snapPoints가 키보드 show/hide 시마다 갱신되어 리렌더 과다
    - 해결: 측정 로직을 useMeasuredSnapPoints로 분리하고, 키보드 리스너로 스냅 인덱스만 제어해 안정화
- **깊은 상대경로 import로 유지보수 비용 증가**
    - 원인: ../../../components 등 상대경로 혼용으로 가독성 저하 및 리팩터링 비용 증가
    - 해결: alias 도입 후 components/hooks/utils/features 기준으로 전면 통일

**백엔드**
- **게시글 목록 조회 시 500 에러 및 성능 저하**
    - 원인: JPQL IN 절과 fetch join 조합 시 Hibernate 예외 발생, 이미지 정렬 시 null 처리 누락
    - 해결: fetch join 제거 후 별도 쿼리로 분리, null-safe 정렬 로직 추가, eager loading으로 N+1 문제 해결
- **Swagger API 문서 엔드포인트 접근 시 500 에러**
    - 원인: Spring Boot 3.4 호환성 문제 및 GlobalExceptionHandler가 Swagger 경로까지 가로채는 문제
    - 해결: springdoc-openapi 버전 업데이트, Swagger 경로를 SecurityConfig에서 제외, 예외 핸들러에서 Swagger 경로 bypass 처리
- **가족 생성 시 Kino 챗봇 룸 생성 실패로 인한 전체 프로세스 중단**
    - 원인: 챗봇 룸 생성 실패 시 트랜잭션 롤백으로 가족 생성까지 실패
    - 해결: 챗봇 룸 생성을 best-effort로 변경하고 별도 트랜잭션으로 분리하여 핵심 기능에 영향 없도록 개선

**기술 스택**

**프론트엔드:** React Native, TypeScript, Redux Toolkit, React Navigation, WebSocket, Keychain, Biometrics  
**백엔드:** Spring Boot 3.4, Java 17, JPA/Hibernate, MariaDB, Redis, WebSocket, AWS S3, JWT, Spring Security, Swagger  
**인프라:** AWS EC2, Nginx, GitHub Actions (CI/CD)

**성과/결과**

- 도메인별 응집도와 확장성을 높여 기능 추가 및 리팩터링 속도 개선
- 실시간 메시지/접속 상태를 Redux와 로컬 상태로 분리해 복잡도 관리
- 공통 UI 컴포넌트 재사용 구조로 개발 일관성 및 생산성 향상
- 백엔드 API 성능 최적화로 게시글 목록 조회 응답 시간 개선
- 안정적인 에러 핸들링으로 프로덕션 500 에러 발생률 감소
