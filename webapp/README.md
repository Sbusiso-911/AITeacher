# AITeacher Web App

This is a minimal Node.js backend used to synchronize chat history between the Android application and a browser version.

## Endpoints

- `POST /api/sync` – Accepts JSON payload `{ user_id, conversation_id, messages }` and stores the messages on disk.
- `GET /api/history?user_id=XXX` – Returns all stored messages for the specified user.

Data is stored in the `data/` folder as JSON files keyed by `user_id`.

## Running Locally

```bash
npm install
npm start
```

The server will listen on port `3000` by default.
