#!/bin/bash
set -e

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
LOG_DIR="$ROOT_DIR/log"
JAR_DIR="$ROOT_DIR/jar"

if [ "${CF_LOAD_DOTENV:-false}" = "true" ] && [ -f "$ROOT_DIR/.env" ]; then
  set -a
  . "$ROOT_DIR/.env"
  set +a
fi

mkdir -p "$LOG_DIR" "$JAR_DIR"

if command -v netstat >/dev/null 2>&1; then
  for ((i=8070;i<=8106;i++)); do
    PID=$(netstat -nlp 2>/dev/null | grep ":$i " | awk '{print $7}' | awk -F"/" '{print $1}' | head -n 1)
    if [ -n "$PID" ]; then
      kill -9 "$PID" || true
    fi
  done
fi

start_service() {
  local jar_name="$1"
  local min_memory="$2"
  local max_memory="$3"
  local delay_after_start="${4:-0}"

  if [ ! -f "$JAR_DIR/$jar_name" ]; then
    return
  fi

  nohup java ${JAVA_COMMON_OPTS:-} -Xms"$min_memory" -Xmx"$max_memory" -jar "$JAR_DIR/$jar_name" >"$LOG_DIR/$jar_name.log" 2>&1 &

  if [ "$delay_after_start" -gt 0 ]; then
    sleep "$delay_after_start"
  fi
}

start_service cf-sms-service-1.0-SNAPSHOT.jar 128m 200m 20
start_service cf-sms-api-1.0-SNAPSHOT.jar 128m 200m
start_service cf-ucenter-service-1.0-SNAPSHOT.jar 128m 200m 20
start_service cf-ucenter-auth-1.0-SNAPSHOT.jar 128m 256m 25
start_service cf-ucenter-api-1.0-SNAPSHOT.jar 128m 300m
start_service cf-ucenter-admin-1.0-SNAPSHOT.jar 128m 300m 10
start_service cf-chat-service-1.0-SNAPSHOT.jar 128m 256m
start_service cf-chat-api-1.0-SNAPSHOT.jar 128m 256m
start_service cf-file-service-1.0-SNAPSHOT.jar 128m 200m
start_service cf-pay-service-1.0-SNAPSHOT.jar 128m 200m 20
start_service cf-file-api-1.0-SNAPSHOT.jar 128m 256m
start_service cf-car-park-service-1.0-SNAPSHOT.jar 128m 200m
start_service cf-pay-api-1.0-SNAPSHOT.jar 128m 256m 20
start_service cf-car-park-api-1.0-SNAPSHOT.jar 128m 200m
start_service cf-car-park-admin-1.0-SNAPSHOT.jar 128m 300m 5
start_service cf-pay-admin-1.0-SNAPSHOT.jar 128m 300m 5
start_service cf-position-service-1.0-SNAPSHOT.jar 128m 300m
start_service cf-position-api-1.0-SNAPSHOT.jar 128m 300m
start_service cf-ad-service-1.0-SNAPSHOT.jar 128m 300m 5
start_service cf-ad-api-1.0-SNAPSHOT.jar 128m 300m
start_service cf-ad-admin-1.0-SNAPSHOT.jar 128m 300m
start_service cf-charging-service-1.0-SNAPSHOT.jar 128m 512m 5
start_service cf-charging-api-1.0-SNAPSHOT.jar 128m 512m
start_service cf-charging-admin-1.0-SNAPSHOT.jar 128m 512m
start_service cf-logistics-service-1.0-SNAPSHOT.jar 128m 400m 5
start_service cf-logistics-api-1.0-SNAPSHOT.jar 128m 512m
start_service cf-logistics-admin-1.0-SNAPSHOT.jar 128m 512m
start_service cf-internet-of-things-forward-hk-1.0-SNAPSHOT.jar 128m 256m
start_service cf-internet-of-things-forward-dh-1.0-SNAPSHOT.jar 128m 256m
