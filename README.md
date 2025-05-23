# <img src="https://avatars.githubusercontent.com/u/206313018?s=200&v=4" width="50"/> Kinover 백엔드

**Kinover**는 가족만을 위한 비공개 SNS 서비스입니다.  
이 저장소는 Kinover 앱의 백엔드 서버 코드이며, Spring Boot 기반으로 구축되었습니다.

<br/>

## 주요 기술 스택

- **Spring Boot 3.x**
- **MariaDB** (JPA/Hibernate)
- **Redis** (실시간 메시지 처리용 Pub/Sub)
- **WebSocket** (실시간 채팅)
- **AWS S3** (이미지 업로드, Presigned URL)
- **JWT 인증** (Spring Security 기반)
- **FCM** (푸시 알림)
- **Amazon EC2 + Nginx + Certbot** (HTTPS 배포 환경)

<br/>

## 주요 기능

- 가족 단위 사용자 시스템
- 게시글/댓글 CRUD 및 멀티 이미지 업로드
- 실시간 채팅 (WebSocket + Redis Pub/Sub)
- FCM 푸시 알림
- 사용자 접속 상태 실시간 표시
- 가족별 공지사항 관리
- 카테고리 커스터마이징
- Swagger UI API 문서 제공 (`/swagger-ui/index.html#/`)

<br/>

## 배포 환경

- Ubuntu 22.04 on Amazon EC2
- Nginx를 통한 리버스 프록시 및 HTTPS 적용 (`kinover.shop`)
- GitHub Actions를 통한 CI/CD, EC2 로컬 빌드 및 배포

<br/>

## 실행 방법

```bash
./gradlew build
nohup java -Duser.timezone=Asia/Seoul -jar build/libs/kinover_backend-0.0.1-SNAPSHOT.jar > nohup.out 2>&1 &
