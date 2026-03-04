# API 트러블슈팅 가이드 (내부용)

## 상황별 에러 해결 방법

### 1. HTTP 504 Gateway Timeout
- **원인**: 백엔드 서비스의 응답이 설정된 타임아웃(기본 30초) 이내에 오지 않음.
- **체크리스트**:
    - 백엔드 서비스 CPU 사용률 확인 (90% 이상 시 스케일 아웃).
    - DB Lock 여부 조사 (`pg_stat_activity` 조회).
- **조치**: 게이트웨이 타임아웃 일시 상향 및 인스턴스 재시작.

### 2. HTTP 429 Too Many Requests
- **원인**: 특정 IP 또는 토큰에서 초당 허용 요청 수(Rate Limit) 초과.
- **체크리스트**:
    - 비정상적인 크롤링 패턴 여부.
- **조치**: 해당 요청 소스 IP 임시 차단(Blacklist).

### 3. Redis 연결 장애 (`RedisConnectionException`)
- **원인**: Redis 서버 메모리 부족 또는 네트워크 파티션.
- **조치**: 
    1. `redis-cli info memory` 확인. 
    2. 불필요한 캐시 키 정리.
    3. `Order Service` 환경 설정에서 캐시 비활성화 플래그 적용 (임시 DB 직결).
