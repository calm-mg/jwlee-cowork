# 시스템 아키텍처 가이드 v1.2

## 서비스 구성도
- **Gateway (Spring Cloud Gateway)**: 모든 외부 요청을 인증 및 라우팅 (포트 8080)
- **User Service**: 사용자 인증, 권한 관리 (포트 9001)
- **Order Service**: 주문 생성, 이력 관리 (포트 9002) - Redis 캐시 의존
- **Payment Service**: 결제 승인/취소 처리 (포트 9003) - 외부 PG사 연동

## 데이터베이스 및 스택
- **PostgreSQL 16**: 메인 저장소 (Master-Slave 구성)
- **Redis 7.2**: 주문 캐시 및 세션 저장소
- **RabbitMQ**: 서비스 간 비동기 메시지 큐

## 중요 의존성 및 제약 사항
- `Order Service`가 중단되면 `Payment Service`는 주문 확인을 할 수 없어 결제가 불가함.
- `RabbitMQ` 지연 시 결제 후 주문 완료 알림 처리가 누락될 수 있음.
- `Redis` 장애 발생 시 `Order Service`가 직접 DB에 접근하므로 DB 부하가 3배 이상 증가함.
