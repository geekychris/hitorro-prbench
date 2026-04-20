#!/bin/bash
# Start PR Bench - backend on port 8090, React dev server on port 3001
set -e

echo "Building backend..."
mvn package -DskipTests -q

echo "Starting backend on port 8090..."
java -jar target/hitorro-pr-bench-1.0.0.jar &
BACKEND_PID=$!

echo "Starting React dev server on port 3001..."
cd react-app
npm run dev &
FRONTEND_PID=$!

echo ""
echo "PR Bench is running:"
echo "  Backend API:  http://localhost:8090"
echo "  Frontend:     http://localhost:3001"
echo "  Swagger:      http://localhost:8090/swagger-ui.html"
echo "  H2 Console:   http://localhost:8090/h2-console"
echo ""
echo "Press Ctrl+C to stop."

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null" EXIT INT TERM
wait
