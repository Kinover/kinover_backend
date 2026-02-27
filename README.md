<h1>
  <img src="https://avatars.githubusercontent.com/u/206313018?s=200&v=4" width="50" alt="Kinover Logo" />
  Kinover 백엔드
</h1>

**Kinover**는 가족만을 위한 비공개 SNS 서비스입니다.  
이 저장소는 Kinover 앱의 백엔드 서버 코드이며, Spring Boot 기반으로 구축되었습니다.

<br/>

## 주요 기술 스택

- **Java 17**
- **Spring Boot 3.4.2**
- **Spring Data JPA / Hibernate**
- **MariaDB**
- **Spring Security + JWT** (JJWT, java-jwt)
- **WebSocket**
- **Redis Pub/Sub** (Spring Data Redis, Redisson)
- **AWS S3** (Presigned URL 기반 업로드)
- **FCM** (Firebase Cloud Messaging)
- **Swagger/OpenAPI** (springdoc-openapi)
- **MapStruct**
- **Lombok**
- **GitHub Actions** (CI/CD)
- **Amazon EC2 + Nginx + Certbot** (HTTPS 배포 환경)

<br/>

## 주요 기능

- 가족 단위 사용자/권한 시스템
- 카카오 로그인 + JWT 인증
- 가족 채팅방 및 메시지 관리
- 실시간 채팅 (WebSocket + Redis Pub/Sub)
- 가족 일정 등록/수정/조회
- 이미지 업로드 (S3 + Presigned URL)
- FCM 푸시 알림
- 추억(Memory) 및 댓글 관리
- 챌린지/추천 챌린지 관리
- OpenAI 기반 AI 대화 API 연동
- Swagger UI API 문서 제공 (`/swagger-ui/index.html`)

<br/>

## 배포 환경

- Ubuntu 22.04 on Amazon EC2
- Nginx 리버스 프록시 및 HTTPS 적용
- GitHub Actions 기반 빌드/배포 자동화
- 배포 방식: JAR 산출물 + `.env` 전달 후 원격 재기동

<br/>

## 실행 방법

```bash
./gradlew clean build
nohup java -Duser.timezone=Asia/Seoul -jar build/libs/kinover_backend-0.0.1-SNAPSHOT.jar > nohup.out 2>&1 &
```
