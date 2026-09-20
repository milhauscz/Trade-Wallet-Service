@echo off
cd /d "%~dp0"

echo Starting Postgres, Redis, Kafka, Kafka UI...
docker compose up -d --wait postgres redis kafka kafka-ui
if errorlevel 1 (
  echo Docker failed. Is Docker Desktop running?
  exit /b 1
)

echo Starting app on http://localhost:8080  ^(Kafka UI: http://localhost:8081^)
call gradlew.bat bootRun
