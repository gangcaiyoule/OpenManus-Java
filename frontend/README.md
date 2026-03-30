# OpenManus Frontend (React + Vite)

## Quick Start

```bash
cd frontend
npm install
npm run dev
```

Default dev URL: `http://localhost:5173`

## Backend Dependency

Start backend on `http://localhost:8089` first.

Vite proxy is preconfigured:
- `/api` -> `http://localhost:8089`
- `/ws` -> `http://localhost:8089`

## Stream Protocol (WebSocket)

1. POST `/api/agent/workflow-stream` with `{ "input": "..." }`
2. Read `sessionId` and `topic` from response
3. Connect SockJS/STOMP endpoint `/ws`
4. Subscribe:
   - `${topic}` (execution events)
   - `${topic}/logs` (frontend logs)
   - `${topic}/result` (final result)

## Scripts

- `npm run dev`
- `npm run build`
- `npm run test`
