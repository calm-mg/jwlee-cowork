# Deployment Guide

## 1. Service-Enabled Artifact 생성
Spring Boot의 `executable` 기능을 활용하여 직접 실행 가능한 JAR 파일을 생성함

- `pom.xml` 설정 확인
  - `spring-boot-maven-plugin`의 `<executable>true</executable>` 설정 포함 여부 확인
- 프로젝트 빌드 수행
  ```bash
  ./mvnw clean package
  ```
- 빌드 결과물 확인
  - `target/toy-app-0.0.1-SNAPSHOT.jar` 파일 생성됨
  - 해당 파일은 `chmod +x` 권한 부여 시 `./jar-file` 형태로 직접 실행 가능함

## 2. System Service 등록 및 실행
생성된 아티팩트를 systemd 서비스로 등록하여 부팅 시 자동 실행되도록 설정함

- 서비스 파일 링크 생성
  ```bash
  sudo ln -s /home/jwlee/workspace/toy-app/script/toy-app.service /etc/systemd/system/
  ```
- 서비스 활성화 (부팅 시 자동 시작)
  ```bash
  sudo systemctl enable toy-app.service
  ```
- 서비스 시작 및 상태 확인
  ```bash
  sudo systemctl start toy-app.service
  sudo systemctl status toy-app.service
  ```
- 로그 모니터링
  ```bash
  journalctl -u toy-app.service -f
  ```

## 3. 서비스 제거
운영 중인 서비스를 중지하고 시스템 설정에서 삭제함

- 서비스 중지 및 비활성화
  ```bash
  sudo systemctl stop toy-app.service
  sudo systemctl disable toy-app.service
  ```
- 서비스 파일 링크 삭제
  ```bash
  sudo rm /etc/systemd/system/toy-app.service
  sudo systemctl daemon-reload
  sudo systemctl reset-failed
  ```

## 4. UFW 방화벽 설정
서비스 포트(8080) 개방 및 방화벽 규칙 관리 방법임

- 서비스 포트 허용
  ```bash
  sudo ufw allow 8080/tcp
  ```
- 방화벽 상태 확인
  ```bash
  sudo ufw status verbose
  ```
- 설정 백업
  ```bash
  sudo cp /etc/ufw/user.rules /etc/ufw/user.rules.bak_$(date +%F_%T)
  ```
- 설정 복구
  ```bash
  sudo cp /path/to/backup/user.rules /etc/ufw/user.rules
  sudo ufw disable
  sudo ufw enable
  ```
