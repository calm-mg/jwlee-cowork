#!/bin/bash
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# 프로젝트 디렉토리로 이동
cd /home/jwlee/workspace/jwlee-cowork

# 필요한 환경 변수 로드 (예: .envrc 가 있다면 source 하거나 직접 export)
source /home/jwlee/workspace/jwlee-cowork/.envrc

# Spring Shell 명령어를 비대화형(Non-interactive) 모드로 실행
export SPRING_PROFILES_INCLUDE=cron

# 1번 인자로 daily 또는 weekly를 받도록 구성
COMMAND=$1
./mvnw clean spring-boot:run \
  -Dspring-boot.run.arguments="$COMMAND" \
  >> /home/jwlee/.obsidian-cron.log 2>&1
