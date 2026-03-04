# 장애 보고서: 2026-03-01 데이터베이스 연결 타임아웃

## 장애 개요
- **일시**: 2026년 3월 1일 14:20 ~ 15:10 (50분간)
- **증상**: 메인 서비스의 결제 및 조회 API 호출 시 `ConnectionTimeoutException` 발생 및 응답 지연 (평균 15s+)
- **영향**: 전체 사용자의 약 30%가 결제 실패 경험

## 기술적 세부사항
- **에러 로그**: `java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.`
- **원인 분석**: 
    - 특정 프로모션 이벤트로 인해 트래픽이 평상시 대비 5배 급증.
    - DB 커넥션 풀(HikariCP)의 `maximum-pool-size`가 10으로 너무 낮게 설정되어 있었음.
    - 읽기 전용 쿼리가 Master DB로 몰리면서 I/O Wait 증가.

## 조치 사항
1. `maximum-pool-size`를 10에서 50으로 상향 조정.
2. Read-only 트래픽을 위한 Replica DB(Slave) 엔드포인트 분리 적용.
3. 데이터베이스 인덱스 최적화 작업 수행.
