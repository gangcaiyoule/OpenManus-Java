#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Starting OpenManus Frontend & Backend..."

# Kill existing processes on these ports
lsof -ti :5173 | xargs kill -9 2>/dev/null || true
lsof -ti :8089 | xargs kill -9 2>/dev/null || true

# Start Spring Boot in background. The web application starts frontend/ Vite.
echo "Starting Spring Boot and frontend/ Vite (8089)..."
./scripts/mvnw-local.sh -q clean spring-boot:run -DskipTests &
SPRING_PID=$!

# Wait for Spring Boot to be ready
echo "Waiting for Spring Boot..."
for i in {1..60}; do
  if curl -fsS http://localhost:8089 > /dev/null 2>&1; then
    echo "Spring Boot ready!"
    break
  fi
  sleep 1
done

echo ""
echo "========================================"
echo "OpenManus is running!"
echo "Frontend: http://localhost:8089"
echo "Backend API: http://localhost:8089/api"
echo ""
echo "Press Ctrl+C to stop all services"
echo "========================================"

# Wait for interrupt
trap "echo 'Stopping services...'; kill $SPRING_PID 2>/dev/null; lsof -ti :5173 | xargs kill -9 2>/dev/null; lsof -ti :8089 | xargs kill -9 2>/dev/null; exit" SIGINT SIGTERM
wait
